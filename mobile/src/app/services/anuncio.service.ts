import {
  HttpClient,
  HttpEventType,
  HttpHeaders,
  HttpParams,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { filter, firstValueFrom, lastValueFrom, map, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Anuncio,
  AnuncioFiltro,
  AnuncioStatus,
  CategoriaEstoque,
  ContatoVendedor,
  NovoAnuncio,
} from '../models/anuncio';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class AnuncioService {
  constructor(
    private readonly http: HttpClient,
    private readonly auth: AuthService
  ) {}

  listar(filtro: AnuncioFiltro = {}): Promise<Anuncio[]> {
    let params = new HttpParams();
    if (filtro.q) {
      params = params.set('q', filtro.q);
    }
    if (filtro.cidade) {
      params = params.set('cidade', filtro.cidade);
    }
    if (filtro.uf) {
      params = params.set('uf', filtro.uf);
    }
    if (filtro.categoria) {
      params = params.set('categoria', filtro.categoria);
    }
    if (filtro.aptidao) {
      params = params.set('aptidao', filtro.aptidao);
    }
    if (filtro.raca) {
      params = params.set('raca', filtro.raca);
    }
    return firstValueFrom(
      this.http.get<Anuncio[]>(`${environment.apiUrl}/api/anuncios`, { params })
    );
  }

  meus(): Promise<Anuncio[]> {
    return firstValueFrom(
      this.http.get<Anuncio[]>(`${environment.apiUrl}/api/anuncios/meus`, {
        headers: this.authHeaders(),
      })
    );
  }

  buscar(id: number): Promise<Anuncio> {
    return firstValueFrom(
      this.http.get<Anuncio>(`${environment.apiUrl}/api/anuncios/${id}`)
    );
  }

  categorias(): Promise<CategoriaEstoque[]> {
    return firstValueFrom(
      this.http.get<CategoriaEstoque[]>(`${environment.apiUrl}/api/categorias`)
    );
  }

  cidades(): Promise<string[]> {
    return firstValueFrom(this.http.get<string[]>(`${environment.apiUrl}/api/cidades`));
  }

  /** @param aoProgredir percentual (0–100) do envio; as fotos dominam o tempo em conexão lenta. */
  criar(dados: NovoAnuncio, imagens: File[], aoProgredir?: (percentual: number) => void): Promise<Anuncio> {
    return this.enviar('POST', `${environment.apiUrl}/api/anuncios`, this.multipart(dados, imagens), aoProgredir);
  }

  atualizar(
    id: number,
    dados: NovoAnuncio,
    novasImagens: File[],
    aoProgredir?: (percentual: number) => void
  ): Promise<Anuncio> {
    return this.enviar(
      'PUT',
      `${environment.apiUrl}/api/anuncios/${id}`,
      this.multipart(dados, novasImagens),
      aoProgredir
    );
  }

  private enviar(
    metodo: 'POST' | 'PUT',
    url: string,
    corpo: FormData,
    aoProgredir?: (percentual: number) => void
  ): Promise<Anuncio> {
    const requisicao = new HttpRequest<FormData>(metodo, url, corpo, {
      headers: this.authHeaders(),
      reportProgress: true,
    });
    return lastValueFrom(
      this.http.request<Anuncio>(requisicao).pipe(
        tap(evento => {
          if (evento.type === HttpEventType.UploadProgress && evento.total) {
            aoProgredir?.(Math.round((100 * evento.loaded) / evento.total));
          }
        }),
        filter((evento): evento is HttpResponse<Anuncio> => evento.type === HttpEventType.Response),
        map(resposta => resposta.body as Anuncio)
      )
    );
  }

  excluir(id: number): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`${environment.apiUrl}/api/anuncios/${id}`, { headers: this.authHeaders() })
    );
  }

  alterarStatus(id: number, status: AnuncioStatus): Promise<Anuncio> {
    return firstValueFrom(
      this.http.patch<Anuncio>(
        `${environment.apiUrl}/api/anuncios/${id}/status`,
        { status },
        { headers: this.authHeaders() }
      )
    );
  }

  // Sem Content-Type manual: o navegador precisa gerar o boundary do multipart.
  private multipart(dados: NovoAnuncio, imagens: File[]): FormData {
    const form = new FormData();
    for (const imagem of imagens) {
      form.append('imagens', imagem, imagem.name);
    }
    form.append('dados', new Blob([JSON.stringify(dados)], { type: 'application/json' }));
    return form;
  }

  contato(id: number): Promise<ContatoVendedor> {
    return firstValueFrom(
      this.http.post<ContatoVendedor>(
        `${environment.apiUrl}/api/anuncios/${id}/contato`,
        {},
        { headers: this.authHeaders() }
      )
    );
  }

  private authHeaders(): HttpHeaders {
    const token = this.auth.token;
    return new HttpHeaders({
      Authorization: token ? `Bearer ${token}` : '',
    });
  }
}
