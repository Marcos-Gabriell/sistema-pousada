import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, tap, of } from 'rxjs';
import { Router } from '@angular/router';

export type NotificationStatus = 'LIDA' | 'NAO_LIDA';

export interface NotificationItem {
  id: number;
  title: string;
  message: string;
  url?: string | null;
  cta?: string | null;
  read: boolean;
  createdAt: string;
  readAt?: string | null;
  type?: string | null;

  origin?: string | null;
  authorName?: string | null;
  referenceId?: number | null;
  dataRef?: string | null;
  dedupKey?: string | null;

  recipientsLabel?: string[];
}

interface NotificationDto {
  id: number;
  type?: string | null;
  title: string;
  body?: string | null;
  bodyFormatted?: string | null;
  link?: string | null;
  action?: string | null;
  itemId?: number | null;
  date?: string | null;
  origin?: string | null;
  status: string;
  recipients?: number[] | null;

  createdAt: string;
  expiresAt?: string | null;
  expiresInDays?: number | null;
  expiresInLabel?: string | null;

  recipientsCount?: number | null;
  recipientsLabel?: string | string[] | null;
}

export interface PaginatedResponse {
  items: NotificationItem[];
  totalItems: number;
}

const API_BASE = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private base = `${API_BASE}/notificacoes`;

  private _items      = signal<NotificationItem[]>([]);
  private _loading    = signal(false);
  private _unread     = signal(0);
  private _connected  = signal(false);

  items     = this._items.asReadonly();
  loading   = this._loading.asReadonly();
  unread    = this._unread.asReadonly();
  connected = this._connected.asReadonly();

  private es: EventSource | null = null;
  private pollTimer: any = null;
  private pollMs = 5000;
  private enabled = true;
  private realtime = false;

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  setEnabled(enabled: boolean) {
    this.enabled = enabled;
    if (!enabled) {
      this.stopPolling();
      this.disconnectSse();
      this._unread.set(0);
      this._items.set([]);
      this._connected.set(false);
    }
  }

  setRealtime(on: boolean) {
    this.realtime = on;
    if (!on) {
      this.disconnectSse();
    }
  }

  private canExecute(): boolean {
    const isLogin = this.router.url.includes('/login');
    return this.enabled && !isLogin;
  }

  load(page: number, size: number, status: NotificationStatus | null, q?: string): Observable<PaginatedResponse> {
    if (!this.canExecute()) {
      return new Observable<PaginatedResponse>(obs => { obs.next({ items: [], totalItems: 0 }); obs.complete(); });
    }

    this._loading.set(true);

    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));

    if (status) params = params.set('status', status);
    if (q && q.trim()) params = params.set('q', q.trim());

    return this.http.get<any>(this.base, { params, observe: 'response' }).pipe(
      map(resp => {
        const body = resp.body ?? {};
        const rawItems: NotificationDto[] = Array.isArray(body.items) ? body.items : (Array.isArray(body) ? body : []);
        const items = rawItems.map(this.mapDto);
        const totalFromHeader = Number(resp.headers.get('X-Total-Count') || '0');
        const totalFromBody = Number(body.totalItems || 0);
        const totalItems = totalFromHeader || totalFromBody || items.length;
        return { items, totalItems } as PaginatedResponse;
      }),
      tap(({ items }) => {
        this._items.set(items);
        this._loading.set(false);
      })
    );
  }

  markAsRead(id: number): Observable<void> {
    if (!this.canExecute()) {
      return new Observable<void>(obs => obs.error(new Error('Serviço desabilitado')));
    }
    return this.http.patch<void>(`${this.base}/${id}/lida`, {});
  }

  markAllAsRead(): Observable<void> {
    if (!this.canExecute()) {
      return new Observable<void>(obs => obs.error(new Error('Serviço desabilitado')));
    }
    return this.http.post<void>(`${this.base}/marcar-todas-como-lidas`, {});
  }

  refreshUnread(): Observable<number> {
    if (!this.canExecute()) return of(0);

    return this.http.get<{ count: number }>(`${this.base}/unread-count`).pipe(
      map(r => r.count),
      tap(count => { if (this.canExecute()) this._unread.set(count); })
    );
  }

  private parseRecipientsLabel(src: string | string[] | null | undefined): string[] {
    if (!src) return [];
    if (Array.isArray(src)) return src.map(s => s?.trim()).filter(Boolean);
    const txt = String(src).trim();
    if (!txt) return [];
    return txt.split(/[|,;/]+/).map(s => s.trim()).filter(Boolean);
  }

  private mapDto = (d: NotificationDto): NotificationItem => ({
    id: d.id,
    title: d.title,
    message: (d.bodyFormatted && d.bodyFormatted.trim()) || (d.body ?? ''),
    url: d.link ?? null,
    cta: d.action ?? null,
    read: d.status !== 'NOVO',
    createdAt: d.createdAt,
    readAt: null,
    type: d.type ?? null,

    origin: d.origin ?? null,
    authorName: null,
    referenceId: d.itemId ?? null,
    dataRef: d.date ?? null,
    dedupKey: null,

    recipientsLabel: this.parseRecipientsLabel(d.recipientsLabel),
  });

  initRealtime() {
    if (!this.canExecute()) return;

    this.startPolling();

    if (this.realtime) {
      this.connectSse();
    }

    const onVis = () => {
      if (!this.canExecute()) return;
      if (document.hidden) this.stopPolling();
      else this.startPolling();
    };
    document.removeEventListener('visibilitychange', onVis as any);
    document.addEventListener('visibilitychange', onVis);
  }

  startPolling(ms: number = this.pollMs) {
    if (!this.canExecute() || this.pollTimer) return;
    this.pollMs = ms;
    this.refreshUnread().subscribe();
    this.pollTimer = setInterval(() => {
      if (this.canExecute()) this.refreshUnread().subscribe();
      else this.stopPolling();
    }, this.pollMs);
  }

  stopPolling() {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  connectSse() {
    if (!this.canExecute() || this.es) return;

    const raw = localStorage.getItem('token') || '';
    const token = raw.replace(/^"|"$/g, '');
    if (!token) return;

    const url = `${this.base}/stream?token=${encodeURIComponent(token)}`;
    const es = new EventSource(url);

    const handleJson = (evt: MessageEvent) => {
      try { return JSON.parse(evt.data); } catch { return null; }
    };

    es.onopen = () => {
      this._connected.set(true);
    };

    es.onerror = () => {
      this._connected.set(false);
      this.disconnectSse();
    };

    es.addEventListener('unread', (evt: MessageEvent) => {
      if (!this.canExecute()) return;
      const data = handleJson(evt);
      if (data && typeof data.unread === 'number') this._unread.set(data.unread);
    });

    es.addEventListener('notification', (evt: MessageEvent) => {
      if (!this.canExecute()) return;
      const data = handleJson(evt) as NotificationDto | null;
      if (!data) return;
      const it = this.mapDto(data);
      this._items.update(curr => [it, ...curr]);
    });

    es.onmessage = (evt) => {
      if (!this.canExecute()) return;
      const data = handleJson(evt);
      if (data?.item) {
        const it = this.mapDto(data.item as NotificationDto);
        this._items.update(curr => [it, ...curr]);
      } else if (typeof data?.unread === 'number') {
        this._unread.set(data.unread);
      }
    };

    this.es = es;
  }

  disconnectSse() {
    if (this.es) {
      this.es.close();
      this.es = null;
    }
    this._connected.set(false);
  }
}
