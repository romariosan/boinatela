import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, NgZone, OnDestroy } from '@angular/core';
import { ModalController, ToastController } from '@ionic/angular/lazy';
import { AuthService } from '../services/auth.service';
import { VerificacaoService } from '../services/verificacao.service';

type Modo = 'login' | 'cadastro' | 'recuperar';
type EtapaRecuperacao = 'solicitar' | 'redefinir';

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
  modo: Modo = 'login';
  etapa: EtapaRecuperacao = 'solicitar';
  nome = '';
  whatsapp = '';
  senha = '';
  codigo = '';
  novaSenha = '';
  confirmarSenha = '';
  reenviarEm = 0;
  tentouRedefinir = false;
  carregando = false;

  private contador?: ReturnType<typeof setInterval>;
  private destruido = false;

  constructor(
    private readonly modalCtrl: ModalController,
    private readonly auth: AuthService,
    private readonly verificacao: VerificacaoService,
    private readonly toastCtrl: ToastController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  get titulo(): string {
    return TITULOS[this.modo];
  }

  get codigoValido(): boolean {
    return /^\d{4}$/.test(this.codigo);
  }

  get senhaCurta(): boolean {
    return this.novaSenha.length < 4;
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

  async enviar(): Promise<void> {
    this.carregando = true;
    try {
      // login() e cadastrar() já chamam auth.iniciarSessao(), que emite o estado logado para o app todo.
      const cadastro = this.modo === 'cadastro';
      if (cadastro) {
        await this.auth.cadastrar(this.nome, this.whatsapp, this.senha);
      } else {
        await this.auth.login(this.whatsapp, this.senha);
      }
      await this.modalCtrl.dismiss({ ok: true });
      await this.avisar('Bem-vindo ao CompreGado!', 'success', 'top');
      if (cadastro) {
        void this.verificacao.exigirVerificacao();
      }
    } catch {
      await this.avisar(
        this.modo === 'cadastro'
          ? 'Não foi possível criar a conta. Confira os dados ou se o WhatsApp já está cadastrado.'
          : 'Não foi possível entrar. Confira o WhatsApp e a senha.',
        'danger',
        'bottom'
      );
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

  private async avisar(message: string, color: string, position: 'top' | 'bottom'): Promise<void> {
    const toast = await this.toastCtrl.create({ message, color, position, duration: 2500 });
    await toast.present();
  }
}
