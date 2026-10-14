package br.com.appcompregado.service;

import br.com.appcompregado.config.WhatsappProperties;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.web.dto.VerificacaoStatusResponse;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validação reversa: o usuário manda "CG-123456" do próprio WhatsApp para o bot, e o bot repassa a
 * mensagem para o webhook. Só verifica se o remetente for o número cadastrado na conta do código.
 */
@Service
public class VerificacaoWhatsappService {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoWhatsappService.class);
    private static final Pattern CODIGO = Pattern.compile("\\bCG-(\\d{6})\\b", Pattern.CASE_INSENSITIVE);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final WhatsappProperties propriedades;

    public VerificacaoWhatsappService(UsuarioRepository usuarioRepository, WhatsappProperties propriedades) {
        this.usuarioRepository = usuarioRepository;
        this.propriedades = propriedades;
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
        Usuario usuario = usuarioRepository.findById(autenticado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        garantirCodigo(usuario);
        String numeroBot = propriedades.getBotNumero().replaceAll("\\D", "");
        return new VerificacaoStatusResponse(
                usuario.isTelefoneVerificado(),
                usuario.getCodigoVerificacao(),
                numeroBot.isEmpty() ? null : numeroBot);
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
        log.info("WhatsApp verificado para o usuário {}.", usuario.getId());
        return true;
    }
}
