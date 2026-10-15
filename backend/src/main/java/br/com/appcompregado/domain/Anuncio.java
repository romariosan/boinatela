package br.com.appcompregado.domain;

import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.TipoVenda;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "anuncio")
public class Anuncio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, length = 4000)
    private String descricao;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private String cidade;

    // A ordem importa: a primeira foto é a capa do anúncio.
    @ElementCollection
    @CollectionTable(name = "anuncio_foto", joinColumns = @JoinColumn(name = "anuncio_id"))
    @OrderColumn(name = "posicao")
    @Column(name = "url", nullable = false, length = 500)
    @BatchSize(size = 50)
    private List<String> fotos = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaAnimal categoriaAnimal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Aptidao aptidao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Raca raca;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoVenda tipoVenda;

    /** Cabeças no anúncio. Opcional: anúncios antigos não têm. */
    private Integer quantidade;

    /** Peso médio por cabeça, em kg. Opcional. */
    private Integer pesoMedioKg;

    // O default no banco cobre as linhas que já existiam quando a coluna foi criada.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'ATIVO'")
    private AnuncioStatus status = AnuncioStatus.ATIVO;

    /**
     * Define quais são os "mais recentes" quando o PRO vence. Nulo nos anúncios criados antes da coluna
     * existir: esses contam como os mais antigos.
     */
    @Column(updatable = false)
    private LocalDateTime dataCriacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_vendedor", nullable = false)
    private Usuario vendedor;

    @PrePersist
    void aoCriar() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public List<String> getFotos() {
        return fotos;
    }

    public void setFotos(List<String> fotos) {
        this.fotos = fotos;
    }

    public CategoriaAnimal getCategoriaAnimal() {
        return categoriaAnimal;
    }

    public void setCategoriaAnimal(CategoriaAnimal categoriaAnimal) {
        this.categoriaAnimal = categoriaAnimal;
    }

    public Aptidao getAptidao() {
        return aptidao;
    }

    public void setAptidao(Aptidao aptidao) {
        this.aptidao = aptidao;
    }

    public Raca getRaca() {
        return raca;
    }

    public void setRaca(Raca raca) {
        this.raca = raca;
    }

    public TipoVenda getTipoVenda() {
        return tipoVenda;
    }

    public void setTipoVenda(TipoVenda tipoVenda) {
        this.tipoVenda = tipoVenda;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public Integer getPesoMedioKg() {
        return pesoMedioKg;
    }

    public void setPesoMedioKg(Integer pesoMedioKg) {
        this.pesoMedioKg = pesoMedioKg;
    }

    public AnuncioStatus getStatus() {
        return status;
    }

    public void setStatus(AnuncioStatus status) {
        this.status = status;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public Usuario getVendedor() {
        return vendedor;
    }

    public void setVendedor(Usuario vendedor) {
        this.vendedor = vendedor;
    }
}
