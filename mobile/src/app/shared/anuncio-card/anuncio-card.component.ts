import { Component, EventEmitter, Input, Output } from '@angular/core';
import {
  Anuncio,
  FOTO_PLACEHOLDER_GADO,
  ROTULOS,
  formatarPreco,
  precoComplementar,
  resumoLote,
} from '../../models/anuncio';

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
  readonly rotulos = ROTULOS;

  usarPlaceholder(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (!img.src.endsWith(FOTO_PLACEHOLDER_GADO)) {
      img.src = FOTO_PLACEHOLDER_GADO;
    }
  }

  get precoLabel(): string {
    return formatarPreco(this.anuncio);
  }

  get complemento(): string | null {
    return precoComplementar(this.anuncio);
  }

  get lote(): string | null {
    return resumoLote(this.anuncio);
  }

  /** O leitor de tela lê o card inteiro de uma vez, na ordem em que o comprador decide. */
  get descricaoAcessivel(): string {
    const partes = [
      this.precoLabel,
      `${ROTULOS[this.anuncio.categoriaAnimal]} ${ROTULOS[this.anuncio.raca]}`,
      this.lote,
      this.anuncio.cidade,
      this.anuncio.titulo,
      this.coletadoDeGrupo ? 'coletado de um grupo, vendedor ainda não confirmou' : null,
      this.anuncio.status === 'VENDIDO' ? 'vendido' : null,
    ];
    return partes.filter(Boolean).join(', ');
  }

  /** Lote ouvido no grupo e ainda sem o dono. */
  get coletadoDeGrupo(): boolean {
    return !!this.anuncio.capturado && !this.anuncio.reivindicado;
  }
}
