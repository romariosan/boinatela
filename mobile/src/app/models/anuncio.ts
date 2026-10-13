export const CATEGORIAS_ANIMAL = ['BEZERRO', 'GARROTE', 'VACA', 'TOURO'] as const;
export const APTIDOES = ['CORTE', 'LEITE', 'REPRODUCAO'] as const;
export const RACAS = [
  'NELORE',
  'ANGUS',
  'GIROLANDO',
  'BRAHMAN',
  'SENEPOL',
  'BRANGUS',
  'GUZERA',
  'TABAPUA',
  'GIR',
  'HOLANDES',
  'CARACU',
  'CANCHIM',
  'MISTO',
  'OUTRA',
] as const;
export const TIPOS_VENDA = ['CABECA', 'KG', 'LOTE'] as const;

export type CategoriaAnimal = (typeof CATEGORIAS_ANIMAL)[number];
export type Aptidao = (typeof APTIDOES)[number];
export type Raca = (typeof RACAS)[number];
export type TipoVenda = (typeof TIPOS_VENDA)[number];
export type AnuncioStatus = 'ATIVO' | 'VENDIDO';

export const ROTULOS: Record<CategoriaAnimal | Aptidao | Raca | TipoVenda, string> = {
  BEZERRO: 'Bezerro',
  GARROTE: 'Garrote',
  VACA: 'Vaca',
  TOURO: 'Touro',
  CORTE: 'Corte',
  LEITE: 'Leite',
  REPRODUCAO: 'Reprodução',
  NELORE: 'Nelore',
  ANGUS: 'Angus',
  GIROLANDO: 'Girolando',
  BRAHMAN: 'Brahman',
  SENEPOL: 'Senepol',
  BRANGUS: 'Brangus',
  GUZERA: 'Guzerá',
  TABAPUA: 'Tabapuã',
  GIR: 'Gir',
  HOLANDES: 'Holandês',
  CARACU: 'Caracu',
  CANCHIM: 'Canchim',
  MISTO: 'Misto',
  OUTRA: 'Outra',
  CABECA: 'Por cabeça',
  KG: 'Por kg',
  LOTE: 'Lote fechado',
};

export const FOTO_PLACEHOLDER_GADO = 'assets/placeholder-gado.svg';
export const MAXIMO_FOTOS = 5;

const MOEDA_BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
/** Centavos incluídos: até R$ 999.999.999,99. */
const PRECO_MAXIMO_DIGITOS = 11;

export function formatarMoeda(valor: number): string {
  return MOEDA_BRL.format(valor);
}

/** Máscara de caixa registradora: os dígitos digitados entram pela direita como centavos. */
export function lerMoeda(texto: string): number | null {
  const digitos = texto.replace(/\D/g, '').replace(/^0+/, '').slice(0, PRECO_MAXIMO_DIGITOS);
  return digitos ? Number(digitos) / 100 : null;
}

export function formatarPreco(anuncio: Pick<Anuncio, 'preco' | 'tipoVenda'>): string {
  const valor = formatarMoeda(anuncio.preco);
  const tipo = anuncio.tipoVenda === 'KG' ? '/kg' : anuncio.tipoVenda === 'LOTE' ? '/lote' : '/cabeça';
  return `${valor} ${tipo}`;
}

export interface Anuncio {
  id: number;
  titulo: string;
  descricao: string;
  preco: number;
  cidade: string;
  /** A primeira foto é a capa. */
  fotos: string[];
  categoriaAnimal: CategoriaAnimal;
  aptidao: Aptidao;
  raca: Raca;
  tipoVenda: TipoVenda;
  status: AnuncioStatus;
  vendedorId: number;
  vendedorNome: string;
}

export interface NovoAnuncio {
  titulo: string;
  descricao: string;
  preco: number;
  /** Sempre no padrão "Cidade - UF", ex.: "Formosa - GO". */
  cidade: string;
  categoriaAnimal: CategoriaAnimal;
  aptidao: Aptidao;
  raca: Raca;
  tipoVenda: TipoVenda;
  /** Só na edição: fotos atuais que continuam, na ordem; as novas entram depois. */
  fotosMantidas?: string[];
}

export interface CategoriaEstoque {
  categoria: CategoriaAnimal;
  count: number;
}

export interface ContatoVendedor {
  whatsapp: string;
  mensagemSugerida: string;
  urlWhatsapp: string;
}

export interface AuthPayload {
  token: string;
  usuarioId: number;
  nome: string;
  whatsapp: string;
  /** Ausente em sessões salvas antes da verificação existir: tratar como não verificado. */
  telefoneVerificado?: boolean;
  codigoVerificacao?: string | null;
}

export interface VerificacaoStatus {
  telefoneVerificado: boolean;
  /** Ex.: BT-482913. Nulo depois de verificado. */
  codigoVerificacao: string | null;
}

export interface AnuncioFiltro {
  q?: string;
  cidade?: string;
  uf?: string;
  categoria?: CategoriaAnimal | '';
  aptidao?: Aptidao | '';
  raca?: Raca | '';
}
