package br.com.appcompregado.repository;

import br.com.appcompregado.domain.MensagemSaida;
import br.com.appcompregado.domain.enums.ChipMensagem;
import br.com.appcompregado.domain.enums.StatusMensagem;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MensagemSaidaRepository extends JpaRepository<MensagemSaida, Long> {

    List<MensagemSaida> findByStatusAndExpiraEmAfterOrderByIdAsc(
            StatusMensagem status, LocalDateTime agora, Pageable pageable);

    List<MensagemSaida> findByStatusAndChipAndExpiraEmAfterOrderByIdAsc(
            StatusMensagem status, ChipMensagem chip, LocalDateTime agora, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from MensagemSaida m
            where m.status = br.com.appcompregado.domain.enums.StatusMensagem.PENDENTE
              and m.expiraEm > :agora
              and (m.dataAgendamento is null or m.dataAgendamento <= :agora)
            order by m.id asc
            """)
    List<MensagemSaida> reservaveis(@Param("agora") LocalDateTime agora, Pageable pagina);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from MensagemSaida m
            where m.status = br.com.appcompregado.domain.enums.StatusMensagem.PENDENTE
              and m.chip = :chip
              and m.expiraEm > :agora
              and (m.dataAgendamento is null or m.dataAgendamento <= :agora)
            order by m.id asc
            """)
    List<MensagemSaida> reservaveisDoChip(
            @Param("chip") ChipMensagem chip, @Param("agora") LocalDateTime agora, Pageable pagina);

    boolean existsByLeadIdAndStatusIn(Long leadId, Collection<StatusMensagem> status);

    @Modifying(flushAutomatically = true)
    @Query("""
            update MensagemSaida m
            set m.status = br.com.appcompregado.domain.enums.StatusMensagem.EXPIRADA
            where m.lead.id = :leadId
              and m.status in (
                br.com.appcompregado.domain.enums.StatusMensagem.PENDENTE,
                br.com.appcompregado.domain.enums.StatusMensagem.RESERVADA)
            """)
    int cancelarDaConversa(@Param("leadId") Long leadId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update MensagemSaida m
            set m.status = br.com.appcompregado.domain.enums.StatusMensagem.EXPIRADA
            where m.telefone = :telefone
              and m.chip = br.com.appcompregado.domain.enums.ChipMensagem.CONSULTOR
              and m.status in (
                br.com.appcompregado.domain.enums.StatusMensagem.PENDENTE,
                br.com.appcompregado.domain.enums.StatusMensagem.RESERVADA)
            """)
    int cancelarConsultor(@Param("telefone") String telefone);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MensagemSaida m set m.status = br.com.appcompregado.domain.enums.StatusMensagem.EXPIRADA
            where m.status = br.com.appcompregado.domain.enums.StatusMensagem.PENDENTE and m.expiraEm <= :agora
            """)
    int expirarPendentes(@Param("agora") LocalDateTime agora);
}
