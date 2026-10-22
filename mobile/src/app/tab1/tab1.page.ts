import { ChangeDetectorRef, Component, NgZone } from '@angular/core';
import { Router } from '@angular/router';
import { RefresherCustomEvent } from '@ionic/angular/lazy';
import { Observable } from 'rxjs';
import { Anuncio, CategoriaAnimal, CategoriaEstoque, ROTULOS } from '../models/anuncio';
import { AnuncioService } from '../services/anuncio.service';
import { pedidoNaoSaiu, registrarFalhaHttp } from '../services/conexao.service';
import { AtalhoBuscaService } from '../services/atalho-busca.service';
import { TemaService } from '../services/tema.service';

const CACHE_VITRINE = 'compregado-vitrine';

interface VitrineSalva {
  salvaEm: number;
  anuncios: Anuncio[];
  categorias: CategoriaEstoque[];
}

type FalhaVitrine = 'sem-conexao' | 'servidor';

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
  falha: FalhaVitrine | null = null;
  /** Preenchido quando a vitrine na tela veio do aparelho, e não da internet. */
  salvaEm: Date | null = null;
  readonly rotulos = ROTULOS;
  readonly esqueletos = [1, 2, 3, 4];
  readonly modoSol$: Observable<boolean>;

  constructor(
    private readonly anunciosApi: AnuncioService,
    private readonly atalhoBusca: AtalhoBuscaService,
    readonly tema: TemaService,
    private readonly router: Router,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.modoSol$ = tema.modoSol$;
    // Sinal fraco no campo: mostra na hora a última vitrine baixada e atualiza por baixo.
    const salva = this.lerCache();
    if (salva) {
      this.anuncios = salva.anuncios;
      this.categorias = salva.categorias;
      this.salvaEm = new Date(salva.salvaEm);
    }
  }

  ionViewWillEnter(): void {
    // O card tocado antes de abrir o detalhe continua focado (contorno de seleção) ao voltar.
    (document.activeElement as HTMLElement | null)?.blur();
    void this.carregar();
  }

  /**
   * Só a primeira carga troca o feed pelo esqueleto. Ao voltar para a aba, atualiza por baixo:
   * esconder a grade aqui jogaria a rolagem para o topo e recriaria todos os cards.
   */
  async carregar(): Promise<void> {
    this.atualizar(() => {
      this.carregando = true;
      this.falha = null;
    });
    try {
      const [anuncios, categorias] = await Promise.all([
        this.anunciosApi.listar(),
        this.anunciosApi.categorias(),
      ]);
      this.atualizar(() => {
        this.anuncios = anuncios;
        this.categorias = categorias;
        this.salvaEm = null;
        this.carregando = false;
      });
      this.salvarCache(anuncios, categorias);
    } catch (erro) {
      registrarFalhaHttp('vitrine', erro);
      const semConexao = pedidoNaoSaiu(erro);
      this.atualizar(() => {
        this.falha = semConexao ? 'sem-conexao' : 'servidor';
        this.carregando = false;
      });
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

  filtrarCategoria(categoria: CategoriaAnimal): void {
    this.atalhoBusca.pedirCategoria(categoria);
    void this.router.navigate(['/tabs/tab2']);
  }

  venderGado(): void {
    void this.router.navigate(['/tabs/publicar']);
  }

  private lerCache(): VitrineSalva | null {
    try {
      const salva = JSON.parse(localStorage.getItem(CACHE_VITRINE) ?? 'null') as VitrineSalva | null;
      return salva?.anuncios?.length ? salva : null;
    } catch {
      return null;
    }
  }

  private salvarCache(anuncios: Anuncio[], categorias: CategoriaEstoque[]): void {
    try {
      const salva: VitrineSalva = { salvaEm: Date.now(), anuncios: anuncios.slice(0, 60), categorias };
      localStorage.setItem(CACHE_VITRINE, JSON.stringify(salva));
    } catch {
      // Armazenamento cheio: a vitrine continua funcionando, só não fica salva para o modo sem internet.
    }
  }

  private atualizar(mudanca: () => void): void {
    this.zone.run(() => {
      mudanca();
      this.cdr.detectChanges();
    });
  }
}
