package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.CodigoOtp;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.CodigoOtpRepository;
import br.com.appcompregado.web.dto.EsqueciSenhaResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Código de 6 dígitos do Chip 1 para cadastrar ou reivindicar um lote capturado. */
@Service
public class OtpCadastroService {

    static final Duration VALIDADE = Duration.ofMinutes(10);
    static final Duration INTERVALO_REENVIO = Duration.ofSeconds(60);
    static final int MAXIMO_POR_HORA = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CodigoOtpRepository codigos;
    private final AnuncioRepository anuncios;
    private final PasswordEncoder passwordEncoder;
    private final WhatsAppSystemService chipSistema;

    public OtpCadastroService(
            CodigoOtpRepository codigos,
            AnuncioRepository anuncios,
            PasswordEncoder passwordEncoder,
            WhatsAppSystemService chipSistema) {
        this.codigos = codigos;
        this.anuncios = anuncios;
        this.passwordEncoder = passwordEncoder;
        this.chipSistema = chipSistema;
    }

    /** Se o envio falhar, a transação desfaz o código: dá para pedir de novo na hora. */
    @Transactional
    public EsqueciSenhaResponse enviar(String whatsappInformado, Long anuncioId) {
        String telefone = AuthService.normalizarWhatsapp(whatsappInformado);
        if (!PhoneUtils.celularBrasileiro(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um celular com DDD.");
        }
        if (anuncioId != null) {
            conferirAnuncio(anuncioId, telefone);
        }

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
        registro.setAnuncioId(anuncioId);
        registro.setDataCriacao(agora);
        registro.setDataExpiracao(agora.plus(VALIDADE));
        codigos.save(registro);

        chipSistema.enviar(telefone,
                "CompreGado: seu código é " + codigo
                        + ". Ele vale por 10 minutos. Se não foi você, ignore esta mensagem.");
        return new EsqueciSenhaResponse(VALIDADE.toSeconds(), INTERVALO_REENVIO.toSeconds());
    }

    private void conferirAnuncio(Long anuncioId, String telefone) {
        Anuncio anuncio = anuncios.findById(anuncioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anúncio não encontrado"));
        if (anuncio.getDataCaptura() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este anúncio não veio de uma captura.");
        }
        if (anuncio.isReivindicado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este anúncio já foi reivindicado.");
        }
        if (!PhoneUtils.mesmoNumero(anuncio.getTelefoneVendedorOriginal(), telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este WhatsApp não é o do anúncio.");
        }
    }
}
