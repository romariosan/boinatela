import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Capacitor } from '@capacitor/core';

/** WebView do APK/AAB. O site público fica em https://appcompregado.com.br e não entra aqui. */
export function aplicativoInstalado(): boolean {
  if (Capacitor.isNativePlatform()) {
    return true;
  }
  const origem = window.location.origin;
  return (
    origem === 'https://localhost' ||
    origem === 'https://compregado.local' ||
    origem === 'capacitor://localhost'
  );
}

/** No APK/AAB a raiz abre a vitrine. No site, a mesma rota continua sendo a página institucional. */
export const abrirVitrineNoAplicativo: CanActivateFn = () => {
  if (!aplicativoInstalado()) {
    return true;
  }
  return inject(Router).createUrlTree(['/tabs/tab1']);
};
