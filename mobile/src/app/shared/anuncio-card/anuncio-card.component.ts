import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Anuncio, FOTO_PLACEHOLDER_GADO, formatarPreco } from '../../models/anuncio';

@Component({
  selector: 'app-anuncio-card',
  templateUrl: './anuncio-card.component.html',
  styleUrls: ['./anuncio-card.component.scss'],
  standalone: false,
})
export class AnuncioCardComponent {
  @Input({ required: true }) anuncio!: Anuncio;
  @Output() abrir = new EventEmitter<number>();

  readonly fotoPlaceholder = FOTO_PLACEHOLDER_GADO;

  usarPlaceholder(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (!img.src.endsWith(FOTO_PLACEHOLDER_GADO)) {
      img.src = FOTO_PLACEHOLDER_GADO;
    }
  }

  get precoLabel(): string {
    return formatarPreco(this.anuncio);
  }
}
