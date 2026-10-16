package br.com.appcompregado.service;

import br.com.appcompregado.domain.CodigoRecuperacao;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.repository.CodigoRecuperacaoRepository;
import br.com.appcompregado.web.dto.AuthResponse;
import br.com.appcompregado.web.dto.EsqueciSenhaResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * "Esqueci minha senha": código de 4 dígitos enviado pelo bot do WhatsApp. Com só 10 mil combinações, a
 * segurança vem dos limites: 5 tentativas por código, 1 pedido por minuto e 5 por hora por número.
 */
@Service
public class RecuperacaoSenhaService {

    static final Duration VALIDADE = Duration.ofMinutes(10);
    static final Duration INTERVALO_REENVIO = Duration.ofSeconds(60);
    static final int MAXIMO_POR_HORA = 5;
    static final int MAXIMO_TENTATIVAS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CodigoRecuperacaoRepository codigos;
    private final PasswordEncoder passwordEncoder;
    private final WhatsAppSystemService chipSistema;
    private final AuthService authService;
    private final ReivindicacaoService reivindicacao;

    public RecuperacaoSenhaService(
            CodigoRecuperacaoRepository codigos,
            PasswordEncoder passwordEncoder,
            WhatsAppSystemService chipSistema,
            AuthService authService,
            ReivindicacaoService reivindicacao) {
        this.codigos = codigos;
        this.passwordEncoder = passwordEncoder;
        this.chipSistema = chipSistema;
        this.authService = authService;
        this.reivindicacao = reivindicacao;
    }

    /** Se o envio falhar, a transação desfaz o código salvo: o usuário pode pedir de novo na hora. */
    @Transactional
    public EsqueciSenhaResponse solicitar(String whatsappInformado) {
        Usuario usuario = buscarUsuario(whatsappInformado);
        LocalDateTime agora = LocalDateTime.now();

        codigos.findFirstByUsuarioOrderByDataCriacaoDesc(usuario).ifPresent(ultimo -> {
            if (ultimo.getDataCriacao().plus(INTERVALO_REENVIO).isAfter(agora)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde para pedir outro código.");
            }
        });
        if (codigos.countByUsuarioAndDataCriacaoAfter(usuario, agora.minusHours(1)) >= MAXIMO_POR_HORA) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitos pedidos. Tente mais tarde.");
        }

        String codigo = "%04d".formatted(RANDOM.nextInt(10_000));
        codigos.invalidarAbertos(usuario);
        CodigoRecuperacao registro = new CodigoRecuperacao();
        registro.setUsuario(usuario);
        registro.setWhatsapp(usuario.getWhatsapp());
        registro.setCodigoHash(passwordEncoder.encode(codigo));
        registro.setDataCriacao(agora);
        registro.setDataExpiracao(agora.plus(VALIDADE));
        codigos.save(registro);

        chipSistema.enviar(usuario.getWhatsapp(),
                "CompreGado: seu código para redefinir a senha é " + codigo
                        + ". Ele vale por 10 minutos. Se não foi você, ignore esta mensagem.");
        return new EsqueciSenhaResponse(VALIDADE.toSeconds(), INTERVALO_REENVIO.toSeconds());
    }

    /**
     * 400 = código errado (ainda dá para tentar); 410 = expirado, usado ou tentativas esgotadas.
     * noRollbackFor: a tentativa errada precisa ficar gravada mesmo com a exceção.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse redefinir(String whatsappInformado, String codigo, String novaSenha) {
        Usuario usuario = buscarUsuario(whatsappInformado);
        CodigoRecuperacao registro = codigos.findFirstByUsuarioAndUtilizadoFalseOrderByDataCriacaoDesc(usuario)
                .filter(c -> c.getDataExpiracao().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Código expirado. Peça um novo."));

        if (!passwordEncoder.matches(codigo, registro.getCodigoHash())) {
            registro.setTentativas(registro.getTentativas() + 1);
            if (registro.getTentativas() >= MAXIMO_TENTATIVAS) {
                registro.setUtilizado(true);
                throw new ResponseStatusException(HttpStatus.GONE, "Muitas tentativas. Peça um novo código.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorreto.");
        }

        registro.setUtilizado(true);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.encerrarSessoes();
        // O código chegou por WhatsApp e foi digitado no app: isso já prova que o número é do usuário.
        usuario.setTelefoneVerificado(true);
        usuario.setCodigoVerificacao(null);
        reivindicacao.vincularVerificados(usuario);
        return authService.toResponse(usuario);
    }

    private Usuario buscarUsuario(String whatsappInformado) {
        return authService.localizarPorTelefone(whatsappInformado)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Número de WhatsApp não cadastrado"));
    }
}
