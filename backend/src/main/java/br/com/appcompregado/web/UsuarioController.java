package br.com.appcompregado.web;

import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.service.SenhaAtualIncorretaException;
import br.com.appcompregado.service.TrocaSenhaBloqueadaException;
import br.com.appcompregado.service.UsuarioService;
import br.com.appcompregado.web.dto.AlterarSenhaRequest;
import br.com.appcompregado.web.dto.AuthResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /** Devolve a sessão nova (token): o app troca a que tem guardada e continua logado. */
    @PutMapping("/alterar-senha")
    public AuthResponse alterarSenha(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody AlterarSenhaRequest request) {
        return usuarioService.alterarSenha(usuario, request);
    }

    @ExceptionHandler(SenhaAtualIncorretaException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, Integer> senhaAtualIncorreta(SenhaAtualIncorretaException erro) {
        return Map.of("tentativasRestantes", erro.getTentativasRestantes());
    }

    @ExceptionHandler(TrocaSenhaBloqueadaException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, Long> trocaBloqueada(TrocaSenhaBloqueadaException erro) {
        return Map.of("minutosRestantes", erro.getMinutosRestantes());
    }
}
