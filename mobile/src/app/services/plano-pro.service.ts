import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AlertController } from '@ionic/angular/lazy';
import { environment } from '../../environments/environment';

/** Resposta da API quando o vendedor gratuito já tem o máximo de anúncios ativos. */
export function limiteDeAnunciosAtingido(erro: unknown): boolean {
  return erro instanceof HttpErrorResponse && erro.error?.erro === 'LIMITE_ANUNCIOS_ATINGIDO';
}

/** Plano Vendedor PRO: sem cobrança no app; o fechamento é manual, pelo WhatsApp. */
@Injectable({ providedIn: 'root' })
export class PlanoProService {
  private aberto = false;

  constructor(private readonly alertCtrl: AlertController) {}

  async exibirPaywallPro(): Promise<void> {
    // A fila offline pode bater no limite com vários anúncios seguidos: um aviso só.
    if (this.aberto) {
      return;
    }
    this.aberto = true;
    try {
      const alerta = await this.alertCtrl.create({
        header: 'Limite Atingido!',
        message:
          'Parece que você é um vendedor frequente. Torne-se um Vendedor PRO para ter anúncios ilimitados e destaque na região!',
        cssClass: 'alerta-plano-pro',
        buttons: [
          { text: 'Cancelar', role: 'cancel' },
          { text: 'Assinar via WhatsApp', cssClass: 'botao-assinar-pro', handler: () => this.abrirWhatsapp() },
        ],
      });
      await alerta.present();
      await alerta.onDidDismiss();
    } finally {
      this.aberto = false;
    }
  }

  private abrirWhatsapp(): void {
    const numero = environment.whatsappPlanoPro;
    const texto = encodeURIComponent(
      'Olá, atingi o limite de 3 anúncios gratuitos e quero me tornar um Vendedor PRO no CompreGado!'
    );
    window.open(`https://wa.me/${numero}?text=${texto}`, '_blank');
  }
}
