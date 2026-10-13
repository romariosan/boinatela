import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, NgZone, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { AlertController, ToastController } from '@ionic/angular/lazy';
import { Observable } from 'rxjs';
import {
  Anuncio,
  AnuncioStatus,
  AuthPayload,
  FOTO_PLACEHOLDER_GADO,
  ROTULOS,
  formatarPreco,
} from '../models/anuncio';
import { AnuncioService } from '../services/anuncio.service';
import { AuthService } from '../services/auth.service';
import { SessaoService } from '../services/sessao.service';
import { VerificacaoService } from '../services/verificacao.service';

@Component({
  selector: 'app-tab3',
  templateUrl: 'tab3.page.html',
  styleUrls: ['tab3.page.scss'],
  standalone: false,
})
export class Tab3Page implements OnInit {
  readonly usuario$: Observable<AuthPayload | null>;
  readonly fotoPlaceholder = FOTO_PLACEHOLDER_GADO;
  readonly rotulos = ROTULOS;

  meusAnuncios: Anuncio[] = [];
  carregandoAnuncios = false;
  ocupado: number | null = null;

  private primeiraEntrada = true;

  constructor(
    private readonly auth: AuthService,
    private readonly anunciosApi: AnuncioService,
    private readonly sessao: SessaoService,
    private readonly verificacao: VerificacaoService,
    private readonly router: Router,
    private readonly alertCtrl: AlertController,
    private readonly toastCtrl: ToastController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef,
    private readonly destroyRef: DestroyRef
  ) {
    this.usuario$ = this.auth.currentUser$;
  }

  ngOnInit(): void {
    this.auth.isLoggedIn$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(logado => {
      if (logado) {
        void this.carregarMeusAnuncios();
      } else {
        this.atualizar(() => (this.meusAnuncios = []));
      }
    });
  }

  ionViewWillEnter(): void {
    // Na primeira entrada a inscrição do ngOnInit já carregou; depois, recarrega para pegar anúncios novos.
    if (this.primeiraEntrada) {
      this.primeiraEntrada = false;
      return;
    }
    if (this.auth.logado) {
      void this.carregarMeusAnuncios();
    }
  }

  async entrar(): Promise<void> {
    await this.sessao.exigirLogin();
  }

  verificarWhatsapp(): void {
    void this.verificacao.exigirVerificacao();
  }

  publicar(): void {
    void this.router.navigate(['/tabs/publicar']);
  }

  verAnuncio(id: number): void {
    void this.router.navigate(['/tabs/tab3/anuncio', id]);
  }

  editar(id: number): void {
    void this.router.navigate(['/tabs/editar', id]);
  }

  async alternarVendido(anuncio: Anuncio): Promise<void> {
    const status: AnuncioStatus = anuncio.status === 'VENDIDO' ? 'ATIVO' : 'VENDIDO';
    this.atualizar(() => (this.ocupado = anuncio.id));
    try {
      const atualizado = await this.anunciosApi.alterarStatus(anuncio.id, status);
      this.atualizar(() => {
        this.meusAnuncios = this.meusAnuncios.map(item => (item.id === atualizado.id ? atualizado : item));
      });
      await this.avisar(
        status === 'VENDIDO' ? 'Marcado como vendido. Ele saiu da vitrine.' : 'Anúncio reativado na vitrine.',
        'success'
      );
    } catch (erro) {
      await this.tratarErroAcao(erro, anuncio.id);
    } finally {
      this.atualizar(() => (this.ocupado = null));
    }
  }

  async confirmarExclusao(anuncio: Anuncio): Promise<void> {
    const alerta = await this.alertCtrl.create({
      header: 'Excluir anúncio',
      message: 'Deseja realmente excluir este anúncio?',
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        { text: 'Excluir', role: 'destructive' },
      ],
    });
    await alerta.present();
    const { role } = await alerta.onDidDismiss();
    if (role !== 'destructive') {
      return;
    }

    this.atualizar(() => (this.ocupado = anuncio.id));
    try {
      await this.anunciosApi.excluir(anuncio.id);
      this.atualizar(() => (this.meusAnuncios = this.meusAnuncios.filter(item => item.id !== anuncio.id)));
      await this.avisar('Anúncio excluído.', 'success');
    } catch (erro) {
      await this.tratarErroAcao(erro, anuncio.id);
    } finally {
      this.atualizar(() => (this.ocupado = null));
    }
  }

  async sair(): Promise<void> {
    this.auth.sair();
    await this.avisar('Você saiu da conta.', 'medium');
  }

  precoLabel(anuncio: Anuncio): string {
    return formatarPreco(anuncio);
  }

  iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/).filter(Boolean);
    const letras = partes.length > 1 ? partes[0][0] + partes[partes.length - 1][0] : (partes[0] ?? '?').slice(0, 2);
    return letras.toUpperCase();
  }

  formatarWhatsapp(numero: string): string {
    const digitos = numero.replace(/\D/g, '');
    if (digitos.length === 11) {
      return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 7)}-${digitos.slice(7)}`;
    }
    if (digitos.length === 10) {
      return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 6)}-${digitos.slice(6)}`;
    }
    return numero;
  }

  usarPlaceholder(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (!img.src.endsWith(FOTO_PLACEHOLDER_GADO)) {
      img.src = FOTO_PLACEHOLDER_GADO;
    }
  }

  private async carregarMeusAnuncios(): Promise<void> {
    this.atualizar(() => (this.carregandoAnuncios = true));
    try {
      const anuncios = await this.anunciosApi.meus();
      this.atualizar(() => {
        this.meusAnuncios = anuncios;
        this.carregandoAnuncios = false;
      });
    } catch (erro) {
      this.atualizar(() => (this.carregandoAnuncios = false));
      const status = erro instanceof HttpErrorResponse ? erro.status : 0;
      if (status === 401 || status === 403) {
        this.auth.sair();
        await this.avisar('Sua sessão expirou. Entre de novo.', 'warning');
      } else {
        await this.avisar('Não foi possível carregar seus anúncios.', 'warning');
      }
    }
  }

  private async tratarErroAcao(erro: unknown, anuncioId: number): Promise<void> {
    const status = erro instanceof HttpErrorResponse ? erro.status : 0;
    if (status === 401 || status === 403) {
      this.auth.sair();
      await this.avisar('Sua sessão expirou. Entre de novo.', 'warning');
    } else if (status === 404) {
      this.atualizar(() => (this.meusAnuncios = this.meusAnuncios.filter(item => item.id !== anuncioId)));
      await this.avisar('Este anúncio já não existe.', 'warning');
    } else {
      await this.avisar('Não foi possível concluir. Verifique a conexão e tente de novo.', 'danger');
    }
  }

  private atualizar(mudanca: () => void): void {
    this.zone.run(() => {
      mudanca();
      this.cdr.detectChanges();
    });
  }

  private async avisar(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, color, duration: 2500 });
    await toast.present();
  }
}
