// This file can be replaced during build by using the `fileReplacements` array.
// `ng build` replaces `environment.ts` with `environment.prod.ts`.
// The list of file replacements can be found in `angular.json`.

export const environment = {
  production: false,
  // Vazio de propósito: o navegador chama /api no próprio ng serve.
  // O proxy (proxy.conf.js) encaminha para https://appcompregado.com.br sem o header Origin,
  // porque a API de produção recusa CORS de http://127.0.0.1:4200.
  // O build do APK troca este arquivo por environment.prod.ts (URL absoluta).
  apiUrl: '',
  /** Número do bot que recebe o código de verificação (DDI + DDD + número). */
  whatsappVerificacao: '5561994044146',
  /** Simulador local só existe com a API na máquina. Nos testes contra a VPS o botão abre o wa.me. */
  whatsappSimulado: null as string | null,
  /** WhatsApp que fecha o plano Vendedor PRO (DDI + DDD + número). Substituir pelo telefone real. */
  whatsappPlanoPro: '5561999999999',
  webBaseUrl: 'https://appcompregado.com.br',
  googlePlayUrl: 'https://play.google.com/store/apps/details?id=br.com.appcompregado',
};

/*
 * For easier debugging in development mode, you can import the following file
 * to ignore zone related error stack frames such as `zone.run`, `zoneDelegate.invokeTask`.
 *
 * This import should be commented out in production mode because it will have a negative impact
 * on performance if an error is thrown.
 */
// import 'zone.js/plugins/zone-error';  // Included with Angular CLI.
