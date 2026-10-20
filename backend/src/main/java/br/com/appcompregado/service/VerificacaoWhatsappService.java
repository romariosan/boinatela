package br.com.appcompregado.service;

import br.com.appcompregado.config.WhatsappProperties;
import br.com.appcompregado.domain.CodigoOtp;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.repository.CodigoOtpRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.web.dto.EsqueciSenhaResponse;
import br.com.appcompregado.web.dto.VerificacaoStatusResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Conta já existente e ainda sem WhatsApp confirmado recebe um código de 6 dígitos no próprio número.
 */
@Service
public class VerificacaoWhatsappService {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoWhatsappService.class);
    private static final Pattern CODIGO = Pattern.compile("\\bCG-(\\d{6})\\b", Pattern.CASE_INSENSITIVE);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration VALIDADE = Duration.ofMinutes(10);
    private static final Duration INTERVALO_REENVIO = Duration.ofSeconds(60);
    private static final int MAXIMO_POR_HORA = 5;
    private static final int MAXIMO_TENTATIVAS = 5;

    private final UsuarioRepository usuarioRepository;
    private final WhatsappProperties propriedades;
    private final ReivindicacaoService reivindicacao;
    private final CodigoOtpRepository codigos;
    private final PasswordEncoder passwordEncoder;
    private final WhatsAppSystemService chipSistema;

    public VerificacaoWhatsappService(
            UsuarioRepository usuarioRepository,
            WhatsappProperties propriedades,
            ReivindicacaoService reivindicacao,
            CodigoOtpRepository codigos,
            PasswordEncoder passwordEncoder,
            WhatsAppSystemService chipSistema) {
        this.usuarioRepository = usuarioRepository;
        this.propriedades = propriedades;
        this.reivindicacao = reivindicacao;
        this.codigos = codigos;
        this.passwordEncoder = passwordEncoder;
        this.chipSistema = chipSistema;
    }

    static String gerarCodigo() {
        return "CG-%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    static Optional<String> extrairCodigo(String mensagem) {
        if (mensagem == null) {
            return Optional.empty();
        }
        Matcher matcher = CODIGO.matcher(mensagem);
        return matcher.find() ? Optional.of("CG-" + matcher.group(1)) : Optional.empty();
    }

    /** Contas criadas antes da verificação existir não têm código; gera na primeira vez que precisarem. */
    public void garantirCodigo(Usuario usuario) {
        if (!usuario.isTelefoneVerificado() && usuario.getCodigoVerificacao() == null) {
            usuario.setCodigoVerificacao(gerarCodigo());
        }
    }

    @Transactional
    public VerificacaoStatusResponse status(Usuario autenticado) {
        Usuario usuario = carregar(autenticado);
        garantirCodigo(usuario);
        String numeroBot = propriedades.getBotNumero().replaceAll("\\D", "");
        return new VerificacaoStatusResponse(
                usuario.isTelefoneVerificado(),
                usuario.getCodigoVerificacao(),
                numeroBot.isEmpty() ? null : numeroBot);
    }

    /** Manda um código de 6 dígitos para o WhatsApp da conta logada. */
    @Transactional
    public EsqueciSenhaResponse solicitarCodigo(Usuario autenticado) {
        Usuario usuario = carregar(autenticado);
        if (usuario.isTelefoneVerificado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WhatsApp já confirmado.");
        }
        String telefone = usuario.getWhatsapp();
        LocalDateTime agora = LocalDateTime.now();
        codigos.findFirstByTelefoneOrderByDataCriacaoDesc(telefone).ifPresent(ultimo -> {
            if (ultimo.getDataCriacao().plus(INTERVALO_REENVIO).isAfter(agora)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde para pedir outro código.");
            }
        });
        if (codigos.countByTelefoneAndDataCriacaoAfter(telefone, agora.minusHours(1)) >= MAXIMO_POR_HORA) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitos pedidos. Tente mais tarde.");
        }
        String codigo = "%06d".formatted(RANDOM.nextInt(1_000_000));
        codigos.invalidarAbertos(telefone);
        CodigoOtp registro = new CodigoOtp();
        registro.setTelefone(telefone);
        registro.setCodigoHash(passwordEncoder.encode(codigo));
        registro.setDataCriacao(agora);
        registro.setDataExpiracao(agora.plus(VALIDADE));
        codigos.save(registro);
        chipSistema.enviar(telefone,
                "CompreGado: seu código é " + codigo
                        + ". Ele vale por 10 minutos. Se não foi você, ignore esta mensagem.");
        return new EsqueciSenhaResponse(VALIDADE.toSeconds(), INTERVALO_REENVIO.toSeconds());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public VerificacaoStatusResponse confirmarCodigo(Usuario autenticado, String codigo) {
        Usuario usuario = carregar(autenticado);
        if (usuario.isTelefoneVerificado()) {
            return status(usuario);
        }
        CodigoOtp registro = codigos.findFirstByTelefoneAndUtilizadoFalseOrderByDataCriacaoDesc(usuario.getWhatsapp())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Peça um código novo."));
        if (registro.getDataExpiracao().isBefore(LocalDateTime.now())
                || registro.getTentativas() >= MAXIMO_TENTATIVAS) {
            registro.setUtilizado(true);
            throw new ResponseStatusException(HttpStatus.GONE, "Código expirado. Peça outro.");
        }
        if (codigo == null || !passwordEncoder.matches(codigo.trim(), registro.getCodigoHash())) {
            registro.setTentativas(registro.getTentativas() + 1);
            if (registro.getTentativas() >= MAXIMO_TENTATIVAS) {
                registro.setUtilizado(true);
                throw new ResponseStatusException(HttpStatus.GONE, "Código expirado. Peça outro.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorreto.");
        }
        registro.setUtilizado(true);
        usuario.setTelefoneVerificado(true);
        usuario.setCodigoVerificacao(null);
        reivindicacao.vincularVerificados(usuario);
        log.info("WhatsApp verificado para o usuário {}.", usuario.getId());
        return status(usuario);
    }

    private Usuario carregar(Usuario autenticado) {
        return usuarioRepository.findById(autenticado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @Transactional
    public boolean processarMensagem(String remetente, String mensagem) {
        Optional<String> codigo = extrairCodigo(mensagem);
        if (codigo.isEmpty()) {
            return false;
        }
        Optional<Usuario> dono = usuarioRepository.findByCodigoVerificacaoAndTelefoneVerificadoFalse(codigo.get())
                .stream()
                .filter(usuario -> TelefoneBr.mesmoNumero(usuario.getWhatsapp(), remetente))
                .findFirst();
        if (dono.isEmpty()) {
            log.info("Código de verificação sem conta correspondente ao remetente.");
            return false;
        }
        Usuario usuario = dono.get();
        usuario.setTelefoneVerificado(true);
        usuario.setCodigoVerificacao(null);
        reivindicacao.vincularVerificados(usuario);
        log.info("WhatsApp verificado para o usuário {}.", usuario.getId());
        return true;
    }
}
