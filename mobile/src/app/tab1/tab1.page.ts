import { ChangeDetectorRef, Component, NgZone } from '@angular/core';
import { Router } from '@angular/router';
import { RefresherCustomEvent, ToastController } from '@ionic/angular/lazy';
import { Anuncio, CategoriaEstoque, ROTULOS } from '../models/anuncio';
import { AnuncioService } from '../services/anuncio.service';

@Component({
  selector: 'app-tab1',
  templateUrl: 'tab1.page.html',
  styleUrls: ['tab1.page.scss'],
  standalone: false,
})
export class Tab1Page {
  anuncios: Anuncio[] = [];
  categorias: CategoriaEstoque[] = [];
  carregando = false;
  readonly rotulos = ROTULOS;

  constructor(
    private readonly anunciosApi: AnuncioService,
    private readonly router: Router,
    private readonly toastCtrl: ToastController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ionViewWillEnter(): void {
    // O card tocado antes de abrir o detalhe continua focado (contorno de seleção) ao voltar.
    (document.activeElement as HTMLElement | null)?.blur();
    void this.carregar();
  }

  /**
   * Só a primeira carga troca o feed pelo spinner. Ao voltar para a aba, atualiza por baixo:
   * esconder a grade aqui jogaria a rolagem para o topo e recriaria todos os cards.
   */
  async carregar(): Promise<void> {
    const primeiraCarga = !this.anuncios.length;
    if (primeiraCarga) {
      this.carregando = true;
      this.cdr.detectChanges();
    }
    try {
      const [anuncios, categorias] = await Promise.all([
        this.anunciosApi.listar(),
        this.anunciosApi.categorias(),
      ]);
      this.zone.run(() => {
        this.anuncios = anuncios;
        this.categorias = categorias;
        this.carregando = false;
        this.cdr.detectChanges();
      });
    } catch {
      this.zone.run(() => {
        this.carregando = false;
        this.cdr.detectChanges();
      });
      const toast = await this.toastCtrl.create({
        message: 'Não foi possível carregar a vitrine. Suba a API em :8080.',
        duration: 2800,
        color: 'warning',
      });
      await toast.present();
    }
  }

  async atualizarPuxando(event: Event): Promise<void> {
    await this.carregar();
    await (event as RefresherCustomEvent).target.complete();
  }

  porId(_indice: number, anuncio: Anuncio): number {
    return anuncio.id;
  }

  verDetalhes(id: number): void {
    void this.router.navigate(['/tabs/tab1/anuncio', id]);
  }
}
