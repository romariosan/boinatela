import { Component, HostListener, Input, NgZone, OnDestroy, OnInit } from '@angular/core';
import { ModalController } from '@ionic/angular/lazy';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth.service';

const INTERVALO_CONSULTA_MS = 3000;

/** Fecha com role 'verificado' quando o backend confirma; 'cancel' se o usuário desistir. */
@Component({
  selector: 'app-verificar-whatsapp',
  templateUrl: './verificar-whatsapp.component.html',
  styleUrls: ['./verificar-whatsapp.component.scss'],
  standalone: false,
})
export class VerificarWhatsappComponent implements OnInit, OnDestroy {
  @Input({ required: true }) codigo!: string;
  @Input() numeroBot?: string | null;

  abriuWhatsapp = false;
  private timer?: ReturnType<typeof setInterval>;
  private consultando = false;
  private concluido = false;

  constructor(
    private readonly auth: AuthService,
    private readonly modalCtrl: ModalController,
    private readonly zone: NgZone
  ) {}

  ngOnInit(): void {
    this.timer = setInterval(() => void this.consultar(), INTERVALO_CONSULTA_MS);
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  /** Quem volta do WhatsApp não precisa esperar o próximo ciclo de 3 s. */
  @HostListener('document:visibilitychange')
  aoVoltarParaOApp(): void {
    if (document.visibilityState === 'visible') {
      void this.consultar();
    }
  }

  confirmar(): void {
    const texto = 'Quero+confirmar+meu+cadastro+no+CompreGado.+Codigo:+' + encodeURIComponent(this.codigo);
    const numero = this.numeroBot || environment.whatsappVerificacao;
    window.open(`https://wa.me/${numero}?text=${texto}`, '_system');
    this.abriuWhatsapp = true;
  }

  fechar(): void {
    void this.modalCtrl.dismiss(null, 'cancel');
  }

  private async consultar(): Promise<void> {
    if (this.consultando || this.concluido) {
      return;
    }
    this.consultando = true;
    try {
      const status = await this.auth.statusVerificacao();
      if (status.telefoneVerificado) {
        this.concluido = true;
        clearInterval(this.timer);
        this.auth.marcarVerificado();
        await this.modalCtrl.dismiss(null, 'verificado');
      } else if (status.codigoVerificacao && status.codigoVerificacao !== this.codigo) {
        const codigo = status.codigoVerificacao;
        this.zone.run(() => (this.codigo = codigo));
      }
    } catch {
      // Falha de rede: o próximo ciclo tenta de novo.
    } finally {
      this.consultando = false;
    }
  }
}
