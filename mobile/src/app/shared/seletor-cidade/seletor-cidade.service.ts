import { Injectable } from '@angular/core';
import { ModalController } from '@ionic/angular/lazy';
import { SeletorCidadeComponent } from './seletor-cidade.component';

export interface OpcoesSeletorCidade {
  titulo?: string;
  carregar: () => Promise<string[]>;
  selecionada?: string;
  opcaoTodas?: string;
  exibir?: (cidade: string) => string;
}

@Injectable({ providedIn: 'root' })
export class SeletorCidadeService {
  constructor(private readonly modalCtrl: ModalController) {}

  /** Devolve a cidade escolhida ('' = opção "todas") ou null se o usuário cancelar. */
  async escolher(opcoes: OpcoesSeletorCidade): Promise<string | null> {
    const modal = await this.modalCtrl.create({
      component: SeletorCidadeComponent,
      componentProps: { ...opcoes },
    });
    await modal.present();
    const { data, role } = await modal.onWillDismiss<{ cidade: string }>();
    return role === 'confirm' && data ? data.cidade : null;
  }
}
