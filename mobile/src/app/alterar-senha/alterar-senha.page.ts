import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { LoadingController, NavController, ToastController } from '@ionic/angular/lazy';
import { Subscription } from 'rxjs';
import { AuthService, MINIMO_SENHA } from '../services/auth.service';

/**
 * Regras entre campos ficam no próprio controle (e não no grupo): o Ionic pinta o campo de vermelho a
 * partir do estado do controle, então um erro só do grupo não apareceria na tela.
 */
function diferenteDe(irmao: string, erro: string) {
  return (controle: AbstractControl): ValidationErrors | null => {
    const outro = controle.parent?.get(irmao)?.value;
    return controle.value && controle.value === outro ? { [erro]: true } : null;
  };
}

function igualA(irmao: string, erro: string) {
  return (controle: AbstractControl): ValidationErrors | null => {
    const outro = controle.parent?.get(irmao)?.value;
    return controle.value && controle.value !== outro ? { [erro]: true } : null;
  };
}

@Component({
  selector: 'app-alterar-senha',
  templateUrl: 'alterar-senha.page.html',
  styleUrls: ['alterar-senha.page.scss'],
  standalone: false,
})
export class AlterarSenhaPage implements OnDestroy {
  readonly minimo = MINIMO_SENHA;

  readonly form = new FormGroup({
    senhaAtual: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    novaSenha: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(MINIMO_SENHA),
        Validators.maxLength(72),
        diferenteDe('senhaAtual', 'igualAAtual'),
      ],
    }),
    confirmarNovaSenha: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, igualA('novaSenha', 'naoConferem')],
    }),
  });

  /** Motivo da API para recusar a senha atual; some quando o usuário digita de novo. */
  recusaSenhaAtual = '';
  private enviando = false;
  private readonly assinaturas = new Subscription();

  constructor(
    private readonly auth: AuthService,
    private readonly loadingCtrl: LoadingController,
    private readonly toastCtrl: ToastController,
    private readonly navCtrl: NavController
  ) {
    const { senhaAtual, novaSenha, confirmarNovaSenha } = this.form.controls;
    // Mudar um campo pode resolver (ou criar) o erro do campo que depende dele.
    this.assinaturas.add(senhaAtual.valueChanges.subscribe(() => novaSenha.updateValueAndValidity()));
    this.assinaturas.add(novaSenha.valueChanges.subscribe(() => confirmarNovaSenha.updateValueAndValidity()));
  }

  ngOnDestroy(): void {
    this.assinaturas.unsubscribe();
  }

  ionViewWillEnter(): void {
    if (!this.auth.logado) {
      void this.navCtrl.navigateBack('/tabs/tab3');
    }
  }

  ionViewDidLeave(): void {
    // Senhas não ficam na memória da tela depois de sair dela.
    this.form.reset();
    this.recusaSenhaAtual = '';
  }

  get erroNovaSenha(): string {
    const controle = this.form.controls.novaSenha;
    if (controle.hasError('required')) {
      return 'Digite a nova senha.';
    }
    return controle.hasError('igualAAtual')
      ? 'A nova senha precisa ser diferente da atual.'
      : `Use pelo menos ${MINIMO_SENHA} caracteres.`;
  }

  get erroConfirmacao(): string {
    return this.form.controls.confirmarNovaSenha.hasError('required')
      ? 'Repita a nova senha.'
      : 'As senhas não conferem.';
  }

  aoDigitarSenhaAtual(): void {
    this.recusaSenhaAtual = '';
  }

  async alterar(): Promise<void> {
    if (this.enviando) {
      return;
    }
    // Os erros só aparecem em campos "tocados": ninguém abre a tela já em vermelho.
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      await this.avisar(
        this.form.controls.confirmarNovaSenha.hasError('naoConferem')
          ? 'A confirmação não confere com a nova senha.'
          : 'Confira os campos em vermelho.',
        'warning'
      );
      return;
    }

    this.enviando = true;
    const carregando = await this.loadingCtrl.create({ message: 'Alterando senha...', spinner: 'crescent' });
    await carregando.present();
    const { senhaAtual, novaSenha, confirmarNovaSenha } = this.form.getRawValue();
    try {
      await this.auth.alterarSenha(senhaAtual, novaSenha, confirmarNovaSenha);
      await carregando.dismiss();
      await this.avisar('Senha alterada! Em outros aparelhos, será preciso entrar de novo.', 'success');
      await this.navCtrl.navigateBack('/tabs/tab3');
    } catch (erro) {
      await carregando.dismiss();
      await this.tratarErro(erro);
    } finally {
      this.enviando = false;
    }
  }

  private async tratarErro(erro: unknown): Promise<void> {
    const status = erro instanceof HttpErrorResponse ? erro.status : -1;
    const corpo = erro instanceof HttpErrorResponse ? erro.error : null;
    if (status === 422) {
      const restantes = Number(corpo?.tentativasRestantes);
      const aviso =
        restantes === 1
          ? 'Senha atual incorreta. Resta 1 tentativa antes do bloqueio.'
          : restantes > 1
            ? `Senha atual incorreta. Restam ${restantes} tentativas.`
            : 'A senha atual está incorreta.';
      this.recusarSenhaAtual(aviso);
      await this.avisar(aviso, 'danger');
    } else if (status === 429) {
      const minutos = Number(corpo?.minutosRestantes) || 15;
      const espera = minutos === 1 ? '1 minuto' : `${minutos} minutos`;
      this.recusarSenhaAtual(`Bloqueado por segurança. Tente de novo em ${espera}.`);
      await this.avisar(
        `Muitas tentativas erradas. Por segurança, espere ${espera} para tentar de novo. Se esqueceu a senha, use "Esqueceu a senha?" na tela de entrar.`,
        'warning'
      );
    } else if (status === 400) {
      await this.avisar('O servidor recusou a nova senha. Confira os campos e tente de novo.', 'danger');
    } else if (status === 401 || status === 403) {
      this.auth.sair();
      await this.avisar('Sua sessão expirou. Entre de novo para alterar a senha.', 'warning');
      await this.navCtrl.navigateBack('/tabs/tab3');
    } else if (status === 0 || !navigator.onLine) {
      await this.avisar('Sem internet. Sua senha não foi alterada; tente de novo quando tiver sinal.', 'warning');
    } else {
      await this.avisar('Não foi possível alterar a senha agora. Tente de novo em instantes.', 'danger');
    }
  }

  private recusarSenhaAtual(motivo: string): void {
    const senhaAtual = this.form.controls.senhaAtual;
    senhaAtual.setValue('');
    senhaAtual.setErrors({ recusada: true });
    senhaAtual.markAsTouched();
    this.recusaSenhaAtual = motivo;
  }

  private async avisar(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      color,
      position: 'top',
      duration: color === 'success' ? 3000 : 4500,
      buttons: color === 'success' ? [] : [{ text: 'OK', role: 'cancel' }],
    });
    await toast.present();
  }
}
