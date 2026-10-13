package com.boinatela.service;

import com.boinatela.domain.Usuario;
import com.boinatela.repository.UsuarioRepository;
import com.boinatela.security.JwtService;
import com.boinatela.web.dto.AuthRequest;
import com.boinatela.web.dto.AuthResponse;
import com.boinatela.web.dto.LoginRequest;
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
        if (usuarioRepository.existsByWhatsapp(whatsapp)) {
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
        String whatsapp = normalizarWhatsapp(request.whatsapp());
        Usuario usuario = usuarioRepository.findByWhatsapp(whatsapp)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "WhatsApp ou senha inválidos"));
        if (!passwordEncoder.matches(request.senha(), usuario.getSenha())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "WhatsApp ou senha inválidos");
        }
        verificacao.garantirCodigo(usuario);
        return toResponse(usuario);
    }

    public static String normalizarWhatsapp(String whatsapp) {
        return whatsapp.replaceAll("\\D", "");
    }

    AuthResponse toResponse(Usuario usuario) {
        String token = jwtService.gerarToken(usuario.getId(), usuario.getWhatsapp());
        return new AuthResponse(
                token,
                usuario.getId(),
                usuario.getNome(),
                usuario.getWhatsapp(),
                usuario.isTelefoneVerificado(),
                usuario.getCodigoVerificacao());
    }
}
