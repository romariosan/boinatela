package br.com.appcompregado.repository;

import br.com.appcompregado.domain.Usuario;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByWhatsapp(String whatsapp);

    boolean existsByWhatsapp(String whatsapp);

    List<Usuario> findByIsPremiumTrueAndDataExpiracaoPremiumBefore(LocalDate data);

    /** Lista: o código não é único entre usuários; quem decide é o número do remetente. */
    List<Usuario> findByCodigoVerificacaoAndTelefoneVerificadoFalse(String codigoVerificacao);

    /**
     * SELECT ... FOR UPDATE até o fim da transação: dois envios ao mesmo tempo (fila offline, toque duplo)
     * contam os anúncios um depois do outro e não passam juntos do limite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> travarPorId(@Param("id") Long id);
}
