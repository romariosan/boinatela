import { Injectable, NgZone } from '@angular/core';
import { BehaviorSubject, Observable, distinctUntilChanged } from 'rxjs';

/** Estado da internet do aparelho, pelos eventos online/offline do navegador (ou da WebView). */
@Injectable({ providedIn: 'root' })
export class ConexaoService {
  private readonly online = new BehaviorSubject<boolean>(navigator.onLine);

  readonly online$: Observable<boolean> = this.online.pipe(distinctUntilChanged());

  constructor(zone: NgZone) {
    window.addEventListener('online', () => zone.run(() => this.online.next(true)));
    window.addEventListener('offline', () => zone.run(() => this.online.next(false)));
  }
}
