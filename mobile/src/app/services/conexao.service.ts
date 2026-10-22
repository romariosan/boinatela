import { Injectable, NgZone } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, distinctUntilChanged } from 'rxjs';

/**
 * Só é falta de internet quando o aparelho está offline e a resposta nem chegou (status 0).
 * Com sinal, status 0 é CORS, SSL ou falha da API: a tela mostra erro do servidor.
 */
export function pedidoNaoSaiu(erro: unknown): boolean {
  return erro instanceof HttpErrorResponse && erro.status === 0 && navigator.onLine === false;
}

function textoHttp(erro: HttpErrorResponse): string {
  const partes = [erro.message ?? '', erro.statusText ?? ''];
  const corpo = erro.error;
  if (typeof corpo === 'string') {
    partes.push(corpo);
  } else if (corpo && typeof corpo === 'object') {
    const registro = corpo as Record<string, unknown>;
    for (const chave of ['message', 'mensagem', 'detail', 'error', 'title', 'erro']) {
      const valor = registro[chave];
      if (typeof valor === 'string') {
        partes.push(valor);
      }
    }
  }
  return partes.join(' ');
}

/** 403 com frase da API ("não é seu", confirmar lote). Não é CORS nem token inválido. */
function regraDeNegocio(erro: HttpErrorResponse): boolean {
  const texto = textoHttp(erro).toLowerCase();
  return texto.includes('não é seu') || texto.includes('nao e seu') || texto.includes('confirmar este lote');
}

function corpoVazio(erro: HttpErrorResponse): boolean {
  const corpo = erro.error;
  return corpo == null || (typeof corpo === 'string' && corpo.trim() === '');
}

/**
 * Recusa de CORS ou rede, não uma resposta de negócio da API.
 * "Invalid CORS request", status 0, ou 403 sem corpo nenhum (a WebView esconde o corpo).
 */
export function recusaCors(erro: unknown): boolean {
  if (!(erro instanceof HttpErrorResponse)) {
    return false;
  }
  if (erro.status === 0) {
    return true;
  }
  if (/invalid cors request/i.test(textoHttp(erro))) {
    return true;
  }
  return erro.status === 403 && corpoVazio(erro) && !regraDeNegocio(erro);
}

/** 401 e 403 de token/sessão. 403 de CORS ou de regra de negócio não entram. */
export function sessaoInvalida(erro: unknown): boolean {
  if (!(erro instanceof HttpErrorResponse) || recusaCors(erro)) {
    return false;
  }
  if (erro.status === 401) {
    return true;
  }
  return erro.status === 403 && !regraDeNegocio(erro);
}

/** Loga status, URL e a mensagem crua (status 0, ERR_CLEARTEXT_NOT_PERMITTED, ERR_NAME_NOT_RESOLVED, SSL). */
export function registrarFalhaHttp(contexto: string, erro: unknown): void {
  if (erro instanceof HttpErrorResponse) {
    const detalhe =
      erro.error instanceof Error ? erro.error.message : typeof erro.error === 'string' ? erro.error : '';
    console.error(
      `[HTTP] ${contexto} status=${erro.status} url=${erro.url ?? ''} mensagem=${erro.message} detalhe=${detalhe}`
    );
    return;
  }
  console.error(`[HTTP] ${contexto}`, erro);
}

/** Estado da internet do aparelho, pelos eventos online/offline da WebView. */
@Injectable({ providedIn: 'root' })
export class ConexaoService {
  private readonly online = new BehaviorSubject<boolean>(navigator.onLine);

  readonly online$: Observable<boolean> = this.online.pipe(distinctUntilChanged());

  constructor(zone: NgZone) {
    console.info(`[rede] navigator.onLine=${navigator.onLine}`);
    window.addEventListener('online', () => zone.run(() => this.online.next(true)));
    window.addEventListener('offline', () => zone.run(() => this.online.next(false)));
  }
}
