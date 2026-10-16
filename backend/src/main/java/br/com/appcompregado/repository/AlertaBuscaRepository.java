package br.com.appcompregado.repository;

import br.com.appcompregado.domain.AlertaBusca;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertaBuscaRepository extends JpaRepository<AlertaBusca, Long> {

    Optional<AlertaBusca> findByWhatsappAndRacaAndCategoriaPilarAndCidadeAndAtivoTrue(
            String whatsapp, String raca, String categoriaPilar, String cidade);

    long countByWhatsappAndAtivoTrue(String whatsapp);

    @Query("""
            SELECT a FROM AlertaBusca a
            WHERE a.ativo = true
              AND (
                (a.raca <> '' AND LOWER(a.raca) = LOWER(:raca))
                OR (a.cidade <> '' AND LOWER(a.cidade) = LOWER(:cidade))
                OR (a.categoriaPilar <> '' AND LOWER(a.categoriaPilar) = LOWER(:categoria))
              )
            """)
    List<AlertaBusca> combinar(
            @Param("raca") String raca,
            @Param("cidade") String cidade,
            @Param("categoria") String categoria);
}
