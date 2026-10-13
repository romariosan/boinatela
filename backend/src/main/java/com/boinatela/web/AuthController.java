package com.boinatela.web;

import com.boinatela.domain.Usuario;
import com.boinatela.service.AuthService;
import com.boinatela.service.RecuperacaoSenhaService;
import com.boinatela.service.VerificacaoWhatsappService;
import com.boinatela.web.dto.AuthRequest;
import com.boinatela.web.dto.AuthResponse;
import com.boinatela.web.dto.EsqueciSenhaRequest;
import com.boinatela.web.dto.EsqueciSenhaResponse;
import com.boinatela.web.dto.LoginRequest;
import com.boinatela.web.dto.RedefinirSenhaRequest;
import com.boinatela.web.dto.VerificacaoStatusResponse;
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
