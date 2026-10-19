export const environment = {
  production: true,
  // Host público. As chamadas acrescentam /api (fica https://appcompregado.com.br/api/...).
  apiUrl: 'https://appcompregado.com.br',
  /** Número do bot que recebe o código de verificação (DDI + DDD + número). */
  whatsappVerificacao: '5561994044146',
  whatsappSimulado: null as string | null,
  /** WhatsApp que fecha o plano Vendedor PRO (DDI + DDD + número). Substituir pelo telefone real. */
  whatsappPlanoPro: '5561999999999',
  webBaseUrl: 'https://appcompregado.com.br',
  googlePlayUrl: 'https://play.google.com/store/apps/details?id=br.com.appcompregado',
};
