import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, NgZone } from '@angular/core';
import { BehaviorSubject, Observable, distinctUntilChanged, firstValueFrom, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthPayload, VerificacaoStatus } from '../models/anuncio';

const STORAGE_KEY = 'boi-na-tela-auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly usuario = new BehaviorSubject<AuthPayload | null>(this.lerStorage());

  readonly currentUser$: Observable<AuthPayload | null> = this.usuario.asObservable();
  readonly isLoggedIn$: Observable<boolean> = this.usuario.pipe(
    map(usuario => !!usuario?.token),
    distinctUntilChanged()
  );

  constructor(
    private readonly http: HttpClient,
    private readonly zone: NgZone
  ) {}

  get snapshot(): AuthPayload | null {
    return this.usuario.value;
  }

  get token(): string | null {
    return this.usuario.value?.token ?? null;
  }

  get logado(): boolean {
    return !!this.token;
  }

  async login(whatsapp: string, senha: string): Promise<AuthPayload> {
    const payload = await firstValueFrom(
      this.http.post<AuthPayload>(`${environment.apiUrl}/api/auth/login`, { whatsapp, senha })
    );
    this.iniciarSessao(payload);
    return payload;
  }

  async cadastrar(nome: string, whatsapp: string, senha: string): Promise<AuthPayload> {
    const payload = await firstValueFrom(
      this.http.post<AuthPayload>(`${environment.apiUrl}/api/auth/cadastro`, {
        nome,
        whatsapp,
        senha,
      })
    );
    this.iniciarSessao(payload);
    return payload;
  }

  esqueciSenha(whatsapp: string): Promise<{ expiraEmSegundos: number; reenviarEmSegundos: number }> {
    return firstValueFrom(
      this.http.post<{ expiraEmSegundos: number; reenviarEmSegundos: number }>(
        `${environment.apiUrl}/api/auth/esqueci-senha`,
        { whatsapp }
      )
    );
  }

  /** Troca a senha e já inicia a sessão com o token devolvido. */
  async redefinirSenha(whatsapp: string, codigo: string, novaSenha: string): Promise<AuthPayload> {
    const payload = await firstValueFrom(
      this.http.post<AuthPayload>(`${environment.apiUrl}/api/auth/redefinir-senha`, {
        whatsapp,
        codigo,
        novaSenha,
      })
    );
    this.iniciarSessao(payload);
    return payload;
  }

  statusVerificacao(): Promise<VerificacaoStatus> {
    return firstValueFrom(
      this.http.get<VerificacaoStatus>(`${environment.apiUrl}/api/auth/verificar-status`, {
        headers: new HttpHeaders({ Authorization: `Bearer ${this.token ?? ''}` }),
      })
    );
  }

  marcarVerificado(): void {
    const atual = this.usuario.value;
    if (atual && !atual.telefoneVerificado) {
      this.iniciarSessao({ ...atual, telefoneVerificado: true, codigoVerificacao: null });
    }
  }

  iniciarSessao(payload: AuthPayload): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    this.emitir(payload);
  }

  sair(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.emitir(null);
  }

  // A resposta HTTP resolve fora da zona do Angular; emitir dentro dela garante que as telas re-renderizem.
  private emitir(usuario: AuthPayload | null): void {
    this.zone.run(() => this.usuario.next(usuario));
  }

  private lerStorage(): AuthPayload | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthPayload;
    } catch {
      return null;
    }
  }
}
