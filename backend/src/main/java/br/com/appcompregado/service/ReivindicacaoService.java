package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.CodigoOtp;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.StatusContato;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.CodigoOtpRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import java.util.Optional;
import br.com.appcompregado.web.dto.AnuncioResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReivindicacaoService {

    static final int MAXIMO_TENTATIVAS = 5;

    private final AnuncioRepository anuncios;
    private final CodigoOtpRepository codigos;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public ReivindicacaoService(
            AnuncioRepository anuncios,
            CodigoOtpRepository codigos,
            UsuarioRepository usuarios,
            PasswordEncoder passwordEncoder) {
        this.anuncios = anuncios;
        this.codigos = codigos;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 400 = código errado; 410 = expirado ou esgotado. A tentativa errada precisa ficar gravada.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AnuncioResponse reivindicar(Long anuncioId, String whatsappInformado, String otp) {
        String telefone = AuthService.normalizarWhatsapp(whatsappInformado);
        if (!PhoneUtils.celularBrasileiro(telefone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um celular com DDD.");
        }
        Anuncio anuncio = anuncios.findByIdWithVendedor(anuncioId)
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

        CodigoOtp registro = codigos.findFirstByTelefoneAndUtilizadoFalseOrderByDataCriacaoDesc(telefone)
                .filter(c -> anuncioId.equals(c.getAnuncioId()))
                .filter(c -> c.getDataExpiracao().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Código expirado. Peça um novo."));

        if (!passwordEncoder.matches(otp, registro.getCodigoHash())) {
            registro.setTentativas(registro.getTentativas() + 1);
            if (registro.getTentativas() >= MAXIMO_TENTATIVAS) {
                registro.setUtilizado(true);
                throw new ResponseStatusException(HttpStatus.GONE, "Muitas tentativas. Peça um novo código.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorreto.");
        }

        registro.setUtilizado(true);
        anuncio.setReivindicado(true);
        anuncio.setStatusContato(StatusContato.REIVINDICADO);
        localizar(telefone).ifPresent(usuario -> assumir(anuncio, usuario));
        return AnuncioResponse.from(anuncio);
    }

    private Optional<Usuario> localizar(String informado) {
        String canonico = PhoneUtils.normalizar(informado);
        if (canonico.length() < 10) {
            return Optional.empty();
        }
        Optional<Usuario> direto = usuarios.findByWhatsapp(canonico);
        if (direto.isPresent()) {
            return direto;
        }
        for (String legado : PhoneUtils.variantesLegadas(canonico)) {
            Optional<Usuario> achou = usuarios.findByWhatsapp(legado);
            if (achou.isPresent()) {
                return achou;
            }
        }
        return Optional.empty();
    }

    /** Quando o telefone fica verificado, os lotes já reivindicados passam para a conta. */
    @Transactional
    public void vincularVerificados(Usuario usuario) {
        if (usuario == null || !usuario.isTelefoneVerificado()) {
            return;
        }
        List<String> formas = new ArrayList<>();
        formas.add(PhoneUtils.normalizar(usuario.getWhatsapp()));
        formas.addAll(PhoneUtils.variantesLegadas(usuario.getWhatsapp()));
        for (Anuncio anuncio : anuncios.reivindicadosPorTelefone(formas)) {
            assumir(anuncio, usuario);
        }
    }

    private static void assumir(Anuncio anuncio, Usuario usuario) {
        if (anuncio.getVendedor() != null
                && ContaCaptura.WHATSAPP.equals(anuncio.getVendedor().getWhatsapp())) {
            anuncio.setVendedor(usuario);
        }
    }
}
