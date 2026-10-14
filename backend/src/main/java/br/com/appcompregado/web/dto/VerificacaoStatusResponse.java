package br.com.appcompregado.web.dto;

/**
 * {@code codigoVerificacao} é nulo depois de verificado. {@code whatsappBot} (DDI + número) é nulo se a API
 * não tiver WHATSAPP_BOT_NUMERO; o app usa então o número do environment.
 */
public record VerificacaoStatusResponse(boolean telefoneVerificado, String codigoVerificacao, String whatsappBot) {
}
