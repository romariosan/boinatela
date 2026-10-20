import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, NgZone, OnDestroy } from '@angular/core';
import { LoadingController, ModalController, ToastController } from '@ionic/angular/lazy';
import { environment } from '../../environments/environment';
import { AuthService, MINIMO_SENHA } from '../services/auth.service';

type Modo = 'login' | 'cadastro' | 'recuperar';
type EtapaRecuperacao = 'solicitar' | 'redefinir';
type EtapaCadastro = 'dados' | 'codigo';

const TITULOS: Record<Modo, string> = {
  login: 'Entrar',
  cadastro: 'Criar conta',
  recuperar: 'Recuperar senha',
};

@Component({
  selector: 'app-auth-modal',
  templateUrl: './auth-modal.component.html',
  styleUrls: ['./auth-modal.component.scss'],
  standalone: false,
})
export class AuthModalComponent implements OnDestroy {
  readonly minimo = MINIMO_SENHA;
  modo: Modo = 'login';
  etapa: EtapaRecuperacao = 'solicitar';
  etapaCadastro: EtapaCadastro = 'dados';
  nome = '';
  whatsapp = '';
  senha = '';
  codigo = '';
  codigoCadastro = '';
  novaSenha = '';
  confirmarSenha = '';
  reenviarEm = 0;
  tentouRedefinir = false;
  tentouEnviar = false;
  carregando = false;

  private contador?: ReturnType<typeof setInterval>;
  private destruido = false;

  constructor(
    private readonly modalCtrl: ModalController,
    private readonly auth: AuthService,
    private readonly toastCtrl: ToastController,
    private readonly loadingCtrl: LoadingController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  get titulo(): string {
    return TITULOS[this.modo];
  }

  get codigoValido(): boolean {
    return /^\d{4}$/.test(this.codigo);
  }

  get codigoCadastroValido(): boolean {
    return /^\d{6}$/.test(this.codigoCadastro.trim());
  }

  get simuladorWhatsapp(): string | null {
    return environment.whatsappSimulado;
  }

  get senhaCurta(): boolean {
    return this.novaSenha.length < MINIMO_SENHA;
  }

  get senhaInvalida(): boolean {
    return this.modo === 'cadastro' ? this.senha.length < MINIMO_SENHA : !this.senha;
  }

  get senhasDiferentes(): boolean {
    return this.novaSenha !== this.confirmarSenha;
  }

  ngOnDestroy(): void {
    this.destruido = true;
    clearInterval(this.contador);
  }

  fechar(): void {
    void this.modalCtrl.dismiss(null);
  }

  irPara(modo: Modo): void {
    this.modo = modo;
    this.tentouEnviar = false;
    this.etapaCadastro = 'dados';
    if (modo === 'recuperar') {
      this.etapa = 'solicitar';
      this.tentouRedefinir = false;
    }
  }

  /** O limite de reenvio é por número no servidor; outro número não precisa esperar o contador. */
  trocarNumero(): void {
    clearInterval(this.contador);
    this.reenviarEm = 0;
    this.tentouRedefinir = false;
    this.etapa = 'solicitar';
  }

  get whatsappIncompleto(): boolean {
    return this.whatsapp.replace(/\D/g, '').length < 10;
  }

  /** Máscara "(61) 99999-9999": o número digitado fica legível e o erro de um dígito a mais ou a menos aparece. */
  aoDigitarWhatsapp(event: Event): void {
    const digitos = String((event as CustomEvent).detail.value ?? '').replace(/\D/g, '').slice(0, 11);
    let texto = digitos;
    if (digitos.length > 2) {
      const meio = digitos.length > 10 ? 7 : 6;
      texto = `(${digitos.slice(0, 2)}) ${digitos.slice(2, meio)}`;
      if (digitos.length > meio) {
        texto += `-${digitos.slice(meio)}`;
      }
    }
    this.whatsapp = texto;
    (event.target as HTMLIonInputElement).value = texto;
  }

  async enviar(): Promise<void> {
    if (this.carregando) {
      return;
    }
    this.tentouEnviar = true;
    const cadastro = this.modo === 'cadastro';
    if ((cadastro && !this.nome.trim()) || this.whatsappIncompleto || this.senhaInvalida) {
      return;
    }
    this.carregando = true;
    try {
      if (cadastro) {
        const resposta = await this.auth.solicitarCadastro(this.nome.trim(), this.whatsapp, this.senha);
        this.atualizar(() => {
          this.etapaCadastro = 'codigo';
          this.codigoCadastro = '';
          this.iniciarContador(resposta.reenviarEmSegundos);
        });
        await this.avisar('Enviamos o código de 6 dígitos para o seu WhatsApp.', 'success', 'top');
      } else {
        await this.auth.login(this.whatsapp, this.senha);
        await this.modalCtrl.dismiss({ ok: true });
        await this.avisar(`Bem-vindo ao CompreGado, ${this.primeiroNome()}!`, 'success', 'top');
      }
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : -1;
      if (status === 0 || !navigator.onLine) {
        await this.avisar('Sem internet. Tente de novo quando tiver sinal.', 'warning', 'top');
      } else if (cadastro && status === 409) {
        await this.avisarComAcao('Este WhatsApp já tem conta.', 'Entrar', () => this.irPara('login'));
      } else if (cadastro && status === 429) {
        await this.avisar('Aguarde um pouco para pedir outro código.', 'warning', 'top');
      } else if (!cadastro && status === 401) {
        await this.avisarComAcao('WhatsApp ou senha incorretos.', 'Esqueci a senha', () => this.irPara('recuperar'));
      } else {
        await this.avisar('Não foi possível concluir agora. Tente de novo.', 'danger', 'top');
      }
    } finally {
      this.atualizar(() => (this.carregando = false));
    }
  }

  async confirmarConta(): Promise<void> {
    if (this.carregando || !this.codigoCadastroValido) {
      return;
    }
    this.carregando = true;
    try {
      await this.auth.confirmarCadastro(this.whatsapp, this.codigoCadastro.trim());
      await this.modalCtrl.dismiss({ ok: true });
      await this.avisar(`Bem-vindo ao CompreGado, ${this.primeiroNome()}!`, 'success', 'top');
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : 0;
      const mensagens: Record<number, string> = {
        400: 'Código incorreto. Confira a mensagem no WhatsApp.',
        410: 'Este código expirou. Peça outro.',
        409: 'Este WhatsApp já tem conta.',
      };
      await this.avisar(mensagens[status] ?? 'Não foi possível criar a conta agora.', 'danger', 'top');
    } finally {
      this.atualizar(() => (this.carregando = false));
    }
  }

  async enviarCodigo(): Promise<void> {
    if (this.carregando || this.reenviarEm > 0) {
      return;
    }
    if (this.whatsapp.replace(/\D/g, '').length < 10) {
      await this.avisar('Informe o WhatsApp com DDD.', 'warning', 'bottom');
      return;
    }
    this.carregando = true;
    const loading = await this.loadingCtrl.create({
      message: 'Enviando código...',
      spinner: 'crescent',
      backdropDismiss: false,
    });
    await loading.present();
    try {
      const resposta = await this.auth.esqueciSenha(this.whatsapp);
      this.atualizar(() => {
        this.etapa = 'redefinir';
        this.codigo = '';
        this.iniciarContador(resposta.reenviarEmSegundos);
      });
      await this.avisar('Enviamos o código para o seu WhatsApp.', 'success', 'top');
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : 0;
      const mensagens: Record<number, string> = {
        404: 'Número de WhatsApp não cadastrado.',
        429: 'Você pediu códigos demais. Aguarde um pouco e tente de novo.',
      };
      await this.avisar(
        mensagens[status] ?? 'Não foi possível enviar o código agora. Tente mais tarde.',
        'danger',
        'bottom'
      );
    } finally {
      this.atualizar(() => (this.carregando = false));
      await loading.dismiss();
    }
  }

  async redefinirEEntrar(): Promise<void> {
    if (this.carregando) {
      return;
    }
    this.tentouRedefinir = true;
    if (!this.codigoValido || this.senhaCurta || this.senhasDiferentes) {
      return;
    }
    this.carregando = true;
    try {
      await this.auth.redefinirSenha(this.whatsapp, this.codigo, this.novaSenha);
      await this.modalCtrl.dismiss({ ok: true });
      await this.avisar('Senha alterada com sucesso! Você já está logado.', 'success', 'top');
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : 0;
      if (status === 410) {
        this.atualizar(() => {
          this.codigo = '';
          this.reenviarEm = 0;
          clearInterval(this.contador);
        });
      }
      const mensagens: Record<number, string> = {
        400: 'Código incorreto. Confira a mensagem no WhatsApp.',
        410: 'Este código expirou ou foi bloqueado. Peça um novo.',
      };
      await this.avisar(mensagens[status] ?? 'Não foi possível redefinir a senha agora.', 'danger', 'bottom');
    } finally {
      this.atualizar(() => (this.carregando = false));
    }
  }

  abrirSimulador(): void {
    const numero = encodeURIComponent(this.whatsapp.replace(/\D/g, ''));
    window.open(`${this.simuladorWhatsapp}?numero=${numero}`, '_blank');
  }

  private iniciarContador(segundos: number): void {
    clearInterval(this.contador);
    this.reenviarEm = segundos;
    this.contador = setInterval(() => {
      this.atualizar(() => {
        this.reenviarEm = Math.max(this.reenviarEm - 1, 0);
        if (this.reenviarEm === 0) {
          clearInterval(this.contador);
        }
      });
    }, 1000);
  }

  // Respostas HTTP e o setInterval podem rodar fora da zona do Angular. Depois do dismiss a view já foi
  // destruída e detectChanges() lançaria erro.
  private atualizar(mudanca: () => void): void {
    this.zone.run(() => {
      mudanca();
      if (!this.destruido) {
        this.cdr.detectChanges();
      }
    });
  }

  private primeiroNome(): string {
    return (this.auth.snapshot?.nome ?? '').trim().split(/\s+/)[0] || 'produtor';
  }

  private async avisar(message: string, color: string, position: 'top' | 'bottom'): Promise<void> {
    const toast = await this.toastCtrl.create({ message, color, position, duration: 3500 });
    await toast.present();
  }

  /** Erro com saída: em vez de só dizer o que deu errado, leva ao passo que resolve. */
  private async avisarComAcao(message: string, acao: string, aoTocar: () => void): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      color: 'warning',
      position: 'top',
      duration: 6000,
      buttons: [{ text: acao, handler: () => this.atualizar(aoTocar) }],
    });
    await toast.present();
  }
}
