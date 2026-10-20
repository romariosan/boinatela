import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ModalController, ToastController } from '@ionic/angular/lazy';
import { VerificacaoStatus } from '../models/anuncio';
import { VerificarWhatsappComponent } from '../shared/verificar-whatsapp/verificar-whatsapp.component';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class VerificacaoService {
  /** Cadastro e Publicar podem pedir ao mesmo tempo (cadastro aberto pelo Publicar): um modal só. */
  private emAndamento: Promise<boolean> | null = null;

  constructor(
    private readonly auth: AuthService,
    private readonly modalCtrl: ModalController,
    private readonly toastCtrl: ToastController
  ) {}

  /** Resolve true se o WhatsApp do usuário logado já está (ou acabou de ser) verificado. */
  exigirVerificacao(): Promise<boolean> {
    if (this.auth.snapshot?.telefoneVerificado) {
      return Promise.resolve(true);
    }
    this.emAndamento ??= this.verificar().finally(() => (this.emAndamento = null));
    return this.emAndamento;
  }

  private async verificar(): Promise<boolean> {
    let status: VerificacaoStatus;
    try {
      status = await this.auth.statusVerificacao();
    } catch (erro) {
      const sessaoInvalida = erro instanceof HttpErrorResponse && (erro.status === 401 || erro.status === 403);
      if (sessaoInvalida) {
        this.auth.sair();
      }
      await this.avisar(
        sessaoInvalida ? 'Sua sessão expirou. Entre de novo.' : 'Não foi possível verificar seu WhatsApp agora.',
        'warning'
      );
      return false;
    }
    if (status.telefoneVerificado) {
      this.auth.marcarVerificado();
      return true;
    }

    const modal = await this.modalCtrl.create({
      component: VerificarWhatsappComponent,
      breakpoints: [0, 0.75, 1],
      initialBreakpoint: 0.75,
      handle: true,
    });
    await modal.present();
    const { role } = await modal.onWillDismiss();
    if (role !== 'verificado') {
      return false;
    }
    await this.avisar('WhatsApp verificado com sucesso!', 'success');
    return true;
  }

  private async avisar(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, color, position: 'top', duration: 2600 });
    await toast.present();
  }
}
