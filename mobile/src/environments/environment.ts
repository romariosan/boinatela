// This file can be replaced during build by using the `fileReplacements` array.
// `ng build` replaces `environment.ts` with `environment.prod.ts`.
// The list of file replacements can be found in `angular.json`.

export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  /** Número do bot que recebe o código de verificação (DDI + DDD + número). */
  whatsappVerificacao: '556199999999',
  /** API rodando com WHATSAPP_ENVIO_NO_LOG=true: os botões de WhatsApp abrem o simulador em vez do wa.me. */
  whatsappSimulado: 'http://localhost:8080/dev/whatsapp' as string | null,
};

/*
 * For easier debugging in development mode, you can import the following file
 * to ignore zone related error stack frames such as `zone.run`, `zoneDelegate.invokeTask`.
 *
 * This import should be commented out in production mode because it will have a negative impact
 * on performance if an error is thrown.
 */
// import 'zone.js/plugins/zone-error';  // Included with Angular CLI.
