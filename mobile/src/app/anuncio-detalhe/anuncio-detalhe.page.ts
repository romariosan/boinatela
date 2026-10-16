import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, NgZone, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Capacitor } from '@capacitor/core';
import { AlertController, LoadingController, ToastController } from '@ionic/angular/lazy';
import { environment } from '../../environments/environment';
import type { SwiperContainer } from 'swiper/element';
import {
  Anuncio,
  FOTO_PLACEHOLDER_GADO,
  ROTULOS,
  formatarPreco,
  precoComplementar,
  unidadePreco,
} from '../models/anuncio';
import { AnuncioService } from '../services/anuncio.service';
import { AuthService } from '../services/auth.service';

type FalhaDetalhe = 'nao-encontrado' | 'sem-conexao' | 'servidor';

interface ItemFicha {
  icone: string;
  rotulo: string;
  valor: string;
}

@Component({
  selector: 'app-anuncio-detalhe',
  templateUrl: 'anuncio-detalhe.page.html',
  styleUrls: ['anuncio-detalhe.page.scss'],
  standalone: false,
})
export class AnuncioDetalhePage {
  @ViewChild('carrossel') carrossel?: ElementRef<SwiperContainer>;

  anuncio: Anuncio | null = null;
  ficha: ItemFicha[] = [];
  carregando = false;
  falha: FalhaDetalhe | null = null;
  abrindoContato = false;
  modalContato = false;
  telefoneContato = '';
  modalReivindicacao = false;
  etapaReivindicacao: 'telefone' | 'codigo' = 'telefone';
  telefoneReivindicacao = '';
  codigoReivindicacao = '';
  enviandoOtp = false;
  readonly googlePlayUrl = environment.googlePlayUrl;
  readonly fotoPlaceholder = FOTO_PLACEHOLDER_GADO;
  readonly rotulos = ROTULOS;

  private id = 0;
  private tokenVendido: string | null = null;
  private perguntouVendido = false;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly anunciosApi: AnuncioService,
    private readonly toastCtrl: ToastController,
    private readonly alertCtrl: AlertController,
    private readonly loadingCtrl: LoadingController,
    private readonly auth: AuthService,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ionViewWillEnter(): void {
    this.perguntouVendido = false;
    this.id = this.idDaRota();
    this.tokenVendido = this.tokenDaRota();
    void this.carregar();
  }

  modalTermos = false;

  get loteVendido(): boolean {
    if (!this.anuncio || this.anuncio.statusProcessamento === 'RASCUNHO_SEM_FOTO') {
      return false;
    }
    return this.anuncio.status === 'VENDIDO' || this.anuncio.ativo === false;
  }

  /** Lote ouvido no grupo e ainda não confirmado por quem postou. */
  get agregadoDeGrupo(): boolean {
    return !!this.anuncio?.capturado && !this.anuncio.reivindicado;
  }

  /** Publicado pelo dono no app, ou reivindicado depois da escuta. */
  get loteConfirmado(): boolean {
    return !!this.anuncio && (!!this.anuncio.reivindicado || !this.anuncio.capturado);
  }

  /** O APK da loja já é o app. O botão só faz sentido no link público /lote. */
  get mostrarBaixarApp(): boolean {
    if (Capacitor.isNativePlatform()) {
      return false;
    }
    return !this.router.url.startsWith('/tabs');
  }

  async carregar(): Promise<void> {
    this.atualizar(() => {
      this.carregando = true;
      this.falha = null;
    });
    try {
      const anuncio = await this.anunciosApi.buscar(this.id);
      this.atualizar(() => {
        this.anuncio = anuncio;
        this.ficha = this.montarFicha(anuncio);
        this.carregando = false;
      });
      this.iniciarCarrossel();
      if (this.tokenVendido && this.router.url.includes('/vendido')) {
        void this.perguntarMarcarVendido();
      }
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : -1;
      this.atualizar(() => {
        this.carregando = false;
        this.falha = status === 404 ? 'nao-encontrado' : status === 0 || !navigator.onLine ? 'sem-conexao' : 'servidor';
      });
    }
  }

  async falarComVendedor(): Promise<void> {
    if (!this.anuncio || this.abrindoContato || this.loteVendido) {
      return;
    }
    this.telefoneContato = '';
    this.modalContato = true;
  }

  aoDigitarTelefoneContato(event: Event): void {
    this.telefoneContato = this.mascaraWhatsapp(event);
  }

  async irParaWhatsapp(): Promise<void> {
    if (!this.anuncio || this.abrindoContato) {
      return;
    }
    if (this.telefoneContato.replace(/\D/g, '').length < 10) {
      await this.avisar('Informe o WhatsApp com DDD.', 'warning');
      return;
    }
    this.atualizar(() => (this.abrindoContato = true));
    try {
      const contato = await this.anunciosApi.contato(this.anuncio.id, this.telefoneContato);
      this.modalContato = false;
      window.open(contato.urlWhatsapp, '_blank');
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : -1;
      const mensagem =
        status === 409
          ? 'Este anúncio não está mais disponível.'
          : status === 400
            ? 'Informe um WhatsApp de celular com DDD.'
            : status === 0 || !navigator.onLine
              ? 'Sem internet. Tente de novo quando tiver sinal.'
              : 'Não foi possível abrir o WhatsApp do vendedor. Tente de novo.';
      await this.avisar(mensagem, status === 409 ? 'warning' : 'danger');
    } finally {
      this.atualizar(() => (this.abrindoContato = false));
    }
  }

  private mascaraWhatsapp(event: Event): string {
    const digitos = String((event as CustomEvent).detail.value ?? '').replace(/\D/g, '').slice(0, 11);
    let texto = digitos;
    if (digitos.length > 2) {
      const meio = digitos.length > 10 ? 7 : 6;
      texto = `(${digitos.slice(0, 2)}) ${digitos.slice(2, meio)}`;
      if (digitos.length > meio) {
        texto += `-${digitos.slice(meio)}`;
      }
    }
    (event.target as HTMLIonInputElement).value = texto;
    return texto;
  }

  /** Compartilhar com sócio ou comprador é comum no gado; sem Web Share (WebView Android), vai pelo WhatsApp. */
  async compartilhar(): Promise<void> {
    if (!this.anuncio) {
      return;
    }
    const url = `${environment.webBaseUrl}/lote/${this.anuncio.id}`;
    const texto = `${this.anuncio.titulo} — ${this.precoLabel(this.anuncio)} em ${this.anuncio.cidade}. Veja no CompreGado:`;
    if (navigator.share) {
      try {
        await navigator.share({ title: this.anuncio.titulo, text: texto, url });
      } catch {
        // Usuário fechou a folha de compartilhamento.
      }
      return;
    }
    window.open(`https://wa.me/?text=${encodeURIComponent(`${texto} ${url}`)}`, '_blank');
  }

  abrirReivindicacao(): void {
    if (this.enviandoOtp) {
      return;
    }
    this.etapaReivindicacao = 'telefone';
    this.telefoneReivindicacao = '';
    this.codigoReivindicacao = '';
    this.modalReivindicacao = true;
  }

  fecharReivindicacao(): void {
    if (this.enviandoOtp) {
      return;
    }
    this.modalReivindicacao = false;
  }

  aoDigitarTelefoneReivindicacao(event: Event): void {
    this.telefoneReivindicacao = this.mascaraWhatsapp(event);
  }

  async enviarCodigoReivindicacao(): Promise<void> {
    if (this.enviandoOtp || !this.anuncio) {
      return;
    }
    if (this.telefoneReivindicacao.replace(/\D/g, '').length < 10) {
      await this.avisar('Informe o WhatsApp com DDD.', 'warning');
      return;
    }
    await this.comLoading('Enviando código...', async () => {
      await this.auth.enviarOtpCadastroOuReivindicar(this.telefoneReivindicacao, this.anuncio!.id);
      this.atualizar(() => {
        this.etapaReivindicacao = 'codigo';
        this.codigoReivindicacao = '';
      });
      await this.avisar('Enviamos o código para o seu WhatsApp.', 'success');
    });
  }

  async confirmarReivindicacao(): Promise<void> {
    if (this.enviandoOtp || !this.anuncio) {
      return;
    }
    if (!/^\d{6}$/.test(this.codigoReivindicacao)) {
      await this.avisar('Digite os 6 números do código.', 'warning');
      return;
    }
    await this.comLoading('Confirmando código...', async () => {
      const atualizado = await this.anunciosApi.reivindicar(
        this.anuncio!.id,
        this.telefoneReivindicacao,
        this.codigoReivindicacao
      );
      this.atualizar(() => {
        this.anuncio = atualizado;
        this.ficha = this.montarFicha(atualizado);
        this.modalReivindicacao = false;
      });
      await this.avisar('Anúncio reivindicado. Baixe o app para editar.', 'success');
    });
  }

  private idDaRota(): number {
    let rota: ActivatedRoute | null = this.route;
    while (rota) {
      const id = rota.snapshot.paramMap.get('id');
      if (id) {
        return Number(id);
      }
      rota = rota.parent;
    }
    return 0;
  }

  private tokenDaRota(): string | null {
    let rota: ActivatedRoute | null = this.route;
    while (rota) {
      const token = rota.snapshot.queryParamMap.get('token');
      if (token) {
        return token;
      }
      rota = rota.parent;
    }
    return null;
  }

  private async perguntarMarcarVendido(): Promise<void> {
    if (this.perguntouVendido || !this.anuncio) {
      return;
    }
    this.perguntouVendido = true;
    if (this.loteVendido) {
      await this.avisar('Este lote já está marcado como vendido.', 'warning');
      return;
    }
    const alerta = await this.alertCtrl.create({
      header: 'Marcar como vendido',
      message: 'O lote sai da vitrine e o botão de contato é desligado.',
      buttons: [
        { text: 'Agora não', role: 'cancel' },
        { text: 'Marcar como vendido', handler: () => void this.confirmarVendido() },
      ],
    });
    await alerta.present();
  }

  private async confirmarVendido(): Promise<void> {
    if (!this.anuncio) {
      return;
    }
    try {
      const atualizado = await this.anunciosApi.marcarVendido(this.anuncio.id, this.tokenVendido);
      this.atualizar(() => {
        this.anuncio = atualizado;
        this.ficha = this.montarFicha(atualizado);
      });
      await this.avisar('Lote marcado como vendido.', 'success');
    } catch {
      await this.avisar('Não foi possível marcar o lote como vendido.', 'danger');
    }
  }

  usarPlaceholder(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (!img.src.endsWith(FOTO_PLACEHOLDER_GADO)) {
      img.src = FOTO_PLACEHOLDER_GADO;
    }
  }

  precoLabel(anuncio: Anuncio): string {
    return formatarPreco(anuncio);
  }

  complemento(anuncio: Anuncio): string | null {
    return precoComplementar(anuncio);
  }

  unidade(anuncio: Anuncio): string {
    return unidadePreco(anuncio.tipoVenda);
  }

  private montarFicha(anuncio: Anuncio): ItemFicha[] {
    const ficha: ItemFicha[] = [
      { icone: 'paw-outline', rotulo: 'Categoria', valor: ROTULOS[anuncio.categoriaAnimal] },
      { icone: 'ribbon-outline', rotulo: 'Raça', valor: ROTULOS[anuncio.raca] },
    ];
    if (anuncio.quantidade) {
      const cabecas = anuncio.quantidade === 1 ? '1 cabeça' : `${anuncio.quantidade} cabeças`;
      ficha.push({ icone: 'layers-outline', rotulo: 'Quantidade', valor: cabecas });
    }
    if (anuncio.pesoMedioKg) {
      ficha.push({ icone: 'scale-outline', rotulo: 'Peso médio', valor: `${anuncio.pesoMedioKg} kg` });
    }
    ficha.push(
      { icone: 'flag-outline', rotulo: 'Aptidão', valor: ROTULOS[anuncio.aptidao] },
      { icone: 'pricetag-outline', rotulo: 'Venda', valor: ROTULOS[anuncio.tipoVenda] }
    );
    return ficha;
  }

  // init="false" no template: os slides vêm de um *ngFor e só existem depois do detectChanges.
  private iniciarCarrossel(): void {
    const carrossel = this.carrossel?.nativeElement;
    if (!carrossel) {
      return;
    }
    Object.assign(carrossel, {
      pagination: { clickable: true },
      observer: true,
      observeSlideChildren: true,
    });
    carrossel.initialize();
  }

  private atualizar(mudanca: () => void): void {
    this.zone.run(() => {
      mudanca();
      this.cdr.detectChanges();
    });
  }

  private async comLoading(mensagem: string, acao: () => Promise<void>): Promise<void> {
    this.enviandoOtp = true;
    const loading = await this.loadingCtrl.create({
      message: mensagem,
      spinner: 'crescent',
      backdropDismiss: false,
    });
    await loading.present();
    try {
      await acao();
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : 0;
      const mensagens: Record<number, string> = {
        400: 'Código ou WhatsApp não confere com este anúncio.',
        404: 'Não encontramos este anúncio.',
        409: 'Este anúncio já foi reivindicado.',
        410: 'Este código expirou ou foi bloqueado. Peça um novo.',
        429: 'Você pediu códigos demais. Aguarde um pouco e tente de novo.',
      };
      await this.avisar(
        mensagens[status] ?? 'Não foi possível concluir agora. Tente de novo.',
        status === 429 ? 'warning' : 'danger'
      );
    } finally {
      this.enviandoOtp = false;
      await loading.dismiss();
      this.cdr.detectChanges();
    }
  }

  private async avisar(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      color,
      duration: 4000,
      position: 'top',
      buttons: [{ text: 'OK', role: 'cancel' }],
    });
    await toast.present();
  }
}
