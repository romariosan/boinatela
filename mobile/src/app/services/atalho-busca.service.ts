import { Injectable } from '@angular/core';
import { CategoriaAnimal } from '../models/anuncio';

/** Filtro pedido por outra tela (atalhos de categoria da vitrine); a busca aplica uma vez ao abrir. */
@Injectable({ providedIn: 'root' })
export class AtalhoBuscaService {
  private categoria: CategoriaAnimal | null = null;

  pedirCategoria(categoria: CategoriaAnimal): void {
    this.categoria = categoria;
  }

  consumirCategoria(): CategoriaAnimal | null {
    const pedida = this.categoria;
    this.categoria = null;
    return pedida;
  }
}
