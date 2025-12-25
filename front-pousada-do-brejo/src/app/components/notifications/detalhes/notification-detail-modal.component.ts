import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationItem } from '../../../services/notifications/notifications.service';

@Component({
  standalone: true,
  selector: 'app-notification-detail-modal',
  imports: [CommonModule],
  templateUrl: './notification-detail-modal.component.html',
  styleUrls: ['./notification-detail-modal.component.css']
})
export class NotificationDetailModalComponent {
  @Input() open = false;
  @Input() item: NotificationItem | null = null;

  @Input() index = 0;
  @Input() total = 0;

  @Output() gotoIndex = new EventEmitter<number>();
  @Output() back = new EventEmitter<void>();
  @Output() closeAll = new EventEmitter<void>();

  get pageCounter(): string {
    return this.total > 0 ? `${this.index + 1} de ${this.total}` : '';
  }
  get hasPrev(): boolean {
    return this.index > 0;
  }
  get hasNext(): boolean {
    return this.index < this.total - 1;
  }

  goPrev() {
    if (this.hasPrev) this.gotoIndex.emit(this.index - 1);
  }
  goNext() {
    if (this.hasNext) this.gotoIndex.emit(this.index + 1);
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

    if (target.getTime() === today.getTime()) return `Hoje, ${time}`;
    if (target.getTime() === yesterday.getTime()) return `Ontem, ${time}`;
    return (
      dt.toLocaleDateString('pt-BR', {
        day: '2-digit',
        month: 'short',
        year: 'numeric'
      }) + `, ${time}`
    );
  }

  asPlain(s: string | null | undefined): string {
    return (s || '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
  }
}
