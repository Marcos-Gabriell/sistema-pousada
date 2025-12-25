import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ModalScrollService {
  private lockCount = 0;
  private previousOverflow: string | null = null;
  private previousPosition: string | null = null;
  private previousTop: string | null = null;
  private previousLeft: string | null = null;
  private previousWidth: string | null = null;
  private scrollY = 0;

  lock(): void {
    try {
      if (this.lockCount === 0) {
        this.previousOverflow = document?.body?.style?.overflow ?? null;
        this.previousPosition = document?.body?.style?.position ?? null;
        this.previousTop = document?.body?.style?.top ?? null;
        this.previousLeft = document?.body?.style?.left ?? null;
        this.previousWidth = document?.body?.style?.width ?? null;

        this.scrollY = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || 0;

        document.body.style.top = `-${this.scrollY}px`;
        document.body.style.left = '0';
        document.body.style.position = 'fixed';
        document.body.style.width = '100%';
        document.body.style.overflow = 'hidden';
      }
      this.lockCount++;
    } catch {
    }
  }

  unlock(): void {
    try {
      if (this.lockCount <= 0) return;
      this.lockCount--;
      if (this.lockCount === 0) {
        if (this.previousOverflow != null) document.body.style.overflow = this.previousOverflow; else document.body.style.overflow = '';
        if (this.previousPosition != null) document.body.style.position = this.previousPosition; else document.body.style.position = '';
        if (this.previousTop != null) document.body.style.top = this.previousTop; else document.body.style.top = '';
        if (this.previousLeft != null) document.body.style.left = this.previousLeft; else document.body.style.left = '';
        if (this.previousWidth != null) document.body.style.width = this.previousWidth; else document.body.style.width = '';

        window.scrollTo(0, this.scrollY || 0);

        this.previousOverflow = null;
        this.previousPosition = null;
        this.previousTop = null;
        this.previousLeft = null;
        this.previousWidth = null;
        this.scrollY = 0;
      }
    } catch {}
  }
}
