import { Injectable } from '@angular/core';
import { ModalController } from '@ionic/angular/lazy';
import { AuthModalComponent } from '../modals/auth-modal.component';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class SessaoService {
  constructor(
    private readonly auth: AuthService,
    private readonly modalCtrl: ModalController
  ) {}

  async exigirLogin(): Promise<boolean> {
    if (this.auth.logado) {
      return true;
    }
    const modal = await this.modalCtrl.create({
      component: AuthModalComponent,
      breakpoints: [0, 0.75, 1],
      initialBreakpoint: 0.75,
      handle: true,
    });
    await modal.present();
    const { data } = await modal.onWillDismiss();
    return !!data?.ok && this.auth.logado;
  }
}
