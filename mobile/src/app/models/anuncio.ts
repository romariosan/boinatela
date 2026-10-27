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
/** PAUSADO: o plano PRO venceu e o anúncio passou do limite gratuito; só o dono vê e pode reativar. */
export type AnuncioStatus = 'ATIVO' | 'PAUSADO' | 'VENDIDO';

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

export function unidadePreco(tipoVenda: TipoVenda): string {
  return tipoVenda === 'KG' ? '/kg' : tipoVenda === 'LOTE' ? '/lote' : '/cabeça';
}

export function formatarPreco(
  anuncio: Pick<Anuncio, 'preco' | 'tipoVenda'> & { precoFormatado?: string }
): string {
  if (!anuncio.preco) {
    return anuncio.precoFormatado || 'Preço a Combinar';
  }
  return `${formatarMoeda(anuncio.preco)} ${unidadePreco(anuncio.tipoVenda)}`;
}

export const ROTULO_PRECO: Record<TipoVenda, string> = {
  CABECA: 'Preço por cabeça',
  KG: 'Preço por kg',
  LOTE: 'Preço do lote inteiro',
};

const MOEDA_INTEIRA = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  maximumFractionDigits: 0,
});

/**
 * A conta que o comprador faria de cabeça: total do lote (preço por cabeça), valor por cabeça (lote fechado)
 * ou valor estimado do animal (preço por kg com peso médio). Nulo quando faltam dados para a conta.
 */
export function precoComplementar(
  anuncio: Pick<Anuncio, 'preco' | 'tipoVenda' | 'quantidade' | 'pesoMedioKg'>
): string | null {
  const quantidade = anuncio.quantidade ?? 0;
  const peso = anuncio.pesoMedioKg ?? 0;
  if (!anuncio.preco) {
    return null;
  }
  if (anuncio.tipoVenda === 'CABECA' && quantidade > 1) {
    return `Lote: ${MOEDA_INTEIRA.format(anuncio.preco * quantidade)}`;
  }
  if (anuncio.tipoVenda === 'LOTE' && quantidade > 1) {
    return `≈ ${MOEDA_INTEIRA.format(anuncio.preco / quantidade)} por cabeça`;
  }
  if (anuncio.tipoVenda === 'KG' && peso > 0) {
    return `≈ ${MOEDA_INTEIRA.format(anuncio.preco * peso)} por cabeça`;
  }
  return null;
}

/** Ex.: "20 cabeças · ~180 kg". Nulo se o anúncio não tem nenhum dos dois. */
export function resumoLote(anuncio: Pick<Anuncio, 'quantidade' | 'pesoMedioKg'>): string | null {
  const partes: string[] = [];
  if (anuncio.quantidade) {
    partes.push(anuncio.quantidade === 1 ? '1 cabeça' : `${anuncio.quantidade} cabeças`);
  }
  if (anuncio.pesoMedioKg) {
    partes.push(`~${anuncio.pesoMedioKg} kg`);
  }
  return partes.length ? partes.join(' · ') : null;
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
  /** Cabeças no anúncio; anúncios antigos não têm. */
  quantidade?: number | null;
  /** Peso médio por cabeça, em kg. */
  pesoMedioKg?: number | null;
  status: AnuncioStatus;
  vendedorId: number;
  vendedorNome: string;
  /** Lote ouvido no grupo e confirmado pelo dono. */
  reivindicado?: boolean;
  /** true quando o anúncio veio da escuta, não de uma publicação no app. */
  capturado?: boolean;
  /** false quando o lote foi marcado como vendido. */
  ativo?: boolean;
  /** "Preço a Combinar" quando o valor é zero. */
  precoFormatado?: string;
  /** "📍 Cidade - UF". */
  cidadeFormatada?: string;
  uf?: string | null;
  urlFotoPrincipal?: string | null;
  statusProcessamento?: 'PUBLICADO' | 'RASCUNHO_SEM_FOTO';
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
  quantidade: number | null;
  pesoMedioKg: number | null;
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
  /** Painel /admin. Ausente em sessões antigas = não admin. */
  admin?: boolean;
}

export interface VerificacaoStatus {
  telefoneVerificado: boolean;
  /** Ex.: CG-482913. Nulo depois de verificado. */
  codigoVerificacao: string | null;
  /** Número do bot com DDI; nulo se a API não tiver WHATSAPP_BOT_NUMERO (usa o do environment). */
  whatsappBot?: string | null;
}

export interface AnuncioFiltro {
  q?: string;
  cidade?: string;
  uf?: string;
  categoria?: CategoriaAnimal | '';
  aptidao?: Aptidao | '';
  raca?: Raca | '';
}
