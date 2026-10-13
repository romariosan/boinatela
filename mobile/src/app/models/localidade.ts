export interface Uf {
  sigla: string;
  nome: string;
}

export const UF_PADRAO = 'GO';

export const UFS: readonly Uf[] = [
  { sigla: 'AC', nome: 'Acre' },
  { sigla: 'AL', nome: 'Alagoas' },
  { sigla: 'AP', nome: 'Amapá' },
  { sigla: 'AM', nome: 'Amazonas' },
  { sigla: 'BA', nome: 'Bahia' },
  { sigla: 'CE', nome: 'Ceará' },
  { sigla: 'DF', nome: 'Distrito Federal' },
  { sigla: 'ES', nome: 'Espírito Santo' },
  { sigla: 'GO', nome: 'Goiás' },
  { sigla: 'MA', nome: 'Maranhão' },
  { sigla: 'MT', nome: 'Mato Grosso' },
  { sigla: 'MS', nome: 'Mato Grosso do Sul' },
  { sigla: 'MG', nome: 'Minas Gerais' },
  { sigla: 'PA', nome: 'Pará' },
  { sigla: 'PB', nome: 'Paraíba' },
  { sigla: 'PR', nome: 'Paraná' },
  { sigla: 'PE', nome: 'Pernambuco' },
  { sigla: 'PI', nome: 'Piauí' },
  { sigla: 'RJ', nome: 'Rio de Janeiro' },
  { sigla: 'RN', nome: 'Rio Grande do Norte' },
  { sigla: 'RS', nome: 'Rio Grande do Sul' },
  { sigla: 'RO', nome: 'Rondônia' },
  { sigla: 'RR', nome: 'Roraima' },
  { sigla: 'SC', nome: 'Santa Catarina' },
  { sigla: 'SP', nome: 'São Paulo' },
  { sigla: 'SE', nome: 'Sergipe' },
  { sigla: 'TO', nome: 'Tocantins' },
];

const UFS_DA_REGIAO = ['DF', 'GO'];

/** DF e GO no topo (Formosa e Entorno do DF), depois as demais em ordem alfabética. */
export const UFS_REGIAO: readonly Uf[] = [
  ...UFS_DA_REGIAO.map(sigla => UFS.find(uf => uf.sigla === sigla)!),
  ...UFS.filter(uf => !UFS_DA_REGIAO.includes(uf.sigla)),
];

/** Usado quando a API do IBGE não responde (sem internet, por exemplo). */
export const CIDADES_PRINCIPAIS: Readonly<Record<string, readonly string[]>> = {
  GO: [
    'Formosa',
    'Planaltina',
    'Cabeceiras',
    'Flores de Goiás',
    'Vila Boa',
    "São João d'Aliança",
    'Alto Paraíso de Goiás',
    'Água Fria de Goiás',
    'Luziânia',
    'Cristalina',
    'Valparaíso de Goiás',
    'Águas Lindas de Goiás',
    'Santo Antônio do Descoberto',
    'Padre Bernardo',
    'Goiânia',
    'Anápolis',
    'Rio Verde',
    'Jataí',
  ],
  DF: ['Brasília'],
  MG: ['Unaí', 'Buritis', 'Paracatu', 'Arinos', 'Formoso', 'Uberlândia', 'Uberaba', 'Montes Claros', 'Belo Horizonte'],
  MT: ['Cuiabá', 'Rondonópolis', 'Sinop', 'Barra do Garças', 'Cáceres', 'Primavera do Leste'],
  TO: ['Palmas', 'Araguaína', 'Gurupi', 'Paraíso do Tocantins', 'Porto Nacional'],
};

export function formatarCidade(nome: string, uf: string): string {
  return `${nome.trim()} - ${uf}`;
}

/** "Formosa - GO" → { nome: "Formosa", uf: "GO" }. Sem sufixo de UF, devolve uf vazia. */
export function separarCidade(valor: string): { nome: string; uf: string } {
  const partes = /^(.*\S) - ([A-Z]{2})$/.exec(valor.trim());
  return partes ? { nome: partes[1], uf: partes[2] } : { nome: valor.trim(), uf: '' };
}

export function semAcentos(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}
