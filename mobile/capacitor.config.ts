/// <reference types="@capacitor/splash-screen" />
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'br.com.appcompregado',
  appName: 'CompreGado',
  webDir: 'www',
  plugins: {
    SplashScreen: {
      launchShowDuration: 2000,
      launchAutoHide: true,
      launchFadeOutDuration: 300,
      // Creme da marca: mesmo fundo de resources/splash.png e dos ícones (o logo verde/marrom
      // perde contraste num fundo verde ou escuro).
      backgroundColor: '#F7F3EA',
      // Mantém a proporção e nunca amplia nem corta a imagem.
      androidScaleType: 'CENTER_INSIDE',
      showSpinner: false,
      splashFullScreen: false,
      splashImmersive: false,
    },
  },
};

export default config;
