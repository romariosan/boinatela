import { ChangeDetectorRef, Component, NgZone } from '@angular/core';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular/lazy';
import { Anuncio, CategoriaAnimal, CategoriaEstoque, ROTULOS } from '../models/anuncio';
import { UFS_REGIAO, UF_PADRAO, separarCidade } from '../models/localidade';
import { AnuncioService } from '../services/anuncio.service';
import { AtalhoBuscaService } from '../services/atalho-busca.service';
import { SeletorCidadeService } from '../shared/seletor-cidade/seletor-cidade.service';

@Component({
  selector: 'app-tab2',
  templateUrl: 'tab2.page.html',
  styleUrls: ['tab2.page.scss'],
  standalone: false,
})
export class Tab2Page {
  termo = '';
  categoria: CategoriaAnimal | '' = '';
  uf = UF_PADRAO;
  cidade = '';
  categorias: CategoriaEstoque[] = [];
  cidades: string[] = [];
  anuncios: Anuncio[] = [];
  carregando = false;
  buscou = false;
  readonly rotulos = ROTULOS;
  /** DF e GO; "Todas as UFs" fica logo abaixo delas, antes das demais. */
  readonly ufsDaRegiao = UFS_REGIAO.slice(0, 2);
  readonly outrasUfs = UFS_REGIAO.slice(2);

  private ultimaBusca = 0;

  constructor(
    private readonly anunciosApi: AnuncioService,
    private readonly atalhoBusca: AtalhoBuscaService,
    private readonly seletorCidade: SeletorCidadeService,
    private readonly router: Router,
    private readonly toastCtrl: ToastController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ionViewWillEnter(): void {
    const categoria = this.atalhoBusca.consumirCategoria();
    if (categoria) {
      this.termo = '';
      this.categoria = categoria;
    }
    void this.carregarFiltros();
    void this.buscar();
  }

  get temFiltros(): boolean {
    return !!this.termo.trim() || !!this.categoria || !!this.cidade || this.uf !== UF_PADRAO;
  }

  /** O termo fica cru: aparar aqui apagaria o espaço que o usuário acabou de digitar no searchbar. */
  aoDigitar(event: Event): void {
    this.termo = (event as CustomEvent).detail.value ?? '';
    void this.buscar();
  }

  limparFiltros(): void {
    this.termo = '';
    this.categoria = '';
    this.uf = UF_PADRAO;
    this.cidade = '';
    void this.buscar();
  }

  porId(_indice: number, anuncio: Anuncio): number {
    return anuncio.id;
  }

  aoMudarCategoria(event: Event): void {
    this.categoria = (event as CustomEvent).detail.value ?? '';
    void this.buscar();
  }

  aoMudarUf(event: Event): void {
    this.uf = (event as CustomEvent).detail.value ?? '';
    this.cidade = '';
    void this.buscar();
  }

  async escolherCidade(): Promise<void> {
    const uf = this.uf;
    const cidade = await this.seletorCidade.escolher({
      titulo: uf ? `Cidades com anúncios (${uf})` : 'Cidades com anúncios',
      carregar: async () => this.cidades.filter(item => !uf || separarCidade(item).uf === uf),
      selecionada: this.cidade,
      opcaoTodas: 'Qualquer cidade',
      exibir: item => this.nomeCidade(item),
    });
    if (cidade === null) {
      return;
    }
    this.atualizar(() => (this.cidade = cidade));
    void this.buscar();
  }

  /** Com UF escolhida, mostra só o nome; em "Todas UF" mantém o sufixo para diferenciar homônimas. */
  nomeCidade(cidade: string): string {
    return this.uf ? separarCidade(cidade).nome : cidade;
  }

  verDetalhes(id: number): void {
    void this.router.navigate(['/tabs/tab2/anuncio', id]);
  }

  private async carregarFiltros(): Promise<void> {
    try {
      const [categorias, cidades] = await Promise.all([
        this.anunciosApi.categorias(),
        this.anunciosApi.cidades(),
      ]);
      this.atualizar(() => {
        this.categorias = categorias;
        this.cidades = cidades;
      });
    } catch {
      // Sem filtros a busca por texto continua funcionando; o erro aparece em buscar().
    }
  }

  private async buscar(): Promise<void> {
    // Respostas fora de ordem (digitação rápida) não podem sobrescrever a busca mais recente.
    const busca = ++this.ultimaBusca;
    this.atualizar(() => (this.carregando = true));
    try {
      const anuncios = await this.anunciosApi.listar({
        q: this.termo.trim(),
        cidade: this.cidade,
        uf: this.uf,
        categoria: this.categoria,
      });
      if (busca !== this.ultimaBusca) {
        return;
      }
      this.atualizar(() => {
        this.anuncios = anuncios;
        this.carregando = false;
        this.buscou = true;
      });
    } catch {
      if (busca !== this.ultimaBusca) {
        return;
      }
      this.atualizar(() => (this.carregando = false));
      const toast = await this.toastCtrl.create({
        message: 'Busca indisponível. Confira sua conexão.',
        duration: 2500,
        color: 'warning',
      });
      await toast.present();
    }
  }

  private atualizar(mudanca: () => void): void {
    this.zone.run(() => {
      mudanca();
      this.cdr.detectChanges();
    });
  }
}
