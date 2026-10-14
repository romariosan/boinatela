package br.com.appcompregado.web;

import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.service.AuthService;
import br.com.appcompregado.service.RecuperacaoSenhaService;
import br.com.appcompregado.service.VerificacaoWhatsappService;
import br.com.appcompregado.web.dto.AuthRequest;
import br.com.appcompregado.web.dto.AuthResponse;
import br.com.appcompregado.web.dto.EsqueciSenhaRequest;
import br.com.appcompregado.web.dto.EsqueciSenhaResponse;
import br.com.appcompregado.web.dto.LoginRequest;
import br.com.appcompregado.web.dto.RedefinirSenhaRequest;
import br.com.appcompregado.web.dto.VerificacaoStatusResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final VerificacaoWhatsappService verificacao;
    private final RecuperacaoSenhaService recuperacao;

    public AuthController(
            AuthService authService,
            VerificacaoWhatsappService verificacao,
            RecuperacaoSenhaService recuperacao) {
        this.authService = authService;
        this.verificacao = verificacao;
        this.recuperacao = recuperacao;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse cadastrar(@Valid @RequestBody AuthRequest request) {
        return authService.cadastrar(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/esqueci-senha")
    public EsqueciSenhaResponse esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest request) {
        return recuperacao.solicitar(request.whatsapp());
    }

    /** Devolve a sessão (token) para o app já entrar logado. */
    @PostMapping("/redefinir-senha")
    public AuthResponse redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        return recuperacao.redefinir(request.whatsapp(), request.codigo(), request.novaSenha());
    }

    @GetMapping("/verificar-status")
    public VerificacaoStatusResponse verificarStatus(@AuthenticationPrincipal Usuario usuario) {
        return verificacao.status(usuario);
    }
}
