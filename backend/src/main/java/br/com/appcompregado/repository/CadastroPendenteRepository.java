package br.com.appcompregado.repository;

import br.com.appcompregado.domain.CadastroPendente;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CadastroPendenteRepository extends JpaRepository<CadastroPendente, Long> {

    Optional<CadastroPendente> findFirstByWhatsappAndUtilizadoFalseOrderByDataCriacaoDesc(String whatsapp);

    Optional<CadastroPendente> findFirstByWhatsappOrderByDataCriacaoDesc(String whatsapp);

    long countByWhatsappAndDataCriacaoAfter(String whatsapp, LocalDateTime desde);
}
