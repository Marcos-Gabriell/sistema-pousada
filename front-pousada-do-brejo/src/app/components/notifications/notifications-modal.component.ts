import {
  Component, EventEmitter, HostListener, Input, Output,
  SimpleChanges, OnChanges, OnDestroy, computed, signal, inject,
  ViewChild, ElementRef, TrackByFunction
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import {
  NotificationsService,
  NotificationItem,
  NotificationStatus,
  PaginatedResponse
} from '../../services/notifications/notifications.service';

import { ModalScrollService } from '../../services/modal-scroll.service';

import { NotificationDetailModalComponent } from './detalhes/notification-detail-modal.component';

@Component({
  standalone: true,
  selector: 'app-notifications-modal',
  imports: [CommonModule, FormsModule, NotificationDetailModalComponent],
  styleUrls: ['./notifications-modal.component.css'],
  templateUrl: './notifications-modal.component.html'
})
export class NotificationsModalComponent implements OnChanges, OnDestroy {
  // I/O
  @Input() open = false;
  @Output() close = new EventEmitter<void>();
  @ViewChild('searchBox') searchBox!: ElementRef<HTMLInputElement>;
  @ViewChild('modalRoot') modalRoot!: ElementRef<HTMLDivElement>;

  // DI
  svc = inject(NotificationsService);
  private modalScroll = inject(ModalScrollService);

  // STATE
  q = '';
  status = signal<NotificationStatus | null>(null);
  unread = this.svc.unread;

  page = signal(0);
  pageSize = signal(this.detectPageSize());
  totalItems = signal(0);
  private lastServerCount = signal(0);

  isRefreshing = signal(false);
  isMarkingAll = signal(false);
  error = signal<string | null>(null);

  detailOpen = signal(false);
  selected: NotificationItem | null = null;
  selectedIndex = -1;

  // COMPUTED
  view = computed<NotificationItem[]>(() => this.svc.items());
  totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalItems() / this.pageSize()))
  );
  moreAvailable = computed(() => {
    const total = this.totalItems();
    if (total > 0) return this.page() < this.totalPages() - 1;
    return this.lastServerCount() === this.pageSize();
  });

  // CONSTS
  private searchTmr: any = null;
  private readonly SEARCH_DEBOUNCE_MS = 300;
  private readonly MIN_SPINNER_MS = 500;

  // LIFECYCLE
  ngOnChanges(ch: SimpleChanges) {
    if (ch['open']?.currentValue === true && !ch['open']?.previousValue) {
      this.page.set(0);
      this.fetch();
      this.svc.refreshUnread().subscribe();

      setTimeout(() => this.modalRoot?.nativeElement?.focus(), 0);
      this.modalScroll.lock();
    }
    if (ch['open']?.currentValue === false && ch['open']?.previousValue) {
      this.modalScroll.unlock();
    }
  }

  ngOnDestroy(): void {
    if (this.open) this.modalScroll.unlock();
  }

  // DATA
  private fetch() {
    this.error.set(null);
    this.svc
      .load(this.page(), this.pageSize(), this.status(), this.q.trim())
      .subscribe({
        next: (res) => this.handleResponse(res),
        error: (err) => {
          console.error('Falha ao carregar notificações:', err);
          this.error.set('Não foi possível carregar as notificações.');
        }
      });
  }

  private handleResponse(res: PaginatedResponse) {
    this.lastServerCount.set(res.items.length);
    this.totalItems.set(res.totalItems);
    this.ensurePageBounds(res.totalItems);

    if (res.items.length === 0 && this.page() > 0) {
      this.backUntilHasData();
    }
  }

  reload() {
    const start = Date.now();
    this.isRefreshing.set(true);
    this.svc.refreshUnread().subscribe();

    this.svc
      .load(this.page(), this.pageSize(), this.status(), this.q.trim())
      .subscribe({
        next: (res) => {
          this.handleResponse(res);
          const elapsed = Date.now() - start;
          setTimeout(
            () => this.isRefreshing.set(false),
            Math.max(0, this.MIN_SPINNER_MS - elapsed)
          );
        },
        error: (err) => {
          console.error('Falha ao recarregar notificações:', err);
          this.error.set('Ocorreu um erro ao atualizar a lista.');
          this.isRefreshing.set(false);
        }
      });
  }

  // UI HANDLERS
  setStatus(s: NotificationStatus | null) {
    if (this.status() === s) return;
    this.status.set(s);
    this.page.set(0);
    this.fetch();
  }

  onQueryChange() {
    clearTimeout(this.searchTmr);
    this.searchTmr = setTimeout(() => {
      this.page.set(0);
      this.fetch();
    }, this.SEARCH_DEBOUNCE_MS);
  }

  clearQuery() {
    this.q = '';
    this.page.set(0);
    this.fetch();
    setTimeout(() => this.searchBox?.nativeElement?.focus(), 0);
  }

  nextPage() {
    if (!this.moreAvailable()) return;
    this.page.update((p) => p + 1);
    this.fetch();
  }

  prevPage() {
    if (this.page() === 0) return;
    this.page.update((p) => p - 1);
    this.fetch();
  }

  markAll() {
    if (this.unread() > 0 && this.status() !== 'LIDA' && !this.isMarkingAll()) {
      const start = Date.now();
      this.isMarkingAll.set(true);

      this.svc.markAllAsRead().subscribe({
        next: () => {
          // recarrega lista e contador
          this.reload();
          const elapsed = Date.now() - start;
          setTimeout(
            () => this.isMarkingAll.set(false),
            Math.max(0, this.MIN_SPINNER_MS - elapsed)
          );
        },
        error: (err) => {
          console.error('Falha ao marcar todas como lidas:', err);
          this.isMarkingAll.set(false);
        }
      });
    }
  }

  // DETAIL (abrir a partir da lista atual)
  openDetailFor(n: NotificationItem) {
    this.selectedIndex = this.view().findIndex((x) => x.id === n.id);
    this.selected = n;
    this.detailOpen.set(true);
    if (!n.read) this.svc.markAsRead(n.id).subscribe();
  }

  closeDetail() {
    this.detailOpen.set(false);
    this.selected = null;
    this.selectedIndex = -1;
  }

  closeEverything() {
    this.closeDetail();
    this.close.emit();
  }

  // responsive page size: mobile uses 8, desktop uses 5
  private detectPageSize(): number {
    try {
      // treat widths <= 600px as mobile
      return window.innerWidth <= 600 ? 8 : 5;
    } catch {
      return 5;
    }
  }

  @HostListener('window:resize')
  onResize() {
    const newSize = this.detectPageSize();
    if (this.pageSize() !== newSize) {
      this.pageSize.set(newSize);
      this.page.set(0);
      this.fetch();
    }
  }

  globalIndex(): number {
    return this.selectedIndex >= 0
      ? this.page() * this.pageSize() + this.selectedIndex
      : -1;
  }

  openDetailByGlobalIndex(idx: number) {
    if (idx < 0 || idx >= this.totalItems()) return;

    const targetPage = Math.floor(idx / this.pageSize());
    const indexInPage = idx % this.pageSize();

    if (targetPage === this.page()) {
      const list = this.view();
      if (indexInPage < list.length) {
        this.selectedIndex = indexInPage;
        this.selected = list[indexInPage];
        this.detailOpen.set(true);
        if (this.selected && !this.selected.read) {
          this.svc.markAsRead(this.selected.id).subscribe();
        }
      }
      return;
    }

    this.page.set(targetPage);
    this.svc
      .load(targetPage, this.pageSize(), this.status(), this.q.trim())
      .subscribe({
        next: () => {
          const list = this.view();
          if (indexInPage < list.length) {
            this.selectedIndex = indexInPage;
            this.selected = list[indexInPage];
            this.detailOpen.set(true);
            if (this.selected && !this.selected.read) {
              this.svc.markAsRead(this.selected.id).subscribe();
            }
          } else if (list.length) {
            this.selectedIndex = 0;
            this.selected = list[0];
            this.detailOpen.set(true);
          }
        },
        error: (err) =>
          console.error('Falha ao carregar página alvo:', err)
      });
  }

  // ====== HELPERS ======
  private ensurePageBounds(totalItems: number) {
    const tp = Math.max(1, Math.ceil(totalItems / this.pageSize()));
    if (this.page() >= tp) this.page.set(Math.max(0, tp - 1));
  }

  private backUntilHasData() {
    if (this.page() === 0) return;
    this.page.update((p) => p - 1);
    this.fetch();
  }

  stripHtml = (s: string | null | undefined): string => {
    if (!s) return '';
    return s.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim();
  };

  preview(v: string | null | undefined): string {
    if (!v) return '';

    const clean = this.stripHtml(v).replace(/\s+/g, ' ').trim();
    const patternToRemove =
      /^Usuário[^|]+\|\s*\d+\s*esta\s+(ATIVO|INATIVO)\s+por\s+\w+\s*\|\s*\d*\.\s*/i;
    let finalClean = clean.replace(patternToRemove, '');
    if (!finalClean.trim()) finalClean = clean;

    const maxLength = 50;
    if (finalClean.length <= maxLength) return finalClean;

    const truncated = finalClean.substring(0, maxLength);
    const lastSpace = truncated.lastIndexOf(' ');
    if (lastSpace > 30) return truncated.substring(0, lastSpace) + '...';
    return truncated + '...';
  }

  smartDate(d: string | Date | null | undefined): string {
    if (!d) return '';
    const dt = new Date(d);
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);
    const target = new Date(dt.getFullYear(), dt.getMonth(), dt.getDate());
    const time = dt.toLocaleTimeString('pt-BR', {
      hour: '2-digit',
      minute: '2-digit'
    });

    if (target.getTime() === today.getTime()) return time;
    if (target.getTime() === yesterday.getTime()) return `Ontem, ${time}`;
    if (dt.getFullYear() === now.getFullYear()) {
      return dt.toLocaleDateString('pt-BR', {
        day: '2-digit',
        month: 'short'
      });
    }
    return dt.toLocaleDateString('pt-BR');
  }

  // atalhos
  @HostListener('window:keydown', ['$event'])
  onKeydown(e: KeyboardEvent) {
    if (!this.open || this.detailOpen()) return;
    if (e.key === '/') {
      e.preventDefault();
      this.searchBox?.nativeElement?.focus();
    }
  }

  @HostListener('window:keydown.escape')
  onEsc() {
    if (!this.open) return;
    this.detailOpen() ? this.closeDetail() : this.close.emit();
  }

  trackById: TrackByFunction<NotificationItem> = (_i, n) => n.id;
}
