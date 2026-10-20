package br.com.appcompregado.web;

import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.service.AuthService;
import br.com.appcompregado.service.OtpCadastroService;
import br.com.appcompregado.service.RecuperacaoSenhaService;
import br.com.appcompregado.service.CadastroPendenteService;
import br.com.appcompregado.service.VerificacaoWhatsappService;
import br.com.appcompregado.web.dto.AuthRequest;
import br.com.appcompregado.web.dto.AuthResponse;
import br.com.appcompregado.web.dto.CodigoRequest;
import br.com.appcompregado.web.dto.ConfirmarCadastroRequest;
import br.com.appcompregado.web.dto.EsqueciSenhaRequest;
import br.com.appcompregado.web.dto.EsqueciSenhaResponse;
import br.com.appcompregado.web.dto.LoginRequest;
import br.com.appcompregado.web.dto.OtpCadastroRequest;
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
    private final OtpCadastroService otpCadastro;
    private final CadastroPendenteService cadastroPendente;

    public AuthController(
            AuthService authService,
            VerificacaoWhatsappService verificacao,
            RecuperacaoSenhaService recuperacao,
            OtpCadastroService otpCadastro,
            CadastroPendenteService cadastroPendente) {
        this.authService = authService;
        this.verificacao = verificacao;
        this.recuperacao = recuperacao;
        this.otpCadastro = otpCadastro;
        this.cadastroPendente = cadastroPendente;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse cadastrar(@Valid @RequestBody AuthRequest request) {
        return authService.cadastrar(request);
    }

    /** Guarda nome e senha e manda o código. A conta só existe depois de /cadastro/confirmar. */
    @PostMapping("/cadastro/solicitar")
    public EsqueciSenhaResponse solicitarCadastro(@Valid @RequestBody AuthRequest request) {
        return cadastroPendente.solicitar(request);
    }

    @PostMapping("/cadastro/confirmar")
    public AuthResponse confirmarCadastro(@Valid @RequestBody ConfirmarCadastroRequest request) {
        return cadastroPendente.confirmar(request.whatsapp(), request.codigo());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/esqueci-senha")
    public EsqueciSenhaResponse esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest request) {
        return recuperacao.solicitar(request.whatsapp());
    }

    /** Chip 1: código de 6 dígitos para cadastrar ou reivindicar um lote. */
    @PostMapping("/otp/cadastro-ou-reivindicar")
    public EsqueciSenhaResponse otpCadastroOuReivindicar(@Valid @RequestBody OtpCadastroRequest request) {
        return otpCadastro.enviar(request.whatsapp(), request.anuncioId());
    }

    /** Chip 1: mesmo código de 4 dígitos do esqueci-senha, pelo número do sistema. */
    @PostMapping("/otp/recuperar-senha")
    public EsqueciSenhaResponse otpRecuperarSenha(@Valid @RequestBody EsqueciSenhaRequest request) {
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

    @PostMapping("/verificar/solicitar")
    public EsqueciSenhaResponse solicitarVerificacao(@AuthenticationPrincipal Usuario usuario) {
        return verificacao.solicitarCodigo(usuario);
    }

    @PostMapping("/verificar/confirmar")
    public VerificacaoStatusResponse confirmarVerificacao(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody CodigoRequest request) {
        return verificacao.confirmarCodigo(usuario, request.codigo());
    }
}
