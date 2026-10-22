import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, NgZone, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { AlertController, RefresherCustomEvent, ToastController } from '@ionic/angular/lazy';
import { Observable } from 'rxjs';
import {
  Anuncio,
  AnuncioStatus,
  AuthPayload,
  FOTO_PLACEHOLDER_GADO,
  formatarPreco,
  resumoLote,
} from '../models/anuncio';
import { AnuncioService } from '../services/anuncio.service';
import { pedidoNaoSaiu, registrarFalhaHttp, sessaoInvalida } from '../services/conexao.service';
import { AuthService } from '../services/auth.service';
import { FilaPublicacaoService, PublicacaoPendente } from '../services/fila-publicacao.service';
import { PlanoProService, limiteDeAnunciosAtingido } from '../services/plano-pro.service';
import { SessaoService } from '../services/sessao.service';
import { TemaService } from '../services/tema.service';
import { VerificacaoService } from '../services/verificacao.service';

type FalhaLista = 'sem-conexao' | 'servidor';

@Component({
  selector: 'app-tab3',
  templateUrl: 'tab3.page.html',
  styleUrls: ['tab3.page.scss'],
  standalone: false,
})
export class Tab3Page implements OnInit {
  readonly usuario$: Observable<AuthPayload | null>;
  readonly pendentes$: Observable<PublicacaoPendente[]>;
  readonly modoSol$: Observable<boolean>;
  readonly fotoPlaceholder = FOTO_PLACEHOLDER_GADO;
  readonly esqueletos = [1, 2];
  readonly rotuloStatus: Record<AnuncioStatus, string> = {
    ATIVO: 'Na vitrine',
    PAUSADO: 'Pausado - Renove o PRO',
    VENDIDO: 'Vendido',
  };

  meusAnuncios: Anuncio[] = [];
  carregandoAnuncios = false;
  falhaAnuncios: FalhaLista | null = null;
  ocupado: number | null = null;

  private primeiraEntrada = true;

  constructor(
    private readonly auth: AuthService,
    private readonly anunciosApi: AnuncioService,
    private readonly fila: FilaPublicacaoService,
    readonly tema: TemaService,
    private readonly sessao: SessaoService,
    private readonly verificacao: VerificacaoService,
    private readonly planoPro: PlanoProService,
    private readonly router: Router,
    private readonly alertCtrl: AlertController,
    private readonly toastCtrl: ToastController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef,
    private readonly destroyRef: DestroyRef
  ) {
    this.usuario$ = this.auth.currentUser$;
    this.pendentes$ = this.fila.pendentes$;
    this.modoSol$ = this.tema.modoSol$;
  }

  ngOnInit(): void {
    this.auth.isLoggedIn$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(logado => {
      if (logado) {
        void this.carregarMeusAnuncios();
      } else {
        this.atualizar(() => {
          this.meusAnuncios = [];
          this.falhaAnuncios = null;
        });
      }
    });
    // Anúncio da fila que saiu quando o sinal voltou: entra na lista sem o vendedor precisar atualizar.
    this.fila.publicados$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.auth.logado) {
        void this.carregarMeusAnuncios();
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

  alterarSenha(): void {
    void this.router.navigate(['/tabs/alterar-senha']);
  }

  editar(id: number): void {
    void this.router.navigate(['/tabs/editar', id]);
  }

  async alternarVendido(anuncio: Anuncio): Promise<void> {
    const status: AnuncioStatus = anuncio.status === 'ATIVO' ? 'VENDIDO' : 'ATIVO';
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
      if (limiteDeAnunciosAtingido(erro)) {
        await this.planoPro.exibirPaywallPro();
      } else {
        await this.tratarErroAcao(erro, anuncio.id);
      }
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

  recarregar(): void {
    void this.carregarMeusAnuncios();
  }

  async atualizarPuxando(event: Event): Promise<void> {
    await Promise.all([this.carregarMeusAnuncios(), this.fila.processar()]);
    await (event as RefresherCustomEvent).target.complete();
  }

  async tentarPublicarAgora(id: string): Promise<void> {
    await this.fila.tentarDeNovo(id);
  }

  async confirmarDescarte(item: PublicacaoPendente): Promise<void> {
    const alerta = await this.alertCtrl.create({
      header: 'Descartar anúncio',
      message: `"${item.dados.titulo}" ainda não foi publicado. Descartar apaga ele e as fotos deste aparelho.`,
      buttons: [
        { text: 'Manter', role: 'cancel' },
        { text: 'Descartar', role: 'destructive' },
      ],
    });
    await alerta.present();
    const { role } = await alerta.onDidDismiss();
    if (role === 'destructive') {
      await this.fila.descartar(item.id);
    }
  }

  precoLabel(anuncio: Pick<Anuncio, 'preco' | 'tipoVenda'>): string {
    return formatarPreco(anuncio);
  }

  resumo(anuncio: Anuncio): string | null {
    return resumoLote(anuncio);
  }

  porId(_indice: number, anuncio: Anuncio): number {
    return anuncio.id;
  }

  porIdPendente(_indice: number, item: PublicacaoPendente): string {
    return item.id;
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

  /** Com a lista já na tela, uma falha ao atualizar só avisa: os anúncios mostrados continuam valendo. */
  private async carregarMeusAnuncios(): Promise<void> {
    this.atualizar(() => {
      this.carregandoAnuncios = true;
      this.falhaAnuncios = null;
    });
    try {
      const anuncios = await this.anunciosApi.meus();
      this.atualizar(() => {
        this.meusAnuncios = anuncios;
        this.carregandoAnuncios = false;
      });
    } catch (erro) {
      registrarFalhaHttp('meus-anuncios', erro);
      const semConexao = pedidoNaoSaiu(erro);
      this.atualizar(() => {
        this.carregandoAnuncios = false;
        this.falhaAnuncios = semConexao ? 'sem-conexao' : 'servidor';
      });
      if (sessaoInvalida(erro)) {
        this.auth.sair();
        await this.avisar('Sua sessão expirou. Entre de novo.', 'warning');
      } else if (this.meusAnuncios.length) {
        await this.avisar(
          semConexao ? 'Sem internet: a lista pode estar desatualizada.' : 'Não foi possível atualizar seus anúncios.',
          'warning'
        );
      }
    }
  }

  private async tratarErroAcao(erro: unknown, anuncioId: number): Promise<void> {
    registrarFalhaHttp('acao-anuncio', erro);
    const status = erro instanceof HttpErrorResponse ? erro.status : -1;
    if (sessaoInvalida(erro)) {
      this.auth.sair();
      await this.avisar('Sua sessão expirou. Entre de novo.', 'warning');
    } else if (status === 404) {
      this.atualizar(() => (this.meusAnuncios = this.meusAnuncios.filter(item => item.id !== anuncioId)));
      await this.avisar('Este anúncio já não existe.', 'warning');
    } else if (pedidoNaoSaiu(erro)) {
      await this.avisar('Sem internet. Tente de novo quando tiver sinal.', 'warning');
    } else {
      await this.avisar('Não foi possível concluir. Tente de novo.', 'danger');
    }
  }

  private atualizar(mudanca: () => void): void {
    this.zone.run(() => {
      mudanca();
      this.cdr.detectChanges();
    });
  }

  private async avisar(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      color,
      position: 'top',
      duration: color === 'success' ? 2600 : 4000,
      buttons: color === 'success' ? [] : [{ text: 'OK', role: 'cancel' }],
    });
    await toast.present();
  }
}
