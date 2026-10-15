import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, NgZone, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IonContent, NavController, ToastController } from '@ionic/angular/lazy';
import { Subscription, debounceTime } from 'rxjs';
import {
  APTIDOES,
  Aptidao,
  CATEGORIAS_ANIMAL,
  CategoriaAnimal,
  MAXIMO_FOTOS,
  NovoAnuncio,
  RACAS,
  ROTULOS,
  ROTULO_PRECO,
  Raca,
  TIPOS_VENDA,
  TipoVenda,
  formatarMoeda,
  lerMoeda,
  precoComplementar,
} from '../models/anuncio';
import { UFS_REGIAO, UF_PADRAO, formatarCidade, separarCidade } from '../models/localidade';
import { AnuncioService } from '../services/anuncio.service';
import { AuthService } from '../services/auth.service';
import { FilaPublicacaoService } from '../services/fila-publicacao.service';
import { LocalidadesService } from '../services/localidades.service';
import { PlanoProService, limiteDeAnunciosAtingido } from '../services/plano-pro.service';
import { SessaoService } from '../services/sessao.service';
import { VerificacaoService } from '../services/verificacao.service';
import { comprimirImagem } from '../shared/comprimir-imagem';
import { SeletorCidadeService } from '../shared/seletor-cidade/seletor-cidade.service';

const TIPOS_IMAGEM = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const TAMANHO_MAXIMO_BYTES = 10 * 1024 * 1024;
const CHAVE_RASCUNHO = 'compregado-rascunho-anuncio';

const PLURAIS: Record<CategoriaAnimal, string> = {
  BEZERRO: 'bezerros',
  GARROTE: 'garrotes',
  VACA: 'vacas',
  TOURO: 'touros',
};

/** Sem {@code arquivo} = foto que já está no anúncio (modo edição). */
interface FotoSelecionada {
  url: string;
  arquivo?: File;
}

type CampoDeUmToque = 'categoriaAnimal' | 'aptidao' | 'tipoVenda';

@Component({
  selector: 'app-publicar',
  templateUrl: 'publicar.page.html',
  styleUrls: ['publicar.page.scss'],
  standalone: false,
})
export class PublicarPage implements OnInit, OnDestroy {
  @ViewChild('seletorArquivo') seletorArquivo!: ElementRef<HTMLInputElement>;
  @ViewChild(IonContent) conteudo?: IonContent;

  readonly categorias = CATEGORIAS_ANIMAL;
  readonly racas = RACAS;
  readonly aptidoes = APTIDOES;
  readonly tiposVenda = TIPOS_VENDA;
  readonly rotulos = ROTULOS;
  readonly ufs = UFS_REGIAO;

  readonly form = new FormGroup({
    titulo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    descricao: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(4000)] }),
    preco: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    categoriaAnimal: new FormControl<CategoriaAnimal>('BEZERRO', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    raca: new FormControl<Raca>('NELORE', { nonNullable: true, validators: [Validators.required] }),
    aptidao: new FormControl<Aptidao>('CORTE', { nonNullable: true, validators: [Validators.required] }),
    tipoVenda: new FormControl<TipoVenda>('CABECA', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    quantidade: new FormControl<number | null>(null, [Validators.min(1), Validators.max(100_000)]),
    pesoMedioKg: new FormControl<number | null>(null, [Validators.min(1), Validators.max(3_000)]),
    uf: new FormControl(UF_PADRAO, { nonNullable: true, validators: [Validators.required] }),
    cidade: new FormControl('Formosa', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(110)],
    }),
  });

  readonly maximoFotos = MAXIMO_FOTOS;

  anuncioId: number | null = null;
  carregandoAnuncio = false;
  imagens: FotoSelecionada[] = [];
  /** O form guarda o número; isto é só o texto mascarado exibido no campo. */
  precoTexto = '';
  enviando = false;
  /** Percentual do upload; nulo enquanto o navegador ainda não informou o tamanho. */
  progresso: number | null = null;
  preparandoFotos = false;
  tentouEnviar = false;
  /** Enquanto o vendedor não mexer no título, ele acompanha categoria, raça e quantidade. */
  tituloEditado = false;

  private ultimoTituloSugerido = '';
  private assinaturas = new Subscription();

  constructor(
    private readonly anunciosApi: AnuncioService,
    private readonly auth: AuthService,
    private readonly sessao: SessaoService,
    private readonly verificacao: VerificacaoService,
    private readonly fila: FilaPublicacaoService,
    private readonly planoPro: PlanoProService,
    private readonly localidades: LocalidadesService,
    private readonly seletorCidade: SeletorCidadeService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly navCtrl: NavController,
    private readonly toastCtrl: ToastController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  get editando(): boolean {
    return this.anuncioId !== null;
  }

  get cidadeInvalida(): boolean {
    const cidade = this.form.controls.cidade;
    return cidade.invalid && (cidade.touched || this.tentouEnviar);
  }

  get precoInvalido(): boolean {
    const preco = this.form.controls.preco;
    return preco.invalid && (preco.touched || this.tentouEnviar);
  }

  get rotuloPreco(): string {
    return ROTULO_PRECO[this.form.controls.tipoVenda.value];
  }

  /** "Lote: R$ 56.000" etc.: o vendedor confere a conta antes de publicar. */
  get previaPreco(): string | null {
    const valores = this.form.getRawValue();
    return precoComplementar({
      preco: valores.preco ?? 0,
      tipoVenda: valores.tipoVenda,
      quantidade: valores.quantidade,
      pesoMedioKg: valores.pesoMedioKg,
    });
  }

  get textoBotao(): string {
    if (this.enviando) {
      return this.progresso !== null && this.progresso < 100
        ? `Enviando fotos... ${this.progresso}%`
        : 'Salvando...';
    }
    return this.editando ? 'Salvar alterações' : 'Publicar anúncio';
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.anuncioId = id;
      this.tituloEditado = true;
      void this.carregarParaEdicao(id);
    } else {
      this.restaurarRascunho();
      this.aplicarTituloSugerido();
    }
    void this.localidades.municipios(this.form.controls.uf.value);

    this.assinaturas.add(
      this.form.valueChanges.subscribe(() => this.aplicarTituloSugerido())
    );
    this.assinaturas.add(
      this.form.valueChanges.pipe(debounceTime(600)).subscribe(() => this.salvarRascunho())
    );
  }

  ngOnDestroy(): void {
    this.assinaturas.unsubscribe();
    this.liberarPreviews();
  }

  escolher(campo: CampoDeUmToque, valor: string): void {
    (this.form.controls[campo] as FormControl<string>).setValue(valor);
  }

  aoDigitarPreco(event: Event): void {
    const valor = lerMoeda(String((event as CustomEvent).detail.value ?? ''));
    this.precoTexto = valor === null ? '' : formatarMoeda(valor);
    // Sem isto, um caractere recusado (letra, dígito além do limite) fica no campo: o binding não muda.
    (event.target as HTMLIonInputElement).value = this.precoTexto;
    this.form.controls.preco.setValue(valor);
    this.form.controls.preco.markAsDirty();
  }

  aoMudarUf(event: Event): void {
    const uf = (event as CustomEvent).detail.value as string;
    this.form.controls.cidade.setValue('');
    void this.localidades.municipios(uf);
  }

  async escolherCidade(): Promise<void> {
    const uf = this.form.controls.uf.value;
    const cidade = await this.seletorCidade.escolher({
      titulo: `Cidade (${uf})`,
      carregar: () => this.localidades.municipios(uf),
      selecionada: this.form.controls.cidade.value,
    });
    this.atualizar(() => {
      if (cidade) {
        this.form.controls.cidade.setValue(cidade);
      }
      this.form.controls.cidade.markAsTouched();
    });
  }

  escolherImagens(): void {
    this.seletorArquivo.nativeElement.click();
  }

  async aoSelecionarImagens(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    input.value = '';
    if (!arquivos.length) {
      return;
    }

    const vagas = Math.max(MAXIMO_FOTOS - this.imagens.length, 0);
    const doTipoCerto = arquivos.filter(arquivo => TIPOS_IMAGEM.includes(arquivo.type));
    this.atualizar(() => (this.preparandoFotos = true));
    const comprimidos = await Promise.all(doTipoCerto.slice(0, vagas).map(comprimirImagem));
    const aceitos = comprimidos.filter(arquivo => arquivo.size <= TAMANHO_MAXIMO_BYTES);

    this.atualizar(() => {
      this.preparandoFotos = false;
      this.imagens = [
        ...this.imagens,
        ...aceitos.map(arquivo => ({ arquivo, url: URL.createObjectURL(arquivo) })),
      ];
    });

    const excedentes = doTipoCerto.length - Math.min(doTipoCerto.length, vagas);
    const recusados = arquivos.length - doTipoCerto.length + (comprimidos.length - aceitos.length);
    if (excedentes > 0) {
      const sobra = excedentes === 1 ? '1 foto ficou de fora' : `${excedentes} fotos ficaram de fora`;
      void this.avisar(`Máximo de ${MAXIMO_FOTOS} fotos por anúncio: ${sobra}.`, 'warning');
    } else if (recusados > 0) {
      void this.avisar(`${recusados} arquivo(s) ignorado(s): use fotos JPG, PNG ou WEBP.`, 'warning');
    }
  }

  removerImagem(indice: number): void {
    const [removida] = this.imagens.splice(indice, 1);
    if (removida?.arquivo) {
      URL.revokeObjectURL(removida.url);
    }
    this.imagens = [...this.imagens];
  }

  async publicar(): Promise<void> {
    if (this.enviando || this.preparandoFotos) {
      return;
    }
    this.tentouEnviar = true;
    this.form.markAllAsTouched();
    if (!this.imagens.length) {
      void this.conteudo?.scrollToTop(300);
      await this.avisar('Adicione pelo menos 1 foto do lote para publicar o anúncio.', 'warning');
      return;
    }
    if (this.form.invalid) {
      await this.avisar('Confira os campos destacados em vermelho.', 'warning');
      return;
    }

    const logado = await this.sessao.exigirLogin();
    if (!logado) {
      return;
    }
    const verificado = await this.verificacao.exigirVerificacao();
    if (!verificado) {
      return;
    }

    const valores = this.form.getRawValue();
    const dados: NovoAnuncio = {
      titulo: valores.titulo.trim(),
      descricao: valores.descricao.trim(),
      preco: valores.preco as number,
      cidade: formatarCidade(valores.cidade, valores.uf),
      categoriaAnimal: valores.categoriaAnimal,
      raca: valores.raca,
      aptidao: valores.aptidao,
      tipoVenda: valores.tipoVenda,
      quantidade: valores.quantidade || null,
      pesoMedioKg: valores.pesoMedioKg || null,
    };
    const novas = this.imagens.flatMap(foto => (foto.arquivo ? [foto.arquivo] : []));
    const podeEsperarSinal = !this.editando && this.fila.disponivel;
    if (podeEsperarSinal && !navigator.onLine) {
      await this.guardarNaFila(dados, novas, false);
      return;
    }

    this.atualizar(() => {
      this.enviando = true;
      this.progresso = null;
    });
    const aoProgredir = (percentual: number) => this.atualizar(() => (this.progresso = percentual));
    try {
      if (this.anuncioId !== null) {
        // O backend grava as mantidas primeiro e as novas depois, a mesma ordem das miniaturas.
        dados.fotosMantidas = this.imagens.filter(foto => !foto.arquivo).map(foto => foto.url);
        await this.anunciosApi.atualizar(this.anuncioId, dados, novas, aoProgredir);
        await this.avisar('Anúncio atualizado!', 'success');
        this.voltarParaConta();
      } else {
        const criado = await this.anunciosApi.criar(dados, novas, aoProgredir);
        localStorage.removeItem(CHAVE_RASCUNHO);
        this.atualizar(() => this.limpar());
        await this.avisar('Anúncio publicado! Ele já aparece na vitrine.', 'success');
        this.zone.run(() => void this.router.navigate(['/tabs/tab1/anuncio', criado.id]));
      }
    } catch (erro) {
      const semSinal = (erro instanceof HttpErrorResponse && erro.status === 0) || !navigator.onLine;
      if (limiteDeAnunciosAtingido(erro)) {
        // O formulário e as fotos ficam: virando PRO (ou vendendo um lote), é só tocar em publicar de novo.
        await this.exibirPaywallPro();
      } else if (semSinal && podeEsperarSinal) {
        await this.guardarNaFila(dados, novas, true);
      } else {
        await this.tratarErro(erro);
      }
    } finally {
      this.atualizar(() => {
        this.enviando = false;
        this.progresso = null;
      });
    }
  }

  private exibirPaywallPro(): Promise<void> {
    return this.planoPro.exibirPaywallPro();
  }

  /** Sem sinal no campo: o anúncio vai para a fila do aparelho e sai sozinho quando a internet voltar. */
  private async guardarNaFila(dados: NovoAnuncio, fotos: File[], envioInterrompido: boolean): Promise<void> {
    try {
      await this.fila.adicionar(dados, fotos, envioInterrompido);
    } catch {
      await this.avisar(
        'Sem internet. Seus dados e fotos continuam aqui: toque em publicar de novo quando tiver sinal.',
        'warning'
      );
      return;
    }
    localStorage.removeItem(CHAVE_RASCUNHO);
    this.atualizar(() => this.limpar());
    await this.avisar(
      'Sem internet agora. O anúncio ficou guardado no aparelho e será publicado sozinho quando o sinal voltar.',
      'warning'
    );
    this.voltarParaConta();
  }

  private tituloSugerido(): string {
    const { categoriaAnimal, raca, quantidade } = this.form.getRawValue();
    const nomeRaca = raca === 'OUTRA' || raca === 'MISTO' ? '' : ` ${ROTULOS[raca]}`;
    if (quantidade && quantidade > 1) {
      return `Lote de ${quantidade} ${PLURAIS[categoriaAnimal]}${nomeRaca}`;
    }
    return `${ROTULOS[categoriaAnimal]}${nomeRaca}`;
  }

  /** Título diferente da última sugestão = o vendedor escreveu o dele; daí em diante não mexemos mais. */
  private aplicarTituloSugerido(): void {
    const atual = this.form.controls.titulo.value;
    if (!this.tituloEditado && this.ultimoTituloSugerido && atual !== this.ultimoTituloSugerido) {
      this.tituloEditado = true;
    }
    if (this.tituloEditado) {
      return;
    }
    const sugerido = this.tituloSugerido();
    this.ultimoTituloSugerido = sugerido;
    if (atual !== sugerido) {
      this.form.controls.titulo.setValue(sugerido, { emitEvent: false });
    }
  }

  /** Sem as fotos (não cabem no armazenamento local): o que dá trabalho digitar volta se o app fechar. */
  private salvarRascunho(): void {
    if (this.editando || this.enviando) {
      return;
    }
    try {
      localStorage.setItem(
        CHAVE_RASCUNHO,
        JSON.stringify({ valores: this.form.getRawValue(), tituloEditado: this.tituloEditado })
      );
    } catch {
      // Armazenamento cheio: o formulário continua funcionando.
    }
  }

  private restaurarRascunho(): void {
    try {
      const salvo = JSON.parse(localStorage.getItem(CHAVE_RASCUNHO) ?? 'null') as {
        valores?: Parameters<PublicarPage['form']['patchValue']>[0];
        tituloEditado?: boolean;
      } | null;
      if (!salvo?.valores) {
        return;
      }
      this.form.patchValue(salvo.valores, { emitEvent: false });
      this.tituloEditado = !!salvo.tituloEditado;
      if (salvo.valores.preco) {
        this.precoTexto = formatarMoeda(salvo.valores.preco);
      }
      void this.avisarRascunho();
    } catch {
      localStorage.removeItem(CHAVE_RASCUNHO);
    }
  }

  private async avisarRascunho(): Promise<void> {
    const toast = await this.toastCtrl.create({
      message: 'Continuamos de onde você parou. As fotos precisam ser escolhidas de novo.',
      duration: 6000,
      position: 'top',
      buttons: [
        {
          text: 'Começar do zero',
          handler: () => {
            localStorage.removeItem(CHAVE_RASCUNHO);
            this.atualizar(() => {
              this.limpar();
              this.aplicarTituloSugerido();
            });
          },
        },
      ],
    });
    await toast.present();
  }

  private async carregarParaEdicao(id: number): Promise<void> {
    this.atualizar(() => (this.carregandoAnuncio = true));
    const logado = await this.sessao.exigirLogin();
    if (!logado) {
      this.voltarParaConta();
      return;
    }
    try {
      const anuncio = await this.anunciosApi.buscar(id);
      if (anuncio.vendedorId !== this.auth.snapshot?.usuarioId) {
        await this.avisar('Você só pode editar os seus anúncios.', 'warning');
        this.voltarParaConta();
        return;
      }
      const local = separarCidade(anuncio.cidade);
      const uf = local.uf || UF_PADRAO;
      void this.localidades.municipios(uf);
      this.atualizar(() => {
        this.form.setValue(
          {
            titulo: anuncio.titulo,
            descricao: anuncio.descricao ?? '',
            preco: anuncio.preco,
            categoriaAnimal: anuncio.categoriaAnimal,
            raca: anuncio.raca,
            aptidao: anuncio.aptidao,
            tipoVenda: anuncio.tipoVenda,
            quantidade: anuncio.quantidade ?? null,
            pesoMedioKg: anuncio.pesoMedioKg ?? null,
            uf,
            cidade: local.nome,
          },
          { emitEvent: false }
        );
        this.precoTexto = formatarMoeda(anuncio.preco);
        this.imagens = anuncio.fotos.map(url => ({ url }));
        this.carregandoAnuncio = false;
      });
    } catch {
      await this.avisar('Não foi possível carregar o anúncio. Confira sua conexão.', 'danger');
      this.voltarParaConta();
    }
  }

  private voltarParaConta(): void {
    this.zone.run(() => void this.navCtrl.navigateBack('/tabs/tab3'));
  }

  private async tratarErro(erro: unknown): Promise<void> {
    const status = erro instanceof HttpErrorResponse ? erro.status : -1;
    if (status === 0 || !navigator.onLine) {
      await this.avisar(
        'Sem internet. Seus dados e fotos continuam aqui: toque em publicar de novo quando tiver sinal.',
        'warning'
      );
    } else if (status === 401 || status === 403) {
      this.auth.sair();
      await this.avisar('Sua sessão expirou. Toque em salvar para entrar de novo.', 'warning');
    } else if (status === 428) {
      // A sessão salva dizia verificado, mas quem decide é o servidor.
      this.auth.iniciarSessao({ ...this.auth.snapshot!, telefoneVerificado: false });
      await this.avisar('Confirme seu WhatsApp e toque em publicar de novo.', 'warning');
      void this.verificacao.exigirVerificacao();
    } else if (status === 413) {
      await this.avisar('As fotos são grandes demais para enviar juntas. Remova uma e tente de novo.', 'danger');
    } else if (status === 400 || status === 415) {
      await this.avisar('O servidor recusou os dados ou as fotos. Confira e tente de novo.', 'danger');
    } else if (status === 404) {
      await this.avisar('Este anúncio não existe mais.', 'danger');
    } else {
      await this.avisar('Não foi possível salvar agora. Seus dados continuam aqui; tente de novo.', 'danger');
    }
  }

  private limpar(): void {
    this.liberarPreviews();
    this.tentouEnviar = false;
    this.tituloEditado = false;
    this.ultimoTituloSugerido = '';
    this.precoTexto = '';
    this.form.reset(undefined, { emitEvent: false });
  }

  private liberarPreviews(): void {
    this.imagens.forEach(foto => foto.arquivo && URL.revokeObjectURL(foto.url));
    this.imagens = [];
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
      duration: color === 'success' ? 2600 : 5000,
      position: 'top',
      buttons: color === 'success' ? [] : [{ text: 'OK', role: 'cancel' }],
    });
    await toast.present();
  }
}
