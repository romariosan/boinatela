import 'zone.js';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { register as registrarSwiper } from 'swiper/element/bundle';

import { AppModule } from './app/app.module';

registrarSwiper();

platformBrowserDynamic().bootstrapModule(AppModule)
  .catch(err => console.log(err));
