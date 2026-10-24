package br.com.appcompregado.repository;

import br.com.appcompregado.domain.LeadConversa;
import br.com.appcompregado.domain.enums.StatusLeadConversa;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadConversaRepository extends JpaRepository<LeadConversa, Long> {

    Optional<LeadConversa> findByAnuncioId(Long anuncioId);

    boolean existsByTelefoneAndStatus(String telefone, StatusLeadConversa status);

    @Query("""
            select c from LeadConversa c
            join fetch c.anuncio a
            left join fetch a.vendedor
            where c.id = :id
            """)
    Optional<LeadConversa> detalhe(@Param("id") Long id);

    @Query("""
            select c from LeadConversa c
            join fetch c.anuncio
            where c.telefone = :telefone
              and c.status <> br.com.appcompregado.domain.enums.StatusLeadConversa.REJEITADO
              and c.encerrada = false
            order by c.dataUltimaInteracao desc, c.id desc
            """)
    java.util.List<LeadConversa> abertas(@Param("telefone") String telefone, Pageable pagina);

    @Query("""
            select c from LeadConversa c
            join fetch c.anuncio
            where c.telefone = :telefone
              and c.status <> br.com.appcompregado.domain.enums.StatusLeadConversa.REJEITADO
            order by c.id desc
            """)
    java.util.List<LeadConversa> anteriores(@Param("telefone") String telefone, Pageable pagina);

    @Query("""
            select c from LeadConversa c
            join fetch c.anuncio
            where c.status = br.com.appcompregado.domain.enums.StatusLeadConversa.QUEBRA_GELO_ENVIADO
              and c.encerrada = false
              and c.lembreteEnviado = false
              and c.dataUltimaInteracao < :limite
            order by c.dataUltimaInteracao asc
            """)
    java.util.List<LeadConversa> paraLembrete(@Param("limite") LocalDateTime limite, Pageable pagina);
}
