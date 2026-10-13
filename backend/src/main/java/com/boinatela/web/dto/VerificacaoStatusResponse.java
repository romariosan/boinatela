package com.boinatela.web.dto;

/** {@code codigoVerificacao} é nulo depois de verificado. */
public record VerificacaoStatusResponse(boolean telefoneVerificado, String codigoVerificacao) {
}
