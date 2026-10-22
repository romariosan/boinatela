import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, NgZone } from '@angular/core';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular/lazy';
import { BehaviorSubject, Observable, Subject, combineLatest, filter, interval } from 'rxjs';
import { Anuncio, NovoAnuncio } from '../models/anuncio';
import { AnuncioService } from './anuncio.service';
import { AuthService } from './auth.service';
import { ConexaoService, pedidoNaoSaiu, registrarFalhaHttp } from './conexao.service';
import { PlanoProService, limiteDeAnunciosAtingido } from './plano-pro.service';

const BANCO = 'compregado';
const LOJA = 'fila-publicacao';
const NOVA_TENTATIVA_MS = 60_000;

interface FotoGuardada {
  nome: string;
  tipo: string;
  blob: Blob;
}

interface PublicacaoGuardada {
  id: string;
  usuarioId: number;
  dados: NovoAnuncio;
  fotos: FotoGuardada[];
  criadoEm: number;
  /**
   * A conexão caiu no meio do envio: o servidor pode ter gravado e só a resposta se perdido.
   * Antes de reenviar, confere se o anúncio já está em "meus anúncios".
   */
  envioInterrompido: boolean;
  /** Recusado pelo servidor: não tenta sozinho de novo, o vendedor decide. */
  erro?: string;
}

/** O que a tela de Conta mostra de cada anúncio esperando sinal. */
export interface PublicacaoPendente {
  id: string;
  dados: NovoAnuncio;
  criadoEm: number;
  quantidadeFotos: number;
  /** URL temporária da primeira foto. */
  capa: string | null;
  erro?: string;
  enviando: boolean;
}

/**
 * Fila de anúncios novos que não conseguiram sair por falta de sinal. Fica no IndexedDB (as fotos não
 * cabem no localStorage) e é enviada sozinha quando a internet volta, o usuário entra ou a cada minuto.
 */
@Injectable({ providedIn: 'root' })
export class FilaPublicacaoService {
  private readonly pendentes = new BehaviorSubject<PublicacaoPendente[]>([]);
  private readonly publicados = new Subject<Anuncio>();
  private banco: Promise<IDBDatabase> | null = null;
  private processando = false;
  private enviandoId: string | null = null;

  readonly pendentes$: Observable<PublicacaoPendente[]> = this.pendentes.asObservable();
  /** Anúncio da fila que acabou de ser publicado (para a lista de "meus anúncios" se atualizar). */
  readonly publicados$: Observable<Anuncio> = this.publicados.asObservable();

  constructor(
    private readonly anuncios: AnuncioService,
    private readonly auth: AuthService,
    private readonly conexao: ConexaoService,
    private readonly planoPro: PlanoProService,
    private readonly toastCtrl: ToastController,
    private readonly router: Router,
    private readonly zone: NgZone
  ) {}

  /** Chamado uma vez na abertura do app. */
  iniciar(): void {
    this.auth.currentUser$.subscribe(() => void this.atualizarLista());
    combineLatest([this.conexao.online$, this.auth.isLoggedIn$])
      .pipe(filter(([online, logado]) => online && logado))
      .subscribe(() => void this.processar());
    // Sinal fraco: o navegador diz "online", mas a requisição não passa. Tenta de novo de tempos em tempos.
    interval(NOVA_TENTATIVA_MS)
      .pipe(filter(() => this.pendentes.value.some(item => !item.erro)))
      .subscribe(() => void this.processar());
  }

  get disponivel(): boolean {
    return typeof indexedDB !== 'undefined';
  }

  async adicionar(dados: NovoAnuncio, fotos: File[], envioInterrompido: boolean): Promise<void> {
    const usuarioId = this.auth.snapshot?.usuarioId;
    if (usuarioId === undefined) {
      throw new Error('Sem usuário logado');
    }
    const item: PublicacaoGuardada = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      usuarioId,
      dados,
      fotos: fotos.map(foto => ({ nome: foto.name, tipo: foto.type, blob: foto })),
      criadoEm: Date.now(),
      envioInterrompido,
    };
    await this.gravar(item);
    await this.atualizarLista();
  }

  async descartar(id: string): Promise<void> {
    await this.apagar(id);
    await this.atualizarLista();
  }

  /** "Tentar de novo" de um item recusado: volta para a fila automática. */
  async tentarDeNovo(id: string): Promise<void> {
    const item = (await this.todos()).find(guardado => guardado.id === id);
    if (item) {
      delete item.erro;
      await this.gravar(item);
      await this.atualizarLista();
    }
    await this.processar();
  }

  async processar(): Promise<void> {
    const usuario = this.auth.snapshot;
    if (this.processando || !usuario?.token || !this.disponivel) {
      return;
    }
    this.processando = true;
    try {
      const itens = (await this.todos()).filter(item => !item.erro && item.usuarioId === usuario.usuarioId);
      for (const item of itens) {
        this.enviandoId = item.id;
        await this.atualizarLista();
        const continuar = await this.enviar(item);
        if (!continuar) {
          break;
        }
      }
    } catch {
      // IndexedDB indisponível ou corrompido: nada a enviar.
    } finally {
      this.processando = false;
      this.enviandoId = null;
      await this.atualizarLista();
    }
  }

  /** @returns false quando não adianta seguir com os próximos (sem sinal, sessão expirada). */
  private async enviar(item: PublicacaoGuardada): Promise<boolean> {
    try {
      if (item.envioInterrompido) {
        const jaPublicado = await this.procurarJaPublicado(item.dados);
        if (jaPublicado) {
          await this.apagar(item.id);
          this.zone.run(() => this.publicados.next(jaPublicado));
          await this.avisarPublicado(jaPublicado);
          return true;
        }
      }
      const arquivos = item.fotos.map(foto => new File([foto.blob], foto.nome, { type: foto.tipo }));
      const criado = await this.anuncios.criar(item.dados, arquivos);
      await this.apagar(item.id);
      this.zone.run(() => this.publicados.next(criado));
      await this.avisarPublicado(criado);
      return true;
    } catch (erro) {
      registrarFalhaHttp('fila-publicacao', erro);
      if (limiteDeAnunciosAtingido(erro)) {
        // Fica guardado sem nova tentativa automática: o vendedor decide (virar PRO ou vender um lote).
        item.erro = 'Limite de 3 anúncios grátis atingido. Marque um anúncio como vendido ou vire Vendedor PRO e toque em tentar de novo.';
        await this.gravar(item);
        void this.planoPro.exibirPaywallPro();
        return true;
      }
      const status = erro instanceof HttpErrorResponse ? erro.status : -1;
      if (pedidoNaoSaiu(erro)) {
        item.envioInterrompido = true;
        await this.gravar(item);
        return false;
      }
      if (status === 401 || status === 403 || status === 428 || status >= 500 || status === -1) {
        // Precisa entrar de novo, confirmar o WhatsApp ou esperar o servidor: continua na fila.
        return false;
      }
      item.erro =
        status === 413
          ? 'As fotos ficaram grandes demais. Publique de novo com menos fotos.'
          : 'O servidor recusou os dados deste anúncio. Publique de novo conferindo os campos.';
      await this.gravar(item);
      return true;
    }
  }

  private async procurarJaPublicado(dados: NovoAnuncio): Promise<Anuncio | null> {
    const meus = await this.anuncios.meus();
    return (
      meus.find(
        anuncio =>
          anuncio.titulo === dados.titulo &&
          anuncio.preco === dados.preco &&
          anuncio.cidade === dados.cidade &&
          anuncio.categoriaAnimal === dados.categoriaAnimal &&
          anuncio.tipoVenda === dados.tipoVenda
      ) ?? null
    );
  }

  private async avisarPublicado(anuncio: Anuncio): Promise<void> {
    const toast = await this.toastCtrl.create({
      message: `Sinal voltou: "${anuncio.titulo}" foi publicado na vitrine.`,
      color: 'success',
      position: 'top',
      duration: 6000,
      buttons: [
        {
          text: 'Ver',
          handler: () => this.zone.run(() => void this.router.navigate(['/tabs/tab3/anuncio', anuncio.id])),
        },
      ],
    });
    await toast.present();
  }

  private async atualizarLista(): Promise<void> {
    let itens: PublicacaoGuardada[] = [];
    try {
      itens = this.disponivel ? await this.todos() : [];
    } catch {
      itens = [];
    }
    const usuarioId = this.auth.snapshot?.usuarioId;
    this.pendentes.value.forEach(item => item.capa && URL.revokeObjectURL(item.capa));
    const visiveis = itens
      .filter(item => item.usuarioId === usuarioId)
      .sort((a, b) => a.criadoEm - b.criadoEm)
      .map<PublicacaoPendente>(item => ({
        id: item.id,
        dados: item.dados,
        criadoEm: item.criadoEm,
        quantidadeFotos: item.fotos.length,
        capa: item.fotos[0] ? URL.createObjectURL(item.fotos[0].blob) : null,
        erro: item.erro,
        enviando: item.id === this.enviandoId,
      }));
    this.zone.run(() => this.pendentes.next(visiveis));
  }

  private abrir(): Promise<IDBDatabase> {
    this.banco ??= new Promise<IDBDatabase>((resolve, reject) => {
      const pedido = indexedDB.open(BANCO, 1);
      pedido.onupgradeneeded = () => pedido.result.createObjectStore(LOJA, { keyPath: 'id' });
      pedido.onsuccess = () => resolve(pedido.result);
      pedido.onerror = () => {
        this.banco = null;
        reject(pedido.error);
      };
    });
    return this.banco;
  }

  private async operar<T>(modo: IDBTransactionMode, operacao: (loja: IDBObjectStore) => IDBRequest<T>): Promise<T> {
    const banco = await this.abrir();
    return new Promise<T>((resolve, reject) => {
      const transacao = banco.transaction(LOJA, modo);
      const pedido = operacao(transacao.objectStore(LOJA));
      transacao.oncomplete = () => resolve(pedido.result);
      transacao.onerror = () => reject(transacao.error);
      transacao.onabort = () => reject(transacao.error);
    });
  }

  private todos(): Promise<PublicacaoGuardada[]> {
    return this.operar('readonly', loja => loja.getAll() as IDBRequest<PublicacaoGuardada[]>);
  }

  private async gravar(item: PublicacaoGuardada): Promise<void> {
    await this.operar('readwrite', loja => loja.put(item));
  }

  private async apagar(id: string): Promise<void> {
    await this.operar('readwrite', loja => loja.delete(id));
  }
}
