import { ChangeDetectorRef, Component, Input, NgZone, OnInit } from '@angular/core';
import { ModalController } from '@ionic/angular/lazy';
import { semAcentos } from '../../models/localidade';

@Component({
  selector: 'app-seletor-cidade',
  templateUrl: './seletor-cidade.component.html',
  styleUrls: ['./seletor-cidade.component.scss'],
  standalone: false,
})
export class SeletorCidadeComponent implements OnInit {
  @Input() titulo = 'Cidade';
  @Input({ required: true }) carregar!: () => Promise<string[]>;
  @Input() selecionada = '';
  /** Quando informado, aparece como primeira opção e devolve '' (sem filtro de cidade). */
  @Input() opcaoTodas = '';
  @Input() exibir: (cidade: string) => string = cidade => cidade;

  readonly limite = 150;

  cidades: string[] = [];
  filtradas: string[] = [];
  termo = '';
  carregando = true;

  constructor(
    private readonly modalCtrl: ModalController,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef
  ) {}

  async ngOnInit(): Promise<void> {
    const cidades = await this.carregar().catch(() => [] as string[]);
    this.zone.run(() => {
      this.cidades = cidades;
      this.carregando = false;
      this.filtrar();
      this.cdr.detectChanges();
    });
  }

  aoBuscar(event: Event): void {
    this.termo = ((event as CustomEvent).detail.value ?? '').trim();
    this.filtrar();
  }

  escolher(cidade: string): void {
    void this.modalCtrl.dismiss({ cidade }, 'confirm');
  }

  cancelar(): void {
    void this.modalCtrl.dismiss(null, 'cancel');
  }

  private filtrar(): void {
    const termo = semAcentos(this.termo);
    this.filtradas = termo ? this.cidades.filter(cidade => semAcentos(cidade).includes(termo)) : this.cidades;
  }
}
