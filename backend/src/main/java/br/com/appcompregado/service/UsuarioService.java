package br.com.appcompregado.service;

import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.web.dto.AlterarSenhaRequest;
import br.com.appcompregado.web.dto.AuthResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsuarioService {

    /** Quem tem um token roubado não pode ficar chutando a senha atual para tomar a conta. */
    static final int MAXIMO_TENTATIVAS_SENHA_ATUAL = 5;
    static final Duration BLOQUEIO_TROCA_SENHA = Duration.ofMinutes(15);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, AuthService authService) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    /**
     * 422 = senha atual errada (não é 401: o token é válido e o app não deve encerrar a sessão);
     * 429 = errou {@value #MAXIMO_TENTATIVAS_SENHA_ATUAL} vezes seguidas, bloqueado por 15 minutos;
     * 400 = confirmação diferente ou senha nova igual à atual.
     * Devolve um token novo: os anteriores, deste e de outros aparelhos, deixam de valer.
     * noRollbackFor: o erro contado precisa ficar gravado mesmo com a exceção.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse alterarSenha(Usuario autenticado, AlterarSenhaRequest request) {
        Usuario usuario = usuarios.findById(autenticado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        LocalDateTime agora = LocalDateTime.now();

        LocalDateTime bloqueadaAte = usuario.getTrocaSenhaBloqueadaAte();
        if (bloqueadaAte != null && bloqueadaAte.isAfter(agora)) {
            throw new TrocaSenhaBloqueadaException(minutosAte(agora, bloqueadaAte));
        }

        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenha())) {
            int erros = usuario.getTentativasSenhaAtual() + 1;
            if (erros >= MAXIMO_TENTATIVAS_SENHA_ATUAL) {
                usuario.setTentativasSenhaAtual(0);
                usuario.setTrocaSenhaBloqueadaAte(agora.plus(BLOQUEIO_TROCA_SENHA));
                throw new TrocaSenhaBloqueadaException(BLOQUEIO_TROCA_SENHA.toMinutes());
            }
            usuario.setTentativasSenhaAtual(erros);
            throw new SenhaAtualIncorretaException(MAXIMO_TENTATIVAS_SENHA_ATUAL - erros);
        }
        usuario.setTentativasSenhaAtual(0);
        usuario.setTrocaSenhaBloqueadaAte(null);

        if (!request.novaSenha().equals(request.confirmarNovaSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A confirmação não confere com a nova senha.");
        }
        if (passwordEncoder.matches(request.novaSenha(), usuario.getSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nova senha precisa ser diferente da atual.");
        }

        usuario.setSenha(passwordEncoder.encode(request.novaSenha()));
        usuario.encerrarSessoes();
        return authService.toResponse(usuario);
    }

    private static long minutosAte(LocalDateTime agora, LocalDateTime fim) {
        long segundos = Duration.between(agora, fim).toSeconds();
        return Math.max(1, (segundos + 59) / 60);
    }
}
