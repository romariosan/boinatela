package br.com.appcompregado.repository;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnuncioRepository extends JpaRepository<Anuncio, Long>, JpaSpecificationExecutor<Anuncio> {

    @Query("""
            SELECT a.categoriaAnimal, COUNT(a)
            FROM Anuncio a
            WHERE a.status = br.com.appcompregado.domain.enums.AnuncioStatus.ATIVO
              AND a.ativo = true
              AND a.statusProcessamento <> br.com.appcompregado.domain.enums.StatusProcessamento.RASCUNHO_SEM_FOTO
              AND a.urlFotoPrincipal IS NOT NULL
              AND trim(a.urlFotoPrincipal) <> ''
              AND a.cidade IS NOT NULL
              AND trim(a.cidade) <> ''
              AND lower(trim(a.cidade)) NOT LIKE 'a confirmar%'
            GROUP BY a.categoriaAnimal
            HAVING COUNT(a) > 0
            """)
    List<Object[]> contarPorCategoriaComEstoque();

    @Query("""
            SELECT DISTINCT a.cidade FROM Anuncio a
            WHERE a.status = br.com.appcompregado.domain.enums.AnuncioStatus.ATIVO
              AND a.ativo = true
              AND a.statusProcessamento <> br.com.appcompregado.domain.enums.StatusProcessamento.RASCUNHO_SEM_FOTO
              AND a.urlFotoPrincipal IS NOT NULL
              AND trim(a.urlFotoPrincipal) <> ''
              AND a.cidade IS NOT NULL
              AND trim(a.cidade) <> ''
              AND lower(trim(a.cidade)) NOT LIKE 'a confirmar%'
            ORDER BY a.cidade
            """)
    List<String> cidadesComEstoque();

    @Query("SELECT a FROM Anuncio a JOIN FETCH a.vendedor WHERE a.id = :id")
    Optional<Anuncio> findByIdWithVendedor(@Param("id") Long id);

    @Query("SELECT a FROM Anuncio a JOIN FETCH a.vendedor v WHERE v.id = :vendedorId ORDER BY a.id DESC")
    List<Anuncio> doVendedor(@Param("vendedorId") Long vendedorId);

    long countByVendedorIdAndStatus(Long vendedorId, AnuncioStatus status);

    /**
     * Do mais novo para o mais antigo. Escrita à mão porque o Postgres põe NULL primeiro no DESC, e
     * dataCriacao nula (anúncio anterior à coluna) precisa contar como o mais antigo; o id desempata.
     */
    @Query("""
            SELECT a FROM Anuncio a
            WHERE a.vendedor.id = :vendedorId AND a.status = :status
            ORDER BY a.dataCriacao DESC NULLS LAST, a.id DESC
            """)
    List<Anuncio> findByVendedorIdAndStatusOrderByDataCriacaoDesc(
            @Param("vendedorId") Long vendedorId, @Param("status") AnuncioStatus status);

    boolean existsByTelefoneVendedorOriginalAndDescricaoAndDataCapturaAfter(
            String telefoneVendedorOriginal, String descricao, LocalDateTime desde);

    Optional<Anuncio> findFirstByTelefoneVendedorOriginalAndDataCapturaAfterOrderByDataCapturaDesc(
            String telefoneVendedorOriginal, LocalDateTime desde);

    @Query("""
            SELECT a.id FROM Anuncio a
            WHERE a.mensagemConsultorEnviada = false
              AND a.reivindicado = false
              AND a.dataCaptura IS NOT NULL
              AND a.dataCaptura <= :ate
              AND a.dataCaptura > :desde
              AND a.telefoneVendedorOriginal IS NOT NULL
            ORDER BY a.dataCaptura ASC
            """)
    List<Long> idsParaAbordagem(
            @Param("desde") LocalDateTime desde, @Param("ate") LocalDateTime ate, Pageable pagina);

    @Query("""
            SELECT MAX(a.dataAbordagemConsultor) FROM Anuncio a
            WHERE a.telefoneVendedorOriginal IN :telefones
              AND a.dataAbordagemConsultor IS NOT NULL
            """)
    LocalDateTime ultimaAbordagem(@Param("telefones") Collection<String> telefones);

    @Query("""
            SELECT a FROM Anuncio a JOIN FETCH a.vendedor
            WHERE a.reivindicado = true AND a.telefoneVendedorOriginal IN :telefones
            """)
    List<Anuncio> reivindicadosPorTelefone(@Param("telefones") Collection<String> telefones);

    /**
     * Ainda no ar, sem o lembrete de 15 dias. dataCaptura nos lotes ouvidos; dataCriacao nos publicados
     * pelo app, que não passam pelo Chip 2.
     */
    @Query("""
            SELECT a.id FROM Anuncio a
            WHERE a.ativo = true
              AND a.status <> br.com.appcompregado.domain.enums.AnuncioStatus.VENDIDO
              AND a.mensagemRetencaoEnviada = false
              AND (
                (a.dataCaptura IS NOT NULL AND a.dataCaptura <= :limite)
                OR (a.dataCaptura IS NULL AND a.dataCriacao IS NOT NULL AND a.dataCriacao <= :limite)
              )
            ORDER BY COALESCE(a.dataCaptura, a.dataCriacao) ASC
            """)
    List<Long> idsParaRetencao(@Param("limite") LocalDateTime limite, Pageable pagina);
}
