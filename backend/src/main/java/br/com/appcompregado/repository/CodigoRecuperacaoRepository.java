package br.com.appcompregado.repository;

import br.com.appcompregado.domain.CodigoRecuperacao;
import br.com.appcompregado.domain.Usuario;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodigoRecuperacaoRepository extends JpaRepository<CodigoRecuperacao, Long> {

    Optional<CodigoRecuperacao> findFirstByUsuarioOrderByDataCriacaoDesc(Usuario usuario);

    Optional<CodigoRecuperacao> findFirstByUsuarioAndUtilizadoFalseOrderByDataCriacaoDesc(Usuario usuario);

    long countByUsuarioAndDataCriacaoAfter(Usuario usuario, LocalDateTime desde);

    @Modifying
    @Query("update CodigoRecuperacao c set c.utilizado = true where c.usuario = :usuario and c.utilizado = false")
    int invalidarAbertos(@Param("usuario") Usuario usuario);
}
