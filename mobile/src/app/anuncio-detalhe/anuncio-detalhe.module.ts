import { CommonModule } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { IonicModule } from '@ionic/angular/lazy';
import { AnuncioDetalhePage } from './anuncio-detalhe.page';

@NgModule({
  imports: [
    CommonModule,
    IonicModule,
    RouterModule.forChild([{ path: '', component: AnuncioDetalhePage }]),
  ],
  declarations: [AnuncioDetalhePage],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class AnuncioDetalhePageModule {}
