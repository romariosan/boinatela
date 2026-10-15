package br.com.appcompregado.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sem isto, o resolver padrão do Spring registra no log em WARN a mensagem da validação, que traz o
 * "rejected value" de cada campo: uma senha recusada (curta, por exemplo) iria em texto puro para o log.
 */
@RestControllerAdvice
public class ValidacaoHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, List<String>> camposInvalidos(MethodArgumentNotValidException erro) {
        List<String> campos = erro.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .toList();
        return Map.of("camposInvalidos", campos);
    }
}
