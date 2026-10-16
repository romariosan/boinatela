import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';

const routes: Routes = [
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
