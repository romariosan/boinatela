package br.com.appcompregado.repository;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.StatusPublicacao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;

/**
 * Busca livre da tela de Busca: "Bezerros Nelore, Formosa" vira os termos bezerros, nelore e formosa,
 * e o anúncio precisa casar com todos (título, descrição, cidade ou o nome de categoria/raça/aptidão).
 */
public final class AnuncioBusca {

    private static final int TAMANHO_MINIMO_TERMO = 3;

    private AnuncioBusca() {
    }

    /** Só anúncios ATIVO: vendido não aparece na vitrine nem na busca. */
    public static Specification<Anuncio> filtrar(
            String q, String cidade, String uf, CategoriaAnimal categoria, Aptidao aptidao, Raca raca) {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType())) {
                root.fetch("vendedor", JoinType.INNER);
            }
            List<Predicate> filtros = new ArrayList<>();
            filtros.add(cb.equal(root.get("status"), AnuncioStatus.ATIVO));
            filtros.add(cb.isTrue(root.get("ativo")));
            filtros.add(cb.notEqual(root.get("statusProcessamento"), StatusProcessamento.RASCUNHO_SEM_FOTO));
            // Só lote aprovado na moderação; PENDENTE/REJEITADO/null ficam fora da vitrine.
            filtros.add(cb.isNotNull(root.get("statusPublicacao")));
            filtros.add(cb.equal(root.get("statusPublicacao"), StatusPublicacao.APROVADO));
            exigirProva(filtros, root, cb);
            for (String termo : termos(q)) {
                filtros.add(casaComTermo(root, cb, termo));
            }
            if (cidade != null && !cidade.isBlank()) {
                filtros.add(cb.equal(cb.lower(root.get("cidade")), cidade.trim().toLowerCase(Locale.ROOT)));
            }
            // Cidade padronizada como "Formosa - GO": a UF é o sufixo.
            if (uf != null && uf.trim().matches("[A-Za-z]{2}")) {
                filtros.add(cb.like(cb.lower(root.get("cidade")), "% - " + uf.trim().toLowerCase(Locale.ROOT)));
            }
            if (categoria != null) {
                filtros.add(cb.equal(root.get("categoriaAnimal"), categoria));
            }
            if (aptidao != null) {
                filtros.add(cb.equal(root.get("aptidao"), aptidao));
            }
            if (raca != null) {
                filtros.add(cb.equal(root.get("raca"), raca));
            }
            return cb.and(filtros.toArray(Predicate[]::new));
        };
    }

    /**
     * Vitrine e busca: foto + cidade preenchida.
     * "Local a combinar" vale (fallback explícito). Legado SEM_CIDADE / "A confirmar" ficam fora
     * até migrar para "Local a combinar".
     */
    private static void exigirProva(
            List<Predicate> filtros, Root<Anuncio> root, CriteriaBuilder cb) {
        filtros.add(cb.isNotNull(root.get("urlFotoPrincipal")));
        filtros.add(cb.notEqual(cb.trim(root.get("urlFotoPrincipal")), ""));
        filtros.add(cb.isNotNull(root.get("cidade")));
        filtros.add(cb.notEqual(cb.trim(root.get("cidade")), ""));
        filtros.add(cb.not(cb.like(cb.lower(cb.trim(root.get("cidade"))), "a confirmar%")));
        // legado literal SEM_CIDADE (antes do fallback "Local a combinar")
        filtros.add(cb.not(cb.equal(cb.lower(cb.trim(root.get("cidade"))), "sem_cidade")));
        filtros.add(cb.not(cb.like(cb.lower(cb.trim(root.get("cidade"))), "sem_cidade -%")));
    }

    /** Mesma regra da vitrine, para teste e para não divergir do filtro. */
    public static boolean temProva(String urlFotoPrincipal, String cidade) {
        if (urlFotoPrincipal == null || urlFotoPrincipal.isBlank()) {
            return false;
        }
        if (cidade == null || cidade.isBlank()) {
            return false;
        }
        String normal = cidade.trim().toLowerCase(Locale.ROOT);
        if (normal.startsWith("a confirmar")) {
            return false;
        }
        // Legado "SEM_CIDADE"; "Local a combinar" é o fallback válido.
        return !normal.equals("sem_cidade") && !normal.startsWith("sem_cidade -");
    }

    static List<String> termos(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return Arrays.stream(q.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(termo -> termo.length() >= TAMANHO_MINIMO_TERMO)
                .distinct()
                .toList();
    }

    private static Predicate casaComTermo(Root<Anuncio> root, CriteriaBuilder cb, String termo) {
        String raiz = singular(termo);
        // termos() só deixa letras e dígitos, então não há % ou _ para escapar no LIKE.
        String padrao = "%" + raiz + "%";
        List<Predicate> alternativas = new ArrayList<>(List.of(
                cb.like(cb.lower(root.get("titulo")), padrao),
                cb.like(cb.lower(root.get("descricao")), padrao),
                cb.like(cb.lower(root.get("cidade")), padrao)));
        Stream.of(termo, raiz).distinct().forEach(forma -> {
            porNome(CategoriaAnimal.class, forma)
                    .forEach(valor -> alternativas.add(cb.equal(root.get("categoriaAnimal"), valor)));
            porNome(Raca.class, forma).forEach(valor -> alternativas.add(cb.equal(root.get("raca"), valor)));
            porNome(Aptidao.class, forma).forEach(valor -> alternativas.add(cb.equal(root.get("aptidao"), valor)));
        });
        return cb.or(alternativas.toArray(Predicate[]::new));
    }

    private static String singular(String termo) {
        return termo.length() > 4 && termo.endsWith("s") ? termo.substring(0, termo.length() - 1) : termo;
    }

    private static <E extends Enum<E>> List<E> porNome(Class<E> tipo, String termo) {
        String nome = Normalizer.normalize(termo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return Arrays.stream(tipo.getEnumConstants()).filter(valor -> valor.name().equals(nome)).toList();
    }
}
