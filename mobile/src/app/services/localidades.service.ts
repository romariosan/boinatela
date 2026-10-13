import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { CIDADES_PRINCIPAIS } from '../models/localidade';

interface MunicipioIbge {
  nome: string;
}

const IBGE_MUNICIPIOS = 'https://servicodados.ibge.gov.br/api/v1/localidades/estados/{UF}/municipios';

@Injectable({ providedIn: 'root' })
export class LocalidadesService {
  private readonly cache = new Map<string, Promise<string[]>>();

  constructor(private readonly http: HttpClient) {}

  /** Municípios da UF pelo IBGE (em cache); sem resposta, cai na lista das principais cidades. */
  municipios(uf: string): Promise<string[]> {
    const emCache = this.cache.get(uf);
    if (emCache) {
      return emCache;
    }
    const pedido = firstValueFrom(
      this.http.get<MunicipioIbge[]>(IBGE_MUNICIPIOS.replace('{UF}', uf)).pipe(timeout(8000))
    )
      .then(lista => lista.map(municipio => municipio.nome).sort((a, b) => a.localeCompare(b, 'pt-BR')))
      .catch(() => {
        this.cache.delete(uf);
        return [...(CIDADES_PRINCIPAIS[uf] ?? [])];
      });
    this.cache.set(uf, pedido);
    return pedido;
  }
}
