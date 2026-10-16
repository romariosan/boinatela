package br.com.appcompregado.repository;

import br.com.appcompregado.domain.LeadContato;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadContatoRepository extends JpaRepository<LeadContato, Long> {

    long countByAnuncioId(Long anuncioId);

    void deleteByAnuncioId(Long anuncioId);
}
