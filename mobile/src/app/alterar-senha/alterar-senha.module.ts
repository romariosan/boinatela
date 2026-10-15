import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { IonicModule } from '@ionic/angular/lazy';
import { AlterarSenhaPage } from './alterar-senha.page';

@NgModule({
  imports: [
    CommonModule,
    ReactiveFormsModule,
    IonicModule,
    RouterModule.forChild([{ path: '', component: AlterarSenhaPage }]),
  ],
  declarations: [AlterarSenhaPage],
})
export class AlterarSenhaPageModule {}
