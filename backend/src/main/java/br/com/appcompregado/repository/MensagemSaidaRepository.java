package br.com.appcompregado.repository;

import br.com.appcompregado.domain.MensagemSaida;
import br.com.appcompregado.domain.enums.ChipMensagem;
import br.com.appcompregado.domain.enums.StatusMensagem;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MensagemSaidaRepository extends JpaRepository<MensagemSaida, Long> {

    List<MensagemSaida> findByStatusAndExpiraEmAfterOrderByIdAsc(
            StatusMensagem status, LocalDateTime agora, Pageable pageable);

    List<MensagemSaida> findByStatusAndChipAndExpiraEmAfterOrderByIdAsc(
            StatusMensagem status, ChipMensagem chip, LocalDateTime agora, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MensagemSaida m set m.status = br.com.appcompregado.domain.enums.StatusMensagem.EXPIRADA
            where m.status = br.com.appcompregado.domain.enums.StatusMensagem.PENDENTE and m.expiraEm <= :agora
            """)
    int expirarPendentes(@Param("agora") LocalDateTime agora);
}
