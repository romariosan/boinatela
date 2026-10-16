package br.com.appcompregado.repository;

import br.com.appcompregado.domain.CodigoOtp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodigoOtpRepository extends JpaRepository<CodigoOtp, Long> {

    Optional<CodigoOtp> findFirstByTelefoneOrderByDataCriacaoDesc(String telefone);

    Optional<CodigoOtp> findFirstByTelefoneAndUtilizadoFalseOrderByDataCriacaoDesc(String telefone);

    long countByTelefoneAndDataCriacaoAfter(String telefone, LocalDateTime desde);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CodigoOtp c set c.utilizado = true where c.telefone = :telefone and c.utilizado = false")
    int invalidarAbertos(@Param("telefone") String telefone);
}
