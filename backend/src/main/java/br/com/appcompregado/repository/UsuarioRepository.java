package br.com.appcompregado.repository;

import br.com.appcompregado.domain.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByWhatsapp(String whatsapp);

    boolean existsByWhatsapp(String whatsapp);

    /** Lista: o código não é único entre usuários; quem decide é o número do remetente. */
    List<Usuario> findByCodigoVerificacaoAndTelefoneVerificadoFalse(String codigoVerificacao);
}
