import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadChildren: () => import('./landing/landing.module').then(m => m.LandingPageModule),
  },
  {
    path: 'app',
    redirectTo: 'tabs/tab1',
    pathMatch: 'full',
  },
  {
    path: 'vitrine',
    redirectTo: 'tabs/tab1',
    pathMatch: 'full',
  },
  {
    path: 'politica-de-privacidade',
    loadChildren: () =>
      import('./pages/privacy-policy/privacy-policy.module').then(m => m.PrivacyPolicyPageModule),
  },
  {
    path: 'lote/:id/vendido',
    loadChildren: () =>
      import('./anuncio-detalhe/anuncio-detalhe.module').then(m => m.AnuncioDetalhePageModule),
  },
  {
    path: 'lote/:id',
    loadChildren: () =>
      import('./anuncio-detalhe/anuncio-detalhe.module').then(m => m.AnuncioDetalhePageModule),
  },
  {
    path: 'admin',
    loadChildren: () =>
      import('./admin/admin-dashboard.module').then(m => m.AdminDashboardPageModule),
  },
  {
    path: '',
    loadChildren: () => import('./tabs/tabs.module').then(m => m.TabsPageModule)
  }
];
@NgModule({
  imports: [
    RouterModule.forRoot(routes, { preloadingStrategy: PreloadAllModules })
  ],
  exports: [RouterModule]
})
export class AppRoutingModule {}
