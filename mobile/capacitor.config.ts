/// <reference types="@capacitor/splash-screen" />
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'br.com.appcompregado',
  appName: 'CompreGado',
  webDir: 'www',
  server: {
    androidScheme: 'https',
    // localhost faz o WebView do celular tentar a porta 443 e cair em ERR_CONNECTION_REFUSED.
    hostname: 'compregado.local',
  },
  plugins: {
    CapacitorHttp: {
      enabled: true,
    },
    SplashScreen: {
      launchShowDuration: 2000,
      launchAutoHide: true,
      launchFadeOutDuration: 300,
      // Verde do ícone novo (resources/icon.png e splash.png).
      backgroundColor: '#1c3829',
      // Mantém a proporção e nunca amplia nem corta a imagem.
      androidScaleType: 'CENTER_INSIDE',
      showSpinner: false,
      splashFullScreen: false,
      splashImmersive: false,
    },
  },
};

export default config;
