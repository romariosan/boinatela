import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { abrirVitrineNoAplicativo } from './abrir-vitrine-no-aplicativo.guard';
import { LandingPage } from './landing.page';

const routes: Routes = [
  {
    path: '',
    component: LandingPage,
    canActivate: [abrirVitrineNoAplicativo],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class LandingPageRoutingModule {}
