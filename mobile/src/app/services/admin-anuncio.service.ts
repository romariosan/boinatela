import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

export interface AdminAnuncio {
  id: number;
  titulo: string;
  descricao?: string;
  preco?: number;
  precoFormatado: string;
  cidade?: string;
  quantidade?: number;
  categoriaAnimal?: string;
  aptidao?: string;
  raca?: string;
  urlFotoPrincipal?: string;
  fotos?: string[];
  telefoneVendedor?: string;
  vendedorNome?: string;
  statusPublicacao: string;
  statusContato: string;
  statusProcessamento?: string;
  /** false quando sem foto (ou cidade vazia / SEM_CIDADE legado). Local a combinar vale. */
  aptoVitrine?: boolean;
  mensagemWhatsapp: string;
}

export interface AdminAnuncioEditar {
  titulo?: string;
  descricao?: string;
  preco?: number;
  cidade?: string;
  quantidade?: number | null;
  categoriaAnimal?: string | null;
  aptidao?: string | null;
  raca?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AdminAnuncioService {
  constructor(
    private readonly http: HttpClient,
    private readonly auth: AuthService
  ) {}

  listarPendentes(): Promise<AdminAnuncio[]> {
    return firstValueFrom(
      this.http.get<AdminAnuncio[]>(`${environment.apiUrl}/api/admin/anuncios/pendentes`, {
        headers: this.headers(),
      })
    );
  }

  listarProspeccao(): Promise<AdminAnuncio[]> {
    return firstValueFrom(
      this.http.get<AdminAnuncio[]>(`${environment.apiUrl}/api/admin/anuncios/prospeccao`, {
        headers: this.headers(),
      })
    );
  }

  aprovar(id: number): Promise<AdminAnuncio> {
    return firstValueFrom(
      this.http.patch<AdminAnuncio>(
        `${environment.apiUrl}/api/admin/anuncios/${id}/publicacao`,
        { status: 'APROVADO' },
        { headers: this.headers() }
      )
    );
  }

  rejeitar(id: number): Promise<AdminAnuncio> {
    return firstValueFrom(
      this.http.patch<AdminAnuncio>(
        `${environment.apiUrl}/api/admin/anuncios/${id}/publicacao`,
        { status: 'REJEITADO' },
        { headers: this.headers() }
      )
    );
  }

  marcarContatado(id: number): Promise<AdminAnuncio> {
    return firstValueFrom(
      this.http.patch<AdminAnuncio>(
        `${environment.apiUrl}/api/admin/anuncios/${id}/contato`,
        { status: 'CONTATADO' },
        { headers: this.headers() }
      )
    );
  }

  editar(id: number, body: AdminAnuncioEditar): Promise<AdminAnuncio> {
    return firstValueFrom(
      this.http.patch<AdminAnuncio>(
        `${environment.apiUrl}/api/admin/anuncios/${id}`,
        body,
        { headers: this.headers() }
      )
    );
  }

  /** wa.me com DDI 55 e mensagem honesta do CompreGado. */
  urlWhatsapp(item: AdminAnuncio): string | null {
    const bruto = (item.telefoneVendedor ?? '').replace(/\D/g, '');
    if (bruto.length < 10) {
      return null;
    }
    const comDdi = bruto.startsWith('55') ? bruto : `55${bruto}`;
    const texto = item.mensagemWhatsapp || 'Olá! Aqui é da equipe do CompreGado.';
    return `https://wa.me/${comDdi}?text=${encodeURIComponent(texto)}`;
  }

  private headers(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.auth.token ?? ''}` });
  }
}
