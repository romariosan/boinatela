package br.com.appcompregado.service;

import br.com.appcompregado.domain.CadastroPendente;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.ChipMensagem;
import br.com.appcompregado.repository.CadastroPendenteRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.web.dto.AuthRequest;
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

/** A conta só nasce depois que o código de 6 dígitos chega no WhatsApp e a pessoa confirma. */
@Service
public class CadastroPendenteService {

    static final Duration VALIDADE = Duration.ofMinutes(10);
    static final Duration INTERVALO_REENVIO = Duration.ofSeconds(60);
    static final int MAXIMO_POR_HORA = 5;
    static final int MAXIMO_TENTATIVAS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CadastroPendenteRepository pendentes;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final FilaMensagemService fila;
    private final AuthService authService;
    private final ReivindicacaoService reivindicacao;

    public CadastroPendenteService(
            CadastroPendenteRepository pendentes,
            UsuarioRepository usuarios,
            PasswordEncoder passwordEncoder,
            FilaMensagemService fila,
            AuthService authService,
            ReivindicacaoService reivindicacao) {
        this.pendentes = pendentes;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.fila = fila;
        this.authService = authService;
        this.reivindicacao = reivindicacao;
    }

    @Transactional
    public EsqueciSenhaResponse solicitar(AuthRequest request) {
        String whatsapp = AuthService.normalizarWhatsapp(request.whatsapp());
        if (!PhoneUtils.celularBrasileiro(whatsapp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um celular com DDD.");
        }
        if (authService.localizarPorTelefone(whatsapp).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WhatsApp já cadastrado");
        }
        LocalDateTime agora = LocalDateTime.now();
        pendentes.findFirstByWhatsappOrderByDataCriacaoDesc(whatsapp).ifPresent(ultimo -> {
            if (ultimo.getDataCriacao().plus(INTERVALO_REENVIO).isAfter(agora)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde para pedir outro código.");
            }
        });
        if (pendentes.countByWhatsappAndDataCriacaoAfter(whatsapp, agora.minusHours(1)) >= MAXIMO_POR_HORA) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitos pedidos. Tente mais tarde.");
        }

        String codigo = "%06d".formatted(RANDOM.nextInt(1_000_000));
        pendentes.findFirstByWhatsappAndUtilizadoFalseOrderByDataCriacaoDesc(whatsapp)
                .ifPresent(aberto -> aberto.setUtilizado(true));

        CadastroPendente pendente = new CadastroPendente();
        pendente.setNome(request.nome().trim());
        pendente.setWhatsapp(whatsapp);
        pendente.setSenhaHash(passwordEncoder.encode(request.senha()));
        pendente.setCodigoHash(passwordEncoder.encode(codigo));
        pendente.setDataCriacao(agora);
        pendente.setDataExpiracao(agora.plus(VALIDADE));
        pendentes.save(pendente);

        fila.enfileirar(ChipMensagem.SISTEMA, whatsapp,
                "CompreGado: seu código é " + codigo
                        + ". Ele vale por 10 minutos. Se não foi você, ignore esta mensagem.",
                VALIDADE);
        return new EsqueciSenhaResponse(VALIDADE.toSeconds(), INTERVALO_REENVIO.toSeconds());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse confirmar(String whatsappInformado, String codigo) {
        String whatsapp = AuthService.normalizarWhatsapp(whatsappInformado);
        CadastroPendente pendente = pendentes.findFirstByWhatsappAndUtilizadoFalseOrderByDataCriacaoDesc(whatsapp)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Peça um código novo."));
        if (pendente.getDataExpiracao().isBefore(LocalDateTime.now())
                || pendente.getTentativas() >= MAXIMO_TENTATIVAS) {
            pendente.setUtilizado(true);
            throw new ResponseStatusException(HttpStatus.GONE, "Código expirado. Peça outro.");
        }
        if (codigo == null || !passwordEncoder.matches(codigo.trim(), pendente.getCodigoHash())) {
            pendente.setTentativas(pendente.getTentativas() + 1);
            if (pendente.getTentativas() >= MAXIMO_TENTATIVAS) {
                pendente.setUtilizado(true);
                throw new ResponseStatusException(HttpStatus.GONE, "Código expirado. Peça outro.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorreto.");
        }
        if (authService.localizarPorTelefone(whatsapp).isPresent()) {
            pendente.setUtilizado(true);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WhatsApp já cadastrado");
        }
        pendente.setUtilizado(true);
        Usuario usuario = new Usuario();
        usuario.setNome(pendente.getNome());
        usuario.setWhatsapp(whatsapp);
        usuario.setSenha(pendente.getSenhaHash());
        usuario.setPremium(false);
        usuario.setTelefoneVerificado(true);
        usuario = usuarios.save(usuario);
        reivindicacao.vincularVerificados(usuario);
        return authService.toResponse(usuario);
    }
}
