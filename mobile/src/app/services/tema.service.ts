import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

const CHAVE_TEMA = 'compregado-modo-sol';
const CLASSE_MODO_SOL = 'modo-sol';

/**
 * Modo sol: tema claro de alto contraste para usar a tela no curral ou na estrada, com sol forte.
 * A paleta fica em theme/variables.scss, sob a classe "modo-sol" do <html>.
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly modoSol = new BehaviorSubject<boolean>(this.lerPreferencia());

  readonly modoSol$: Observable<boolean> = this.modoSol.asObservable();

  constructor() {
    this.aplicar(this.modoSol.value);
  }

  get ativo(): boolean {
    return this.modoSol.value;
  }

  alternar(): void {
    this.definir(!this.modoSol.value);
  }

  definir(ligado: boolean): void {
    try {
      localStorage.setItem(CHAVE_TEMA, ligado ? '1' : '0');
    } catch {
      // Sem armazenamento: o tema vale só até fechar o app.
    }
    this.aplicar(ligado);
    this.modoSol.next(ligado);
  }

  private aplicar(ligado: boolean): void {
    document.documentElement.classList.toggle(CLASSE_MODO_SOL, ligado);
  }

  private lerPreferencia(): boolean {
    try {
      return localStorage.getItem(CHAVE_TEMA) === '1';
    } catch {
      return false;
    }
  }
}
