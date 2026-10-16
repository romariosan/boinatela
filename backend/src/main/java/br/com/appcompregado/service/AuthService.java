package br.com.appcompregado.service;

import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.security.JwtService;
import br.com.appcompregado.web.dto.AuthRequest;
import br.com.appcompregado.web.dto.AuthResponse;
import br.com.appcompregado.web.dto.LoginRequest;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificacaoWhatsappService verificacao;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            VerificacaoWhatsappService verificacao) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificacao = verificacao;
    }

    @Transactional
    public AuthResponse cadastrar(AuthRequest request) {
        String whatsapp = normalizarWhatsapp(request.whatsapp());
        if (!PhoneUtils.celularBrasileiro(whatsapp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um celular com DDD.");
        }
        if (localizarPorTelefone(whatsapp).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WhatsApp já cadastrado");
        }
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome().trim());
        usuario.setWhatsapp(whatsapp);
        usuario.setSenha(passwordEncoder.encode(request.senha()));
        usuario.setPremium(false);
        verificacao.garantirCodigo(usuario);
        usuario = usuarioRepository.save(usuario);
        return toResponse(usuario);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = localizarPorTelefone(request.whatsapp())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "WhatsApp ou senha inválidos"));
        if (!passwordEncoder.matches(request.senha(), usuario.getSenha())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "WhatsApp ou senha inválidos");
        }
        verificacao.garantirCodigo(usuario);
        return toResponse(usuario);
    }

    public static String normalizarWhatsapp(String whatsapp) {
        return PhoneUtils.normalizar(whatsapp);
    }

    /** Acha a conta pela forma canônica ou pela gravada antes do nono dígito. */
    @Transactional(readOnly = true)
    public Optional<Usuario> localizarPorTelefone(String informado) {
        String canonico = normalizarWhatsapp(informado);
        if (canonico.length() < 10) {
            return Optional.empty();
        }
        Optional<Usuario> direto = usuarioRepository.findByWhatsapp(canonico);
        if (direto.isPresent()) {
            return direto;
        }
        for (String legado : PhoneUtils.variantesLegadas(canonico)) {
            Optional<Usuario> achou = usuarioRepository.findByWhatsapp(legado);
            if (achou.isPresent()) {
                return achou;
            }
        }
        return Optional.empty();
    }

    AuthResponse toResponse(Usuario usuario) {
        String token = jwtService.gerarToken(usuario);
        return new AuthResponse(
                token,
                usuario.getId(),
                usuario.getNome(),
                usuario.getWhatsapp(),
                usuario.isTelefoneVerificado(),
                usuario.getCodigoVerificacao());
    }
}
