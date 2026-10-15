import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, NgZone, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ToastController } from '@ionic/angular/lazy';
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
import { SessaoService } from '../services/sessao.service';

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
  readonly fotoPlaceholder = FOTO_PLACEHOLDER_GADO;
  readonly rotulos = ROTULOS;

  private id = 0;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly anunciosApi: AnuncioService,
    private readonly sessao: SessaoService,
    private readonly toastCtrl: ToastController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ionViewWillEnter(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    void this.carregar();
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
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : -1;
      this.atualizar(() => {
        this.carregando = false;
        this.falha = status === 404 ? 'nao-encontrado' : status === 0 || !navigator.onLine ? 'sem-conexao' : 'servidor';
      });
    }
  }

  async falarComVendedor(): Promise<void> {
    if (!this.anuncio || this.abrindoContato) {
      return;
    }
    const ok = await this.sessao.exigirLogin();
    if (!ok) {
      return;
    }
    this.atualizar(() => (this.abrindoContato = true));
    try {
      const contato = await this.anunciosApi.contato(this.anuncio.id);
      window.open(contato.urlWhatsapp, '_blank');
    } catch (erro) {
      const status = erro instanceof HttpErrorResponse ? erro.status : -1;
      const mensagem =
        status === 409
          ? 'Este anúncio não está mais disponível.'
          : status === 0 || !navigator.onLine
            ? 'Sem internet. Tente de novo quando tiver sinal.'
            : 'Não foi possível abrir o WhatsApp do vendedor. Tente de novo.';
      await this.avisar(mensagem, status === 409 ? 'warning' : 'danger');
    } finally {
      this.atualizar(() => (this.abrindoContato = false));
    }
  }

  /** Compartilhar com sócio ou comprador é comum no gado; sem Web Share (WebView Android), vai pelo WhatsApp. */
  async compartilhar(): Promise<void> {
    if (!this.anuncio) {
      return;
    }
    const url = `https://appcompregado.com.br/tabs/tab1/anuncio/${this.anuncio.id}`;
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
