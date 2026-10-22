import {
  FetchBackend,
  HttpBackend,
  HttpErrorResponse,
  HttpEvent,
  HttpHeaders,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Capacitor, CapacitorHttp } from '@capacitor/core';
import { Observable, from } from 'rxjs';

/**
 * Parte do multipart no formato que o CapacitorHttp nativo (Android) grava.
 * `string` vira campo de texto; `base64File` vira arquivo (fotos).
 */
interface ParteFormulario {
  type: 'string' | 'base64File';
  key: string;
  value: string;
  fileName?: string;
  contentType?: string;
}

/**
 * No Android, o patch do Capacitor desvia o fetch para
 * https://compregado.local/_capacitor_http_interceptor_. Esse host não existe na rede,
 * a WebView devolve status 0 e a vitrine aparece como sem internet.
 * JSON, GET e o multipart de anúncio saem direto para a URL da API, sem Origin.
 * No navegador, o fetch (e o proxy do ng serve) continua.
 */
@Injectable()
export class CapacitorHttpBackend implements HttpBackend {
  constructor(private readonly web: FetchBackend) {}

  handle(req: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
    if (!Capacitor.isNativePlatform()) {
      return this.web.handle(req);
    }
    return from(this.enviar(req));
  }

  private async enviar(req: HttpRequest<unknown>): Promise<HttpResponse<unknown>> {
    const formulario = req.body instanceof FormData ? await this.partesFormulario(req.body) : null;
    const headers = this.cabecalhos(req, formulario != null);
    if (formulario) {
      // Sem boundary aqui: o nativo gera um e monta o multipart. O fetch da WebView não entra.
      headers['Content-Type'] = 'multipart/form-data';
    }
    try {
      const resposta = await CapacitorHttp.request({
        url: req.urlWithParams,
        method: req.method,
        headers,
        data: formulario ?? req.body ?? undefined,
        dataType: formulario ? 'formData' : undefined,
        responseType: 'text',
      });
      const tipo = resposta.headers['Content-Type'] || resposta.headers['content-type'] || '';
      let body: unknown = resposta.data;
      if (typeof body === 'string' && body.length > 0 && (tipo.includes('json') || body.trim().startsWith('{'))) {
        try {
          body = JSON.parse(body);
        } catch {
          body = resposta.data;
        }
      }
      if (resposta.status < 200 || resposta.status >= 300) {
        throw new HttpErrorResponse({
          error: body,
          headers: new HttpHeaders(resposta.headers),
          status: resposta.status,
          statusText: String(resposta.status),
          url: resposta.url || req.urlWithParams,
        });
      }
      return new HttpResponse({
        body,
        headers: new HttpHeaders(resposta.headers),
        status: resposta.status,
        statusText: String(resposta.status),
        url: resposta.url || req.urlWithParams,
      });
    } catch (erro) {
      if (erro instanceof HttpErrorResponse) {
        throw erro;
      }
      const mensagem = erro instanceof Error ? erro.message : String(erro);
      throw new HttpErrorResponse({
        error: mensagem,
        status: 0,
        statusText: mensagem,
        url: req.urlWithParams,
      });
    }
  }

  /**
   * O plugin nativo não aceita FormData. As fotos vão em base64; `dados` (JSON do anúncio)
   * continua o campo de texto que o backend lê em @RequestPart("dados").
   */
  private async partesFormulario(form: FormData): Promise<ParteFormulario[]> {
    const partes: ParteFormulario[] = [];
    for (const [key, value] of form.entries()) {
      if (typeof value === 'string') {
        partes.push({ type: 'string', key, value });
        continue;
      }
      const tipo = value.type || 'application/octet-stream';
      if (key === 'dados' || tipo.includes('json') || tipo.startsWith('text/')) {
        partes.push({ type: 'string', key, value: await value.text() });
        continue;
      }
      const nome = value instanceof File ? value.name : 'foto.jpg';
      partes.push({
        type: 'base64File',
        key,
        value: await this.blobParaBase64(value),
        fileName: nome.replace(/["\r\n]/g, '') || 'foto.jpg',
        contentType: tipo,
      });
    }
    return partes;
  }

  private async blobParaBase64(blob: Blob): Promise<string> {
    const bytes = new Uint8Array(await blob.arrayBuffer());
    let binario = '';
    const passo = 0x8000;
    for (let i = 0; i < bytes.length; i += passo) {
      binario += String.fromCharCode(...bytes.subarray(i, i + passo));
    }
    return btoa(binario);
  }

  private cabecalhos(req: HttpRequest<unknown>, ignorarContentType: boolean): Record<string, string> {
    const headers: Record<string, string> = {};
    for (const nome of req.headers.keys()) {
      const chave = nome.toLowerCase();
      if (chave === 'origin' || (ignorarContentType && chave === 'content-type')) {
        continue;
      }
      const valores = req.headers.getAll(nome);
      if (valores?.length) {
        headers[nome] = valores.join(', ');
      }
    }
    return headers;
  }
}
