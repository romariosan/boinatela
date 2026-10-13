import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, NgZone, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IonContent, NavController, ToastController } from '@ionic/angular/lazy';
import {
  APTIDOES,
  Aptidao,
  CATEGORIAS_ANIMAL,
  CategoriaAnimal,
  MAXIMO_FOTOS,
  NovoAnuncio,
  RACAS,
  ROTULOS,
  Raca,
  TIPOS_VENDA,
  TipoVenda,
  formatarMoeda,
  lerMoeda,
} from '../models/anuncio';
import { UFS_REGIAO, UF_PADRAO, formatarCidade, separarCidade } from '../models/localidade';
import { AnuncioService } from '../services/anuncio.service';
import { AuthService } from '../services/auth.service';
import { LocalidadesService } from '../services/localidades.service';
import { SessaoService } from '../services/sessao.service';
import { VerificacaoService } from '../services/verificacao.service';
import { SeletorCidadeService } from '../shared/seletor-cidade/seletor-cidade.service';

const TIPOS_IMAGEM = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const TAMANHO_MAXIMO_BYTES = 10 * 1024 * 1024;

/** Sem {@code arquivo} = foto que já está no anúncio (modo edição). */
interface FotoSelecionada {
  url: string;
  arquivo?: File;
}

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
  tentouEnviar = false;

  constructor(
    private readonly anunciosApi: AnuncioService,
    private readonly auth: AuthService,
    private readonly sessao: SessaoService,
    private readonly verificacao: VerificacaoService,
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

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.anuncioId = id;
      void this.carregarParaEdicao(id);
    }
    void this.localidades.municipios(this.form.controls.uf.value);
  }

  ngOnDestroy(): void {
    this.liberarPreviews();
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

  aoSelecionarImagens(event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    input.value = '';
    if (!arquivos.length) {
      return;
    }

    const validos = arquivos.filter(
      arquivo => TIPOS_IMAGEM.includes(arquivo.type) && arquivo.size <= TAMANHO_MAXIMO_BYTES
    );
    const vagas = MAXIMO_FOTOS - this.imagens.length;
    const aceitos = validos.slice(0, Math.max(vagas, 0));
    this.imagens = [
      ...this.imagens,
      ...aceitos.map(arquivo => ({ arquivo, url: URL.createObjectURL(arquivo) })),
    ];

    const recusados = arquivos.length - validos.length;
    const excedentes = validos.length - aceitos.length;
    if (excedentes > 0) {
      const sobra = excedentes === 1 ? '1 foto ficou de fora' : `${excedentes} fotos ficaram de fora`;
      void this.avisar(`Máximo de ${MAXIMO_FOTOS} fotos por anúncio: ${sobra}.`, 'warning');
    } else if (recusados > 0) {
      void this.avisar(
        `${recusados} arquivo(s) ignorado(s): use JPG, PNG, WEBP ou GIF de até 10 MB.`,
        'warning'
      );
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
    if (this.enviando) {
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
      await this.avisar('Confira os campos destacados.', 'warning');
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

    this.atualizar(() => (this.enviando = true));
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
    };
    const novas = this.imagens.flatMap(foto => (foto.arquivo ? [foto.arquivo] : []));
    try {
      if (this.anuncioId !== null) {
        // O backend grava as mantidas primeiro e as novas depois, a mesma ordem das miniaturas.
        dados.fotosMantidas = this.imagens.filter(foto => !foto.arquivo).map(foto => foto.url);
        await this.anunciosApi.atualizar(this.anuncioId, dados, novas);
        await this.avisar('Anúncio atualizado!', 'success');
        this.voltarParaConta();
      } else {
        const criado = await this.anunciosApi.criar(dados, novas);
        this.atualizar(() => this.limpar());
        await this.avisar('Anúncio publicado!', 'success');
        this.zone.run(() => void this.router.navigate(['/tabs/tab1/anuncio', criado.id]));
      }
    } catch (erro) {
      await this.tratarErro(erro);
    } finally {
      this.atualizar(() => (this.enviando = false));
    }
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
        this.form.setValue({
          titulo: anuncio.titulo,
          descricao: anuncio.descricao ?? '',
          preco: anuncio.preco,
          categoriaAnimal: anuncio.categoriaAnimal,
          raca: anuncio.raca,
          aptidao: anuncio.aptidao,
          tipoVenda: anuncio.tipoVenda,
          uf,
          cidade: local.nome,
        });
        this.precoTexto = formatarMoeda(anuncio.preco);
        this.imagens = anuncio.fotos.map(url => ({ url }));
        this.carregandoAnuncio = false;
      });
    } catch {
      await this.avisar('Não foi possível carregar o anúncio.', 'danger');
      this.voltarParaConta();
    }
  }

  private voltarParaConta(): void {
    this.zone.run(() => void this.navCtrl.navigateBack('/tabs/tab3'));
  }

  private async tratarErro(erro: unknown): Promise<void> {
    const status = erro instanceof HttpErrorResponse ? erro.status : 0;
    if (status === 401 || status === 403) {
      this.auth.sair();
      await this.avisar('Sua sessão expirou. Toque em salvar para entrar de novo.', 'warning');
    } else if (status === 428) {
      // A sessão salva dizia verificado, mas quem decide é o servidor.
      this.auth.iniciarSessao({ ...this.auth.snapshot!, telefoneVerificado: false });
      await this.avisar('Confirme seu WhatsApp e toque em publicar de novo.', 'warning');
      void this.verificacao.exigirVerificacao();
    } else if (status === 413) {
      await this.avisar('As fotos são grandes demais para enviar juntas.', 'danger');
    } else if (status === 400 || status === 415) {
      await this.avisar('O servidor recusou os dados ou as fotos. Confira e tente de novo.', 'danger');
    } else if (status === 404) {
      await this.avisar('Este anúncio não existe mais.', 'danger');
    } else {
      await this.avisar('Não foi possível salvar. Verifique a conexão e tente de novo.', 'danger');
    }
  }

  private limpar(): void {
    this.liberarPreviews();
    this.tentouEnviar = false;
    this.precoTexto = '';
    this.form.reset();
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
    const toast = await this.toastCtrl.create({ message, color, duration: 2600 });
    await toast.present();
  }
}
