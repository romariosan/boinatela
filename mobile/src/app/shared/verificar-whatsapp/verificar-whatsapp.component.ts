import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy } from '@angular/core';
import { ModalController } from '@ionic/angular/lazy';
import { AuthService } from '../../services/auth.service';

/** A pessoa digita o código de 6 dígitos que o robô mandou no WhatsApp dela. */
@Component({
  selector: 'app-verificar-whatsapp',
  templateUrl: './verificar-whatsapp.component.html',
  styleUrls: ['./verificar-whatsapp.component.scss'],
  standalone: false,
})
export class VerificarWhatsappComponent implements OnDestroy {
  codigo = '';
  enviando = false;
  confirmando = false;
  erro = '';
  reenviarEm = 0;
  private contador?: ReturnType<typeof setInterval>;

  constructor(
    private readonly auth: AuthService,
    private readonly modalCtrl: ModalController
  ) {}

  ngOnDestroy(): void {
    clearInterval(this.contador);
  }

  get codigoValido(): boolean {
    return /^\d{6}$/.test(this.codigo.trim());
  }

  async enviarCodigo(): Promise<void> {
    if (this.enviando || this.reenviarEm > 0) {
      return;
    }
    this.enviando = true;
    this.erro = '';
    try {
      const resposta = await this.auth.solicitarVerificacao();
      this.reenviarEm = resposta.reenviarEmSegundos;
      this.contador = setInterval(() => {
        this.reenviarEm = Math.max(this.reenviarEm - 1, 0);
        if (this.reenviarEm === 0) {
          clearInterval(this.contador);
        }
      }, 1000);
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : 0;
      this.erro =
        status === 429
          ? 'Aguarde um pouco para pedir outro código.'
          : 'Não foi possível enviar o código agora.';
    } finally {
      this.enviando = false;
    }
  }

  async confirmar(): Promise<void> {
    if (this.confirmando || !this.codigoValido) {
      this.erro = this.codigoValido ? '' : 'Digite os 6 números do código.';
      return;
    }
    this.confirmando = true;
    this.erro = '';
    try {
      const status = await this.auth.confirmarVerificacao(this.codigo.trim());
      if (status.telefoneVerificado) {
        await this.modalCtrl.dismiss(null, 'verificado');
      }
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : 0;
      this.erro =
        status === 410
          ? 'Este código expirou. Peça outro.'
          : 'Código incorreto. Confira a mensagem no WhatsApp.';
    } finally {
      this.confirmando = false;
    }
  }

  fechar(): void {
    void this.modalCtrl.dismiss(null, 'cancel');
  }
}
