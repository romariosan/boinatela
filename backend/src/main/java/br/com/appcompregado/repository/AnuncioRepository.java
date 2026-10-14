package br.com.appcompregado.repository;

import br.com.appcompregado.domain.Anuncio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnuncioRepository extends JpaRepository<Anuncio, Long>, JpaSpecificationExecutor<Anuncio> {

    @Query("""
            SELECT a.categoriaAnimal, COUNT(a)
            FROM Anuncio a
            WHERE a.status = br.com.appcompregado.domain.enums.AnuncioStatus.ATIVO
            GROUP BY a.categoriaAnimal
            HAVING COUNT(a) > 0
            """)
    List<Object[]> contarPorCategoriaComEstoque();

    @Query("""
            SELECT DISTINCT a.cidade FROM Anuncio a
            WHERE a.status = br.com.appcompregado.domain.enums.AnuncioStatus.ATIVO
            ORDER BY a.cidade
            """)
    List<String> cidadesComEstoque();

    @Query("SELECT a FROM Anuncio a JOIN FETCH a.vendedor WHERE a.id = :id")
    Optional<Anuncio> findByIdWithVendedor(@Param("id") Long id);

    @Query("SELECT a FROM Anuncio a JOIN FETCH a.vendedor v WHERE v.id = :vendedorId ORDER BY a.id DESC")
    List<Anuncio> doVendedor(@Param("vendedorId") Long vendedorId);
}
