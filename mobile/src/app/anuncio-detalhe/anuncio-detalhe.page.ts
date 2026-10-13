import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, NgZone, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ToastController } from '@ionic/angular/lazy';
import type { SwiperContainer } from 'swiper/element';
import { Anuncio, FOTO_PLACEHOLDER_GADO, ROTULOS, formatarPreco } from '../models/anuncio';
import { AnuncioService } from '../services/anuncio.service';
import { SessaoService } from '../services/sessao.service';

@Component({
  selector: 'app-anuncio-detalhe',
  templateUrl: 'anuncio-detalhe.page.html',
  styleUrls: ['anuncio-detalhe.page.scss'],
  standalone: false,
})
export class AnuncioDetalhePage {
  @ViewChild('carrossel') carrossel?: ElementRef<SwiperContainer>;

  anuncio: Anuncio | null = null;
  carregando = false;
  readonly fotoPlaceholder = FOTO_PLACEHOLDER_GADO;
  readonly rotulos = ROTULOS;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly anunciosApi: AnuncioService,
    private readonly sessao: SessaoService,
    private readonly toastCtrl: ToastController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ionViewWillEnter(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    void this.carregar(id);
  }

  async carregar(id: number): Promise<void> {
    this.carregando = true;
    this.cdr.detectChanges();
    try {
      const anuncio = await this.anunciosApi.buscar(id);
      this.zone.run(() => {
        this.anuncio = anuncio;
        this.carregando = false;
        this.cdr.detectChanges();
      });
      this.iniciarCarrossel();
    } catch {
      this.zone.run(() => {
        this.carregando = false;
        this.cdr.detectChanges();
      });
      const toast = await this.toastCtrl.create({
        message: 'Anúncio não encontrado.',
        duration: 2500,
        color: 'warning',
      });
      await toast.present();
    }
  }

  async falarComVendedor(): Promise<void> {
    if (!this.anuncio) {
      return;
    }
    const ok = await this.sessao.exigirLogin();
    if (!ok) {
      return;
    }
    try {
      const contato = await this.anunciosApi.contato(this.anuncio.id);
      window.open(contato.urlWhatsapp, '_blank');
    } catch (erro) {
      const vendido = erro instanceof HttpErrorResponse && erro.status === 409;
      const toast = await this.toastCtrl.create({
        message: vendido ? 'Este animal já foi vendido.' : 'Não foi possível abrir o WhatsApp do vendedor.',
        duration: 2500,
        color: 'danger',
      });
      await toast.present();
    }
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

  usarPlaceholder(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (!img.src.endsWith(FOTO_PLACEHOLDER_GADO)) {
      img.src = FOTO_PLACEHOLDER_GADO;
    }
  }

  precoLabel(anuncio: Anuncio): string {
    return formatarPreco(anuncio);
  }
}
