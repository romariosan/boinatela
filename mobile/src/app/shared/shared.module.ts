import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular/lazy';
import { AuthModalComponent } from '../modals/auth-modal.component';
import { AnuncioCardComponent } from './anuncio-card/anuncio-card.component';
import { SeletorCidadeComponent } from './seletor-cidade/seletor-cidade.component';
import { VerificarWhatsappComponent } from './verificar-whatsapp/verificar-whatsapp.component';

@NgModule({
  declarations: [AuthModalComponent, AnuncioCardComponent, SeletorCidadeComponent, VerificarWhatsappComponent],
  imports: [CommonModule, FormsModule, IonicModule],
  exports: [AuthModalComponent, AnuncioCardComponent, SeletorCidadeComponent, VerificarWhatsappComponent],
})
export class SharedModule {}
