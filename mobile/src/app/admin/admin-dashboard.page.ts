import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, NgZone, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular/lazy';
import { AdminAnuncio, AdminAnuncioEditar, AdminAnuncioService } from '../services/admin-anuncio.service';
import { AuthService } from '../services/auth.service';
import { registrarFalhaHttp } from '../services/conexao.service';

type Aba = 'moderacao' | 'prospeccao';

interface EtapaFunil {
  id: 'CAPTADO' | 'PROCESSADO' | 'AGUARDANDO_REIVINDICACAO' | 'PUBLICADO';
  curto: string;
  rotulo: string;
}

interface FormEdicao {
  id: number;
  titulo: string;
  descricao: string;
  preco: number | null;
  cidade: string;
  quantidade: number | null;
  categoriaAnimal: string;
  aptidao: string;
  raca: string;
}

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: 'admin-dashboard.page.html',
  styleUrls: ['admin-dashboard.page.scss'],
  standalone: false,
})
export class AdminDashboardPage implements OnInit {
  aba: Aba = 'moderacao';
  carregando = false;
  pendentes: AdminAnuncio[] = [];
  prospeccao: AdminAnuncio[] = [];
  usandoAmostra = false;
  editando = false;
  salvandoEdicao = false;
  form: FormEdicao | null = null;

  readonly categorias = ['BEZERRO', 'GARROTE', 'VACA', 'TOURO'];
  readonly aptidoes = ['CORTE', 'LEITE', 'REPRODUCAO'];
  readonly racas = [
    'NELORE', 'ANGUS', 'GIROLANDO', 'BRAHMAN', 'SENEPOL', 'BRANGUS',
    'GUZERA', 'TABAPUA', 'GIR', 'HOLANDES', 'CARACU', 'CANCHIM', 'MISTO', 'OUTRA',
  ];

  readonly etapasFunil: EtapaFunil[] = [
    { id: 'CAPTADO', curto: '1', rotulo: 'Captado' },
    { id: 'PROCESSADO', curto: '2', rotulo: 'Processado' },
    { id: 'AGUARDANDO_REIVINDICACAO', curto: '3', rotulo: 'Aguard. reivindicação' },
    { id: 'PUBLICADO', curto: '4', rotulo: 'Publicado' },
  ];

  /** Amostra só quando a fila real está vazia — cidades dos cenários de QA. */
  private readonly amostraModeracao: AdminAnuncio[] = [
    {
      id: -1,
      titulo: 'Bezerro em Planaltina',
      precoFormatado: 'R$ 5.000',
      cidade: 'Planaltina - DF',
      urlFotoPrincipal: 'assets/icon/favicon.png',
      statusPublicacao: 'PENDENTE',
      statusContato: 'NAO_CONTATADO',
      statusProcessamento: 'PUBLICADO',
      mensagemWhatsapp: '',
    },
    {
      id: -2,
      titulo: '10 vacas — Cabeceiras',
      precoFormatado: 'R$ 12.000',
      cidade: 'Cabeceiras - GO',
      urlFotoPrincipal: 'assets/icon/favicon.png',
      statusPublicacao: 'PENDENTE',
      statusContato: 'NAO_CONTATADO',
      statusProcessamento: 'PUBLICADO',
      mensagemWhatsapp: '',
    },
    {
      id: -3,
      titulo: 'Lote de nelore',
      precoFormatado: 'Preço a Combinar',
      cidade: 'Local a combinar',
      urlFotoPrincipal: 'assets/icon/favicon.png',
      statusPublicacao: 'PENDENTE',
      statusContato: 'NAO_CONTATADO',
      statusProcessamento: 'PUBLICADO',
      mensagemWhatsapp: '',
    },
  ];

  private readonly amostraProspeccao: AdminAnuncio[] = [
    {
      id: -4,
      titulo: 'Vacas aprovadas — Guará',
      precoFormatado: 'R$ 8.000',
      cidade: 'Guará - DF',
      urlFotoPrincipal: 'assets/icon/favicon.png',
      telefoneVendedor: '61999990001',
      vendedorNome: 'Produtor demo',
      statusPublicacao: 'APROVADO',
      statusContato: 'NAO_CONTATADO',
      statusProcessamento: 'PUBLICADO',
      mensagemWhatsapp: 'Olá! Aqui é da equipe do CompreGado.',
    },
  ];

  constructor(
    private readonly admin: AdminAnuncioService,
    private readonly auth: AuthService,
    private readonly toast: ToastController,
    private readonly router: Router,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  get listaModeracao(): AdminAnuncio[] {
    return this.pendentes.length ? this.pendentes : this.usandoAmostra ? this.amostraModeracao : [];
  }

  get listaProspeccao(): AdminAnuncio[] {
    return this.prospeccao.length ? this.prospeccao : this.usandoAmostra ? this.amostraProspeccao : [];
  }

  ngOnInit(): void {
    void this.carregar();
  }

  async atualizarPuxando(ev: CustomEvent): Promise<void> {
    try {
      await this.carregar();
    } finally {
      (ev.target as HTMLIonRefresherElement).complete();
    }
  }

  async trocarAba(ev: CustomEvent): Promise<void> {
    this.aba = ev.detail.value as Aba;
    await this.carregar();
  }

  async carregar(): Promise<void> {
    this.atualizar(() => {
      this.carregando = true;
      this.usandoAmostra = false;
    });
    try {
      if (this.aba === 'moderacao') {
        const pendentes = await this.admin.listarPendentes();
        this.atualizar(() => {
          this.pendentes = pendentes;
          this.usandoAmostra = pendentes.length === 0;
        });
      } else {
        const prospeccao = await this.admin.listarProspeccao();
        this.atualizar(() => {
          this.prospeccao = prospeccao;
          this.usandoAmostra = prospeccao.length === 0;
        });
      }
    } catch (erro) {
      registrarFalhaHttp('admin-carregar', erro);
      await this.avisar(this.mensagemErro(erro, 'Não foi possível carregar a fila.'));
      if (erro instanceof HttpErrorResponse && (erro.status === 401 || erro.status === 400)) {
        if (!this.auth.snapshot?.admin) {
          void this.router.navigateByUrl('/tabs/tab1');
        }
      }
    } finally {
      this.atualizar(() => {
        this.carregando = false;
      });
    }
  }

  /** CAPTADO → PROCESSADO → AGUARDANDO_REIVINDICACAO → PUBLICADO */
  etapaFunil(item: AdminAnuncio): EtapaFunil['id'] {
    if (item.statusPublicacao === 'APROVADO' && this.aptoVitrine(item)) {
      return item.statusContato === 'NAO_CONTATADO' ? 'AGUARDANDO_REIVINDICACAO' : 'PUBLICADO';
    }
    if (item.statusPublicacao === 'APROVADO') {
      return 'PROCESSADO';
    }
    if (item.statusProcessamento === 'PUBLICADO' || (item.urlFotoPrincipal && item.precoFormatado)) {
      return 'PROCESSADO';
    }
    return 'CAPTADO';
  }

  /** Foto obrigatória. Cidade legada SEM_CIDADE / vazia vira "Local a combinar" no approve. */
  aptoVitrine(item: AdminAnuncio): boolean {
    if (typeof item.aptoVitrine === 'boolean') {
      return item.aptoVitrine || this.podeAprovarComFallback(item);
    }
    return this.podeAprovarComFallback(item);
  }

  /** Dá para aprovar se tem foto (backend normaliza cidade). */
  private podeAprovarComFallback(item: AdminAnuncio): boolean {
    return !!(item.urlFotoPrincipal ?? '').trim();
  }

  avisoVitrine(item: AdminAnuncio): string | null {
    if (!this.podeAprovarComFallback(item)) {
      return 'Sem foto — não entra na vitrine.';
    }
    const cidade = (item.cidade ?? '').trim().toLowerCase();
    if (
      !cidade
      || cidade === 'sem_cidade'
      || cidade.startsWith('sem_cidade -')
      || cidade.startsWith('a confirmar')
    ) {
      return 'Sem cidade real — ao aprovar sai como “Local a combinar”.';
    }
    return null;
  }

  rotuloFunil(item: AdminAnuncio): string {
    const id = this.etapaFunil(item);
    return this.etapasFunil.find(e => e.id === id)?.rotulo ?? id;
  }

  etapaAtiva(item: AdminAnuncio, etapa: EtapaFunil): boolean {
    const ordem = this.etapasFunil.map(e => e.id);
    return ordem.indexOf(etapa.id) <= ordem.indexOf(this.etapaFunil(item));
  }

  /** Fetch no web sai fora do NgZone; sem isso a tela fica em "Carregando…". */
  private atualizar(mudanca: () => void): void {
    this.zone.run(() => {
      mudanca();
      this.cdr.detectChanges();
    });
  }

  async aprovar(item: AdminAnuncio): Promise<void> {
    if (item.id < 0) {
      return;
    }
    if (!this.podeAprovarComFallback(item)) {
      await this.avisar(this.avisoVitrine(item) ?? 'Não dá para aprovar sem foto.');
      return;
    }
    try {
      await this.admin.aprovar(item.id);
      this.atualizar(() => {
        this.pendentes = this.pendentes.filter(a => a.id !== item.id);
        this.usandoAmostra = this.pendentes.length === 0;
      });
      const avisoCidade = this.avisoVitrine(item);
      await this.avisar(
        avisoCidade
          ? 'Anúncio aprovado com Local a combinar. Entrou na vitrine e na prospecção.'
          : 'Anúncio aprovado. Entrou na vitrine e na prospecção.',
      );
    } catch (erro) {
      registrarFalhaHttp('admin-aprovar', erro);
      await this.avisar(this.mensagemErro(erro, 'Não foi possível aprovar.'));
    }
  }

  async rejeitar(item: AdminAnuncio): Promise<void> {
    if (item.id < 0) {
      return;
    }
    try {
      await this.admin.rejeitar(item.id);
      this.atualizar(() => {
        this.pendentes = this.pendentes.filter(a => a.id !== item.id);
        this.usandoAmostra = this.pendentes.length === 0;
      });
      await this.avisar('Anúncio rejeitado.');
    } catch (erro) {
      registrarFalhaHttp('admin-rejeitar', erro);
      await this.avisar(this.mensagemErro(erro, 'Não foi possível rejeitar.'));
    }
  }

  chamarWhatsapp(item: AdminAnuncio): void {
    const url = this.admin.urlWhatsapp(item);
    if (!url) {
      void this.avisar('Este lote não tem telefone do vendedor.');
      return;
    }
    window.open(url, '_blank', 'noopener');
  }

  async marcarContatado(item: AdminAnuncio): Promise<void> {
    if (item.id < 0) {
      return;
    }
    try {
      await this.admin.marcarContatado(item.id);
      this.atualizar(() => {
        this.prospeccao = this.prospeccao.filter(a => a.id !== item.id);
        this.usandoAmostra = this.prospeccao.length === 0;
      });
      await this.avisar('Marcado como contatado.');
    } catch (erro) {
      registrarFalhaHttp('admin-contatado', erro);
      await this.avisar(this.mensagemErro(erro, 'Não foi possível marcar.'));
    }
  }

  abrirEdicao(item: AdminAnuncio): void {
    if (item.id < 0) {
      void this.avisar('Amostra visual — sem edição.');
      return;
    }
    this.atualizar(() => {
      this.form = {
        id: item.id,
        titulo: item.titulo ?? '',
        descricao: item.descricao ?? '',
        preco: item.preco ?? null,
        cidade: item.cidade ?? '',
        quantidade: item.quantidade ?? null,
        categoriaAnimal: item.categoriaAnimal ?? '',
        aptidao: item.aptidao ?? '',
        raca: item.raca ?? '',
      };
      this.editando = true;
    });
  }

  fecharEdicao(): void {
    this.atualizar(() => {
      this.editando = false;
      this.form = null;
      this.salvandoEdicao = false;
    });
  }

  async salvarEdicao(): Promise<void> {
    if (!this.form || this.form.id < 0) {
      return;
    }
    const titulo = (this.form.titulo ?? '').trim();
    if (!titulo) {
      await this.avisar('Informe o título.');
      return;
    }
    const body: AdminAnuncioEditar = {
      titulo,
      descricao: this.form.descricao ?? '',
      preco: this.form.preco ?? 0,
      cidade: (this.form.cidade ?? '').trim() || 'Local a combinar',
      quantidade: this.form.quantidade,
      categoriaAnimal: this.form.categoriaAnimal || null,
      aptidao: this.form.aptidao || null,
      raca: this.form.raca || null,
    };
    this.atualizar(() => {
      this.salvandoEdicao = true;
    });
    try {
      const atualizado = await this.admin.editar(this.form.id, body);
      this.atualizar(() => {
        this.pendentes = this.pendentes.map(a => (a.id === atualizado.id ? atualizado : a));
        this.prospeccao = this.prospeccao.map(a => (a.id === atualizado.id ? atualizado : a));
        this.editando = false;
        this.form = null;
      });
      await this.avisar('Anúncio atualizado.');
    } catch (erro) {
      registrarFalhaHttp('admin-editar', erro);
      await this.avisar(this.mensagemErro(erro, 'Não foi possível salvar.'));
    } finally {
      this.atualizar(() => {
        this.salvandoEdicao = false;
      });
    }
  }

  private mensagemErro(erro: unknown, padrao: string): string {
    if (erro instanceof HttpErrorResponse) {
      const corpo = erro.error;
      if (typeof corpo === 'string' && corpo.trim()) {
        return corpo;
      }
      if (corpo && typeof corpo === 'object' && typeof (corpo as { message?: string }).message === 'string') {
        return (corpo as { message: string }).message;
      }
    }
    return padrao;
  }

  private async avisar(mensagem: string): Promise<void> {
    const t = await this.toast.create({ message: mensagem, duration: 2800, position: 'bottom' });
    await t.present();
  }
}
