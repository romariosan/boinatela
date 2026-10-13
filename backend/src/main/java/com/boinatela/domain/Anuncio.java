package com.boinatela.domain;

import com.boinatela.domain.enums.AnuncioStatus;
import com.boinatela.domain.enums.Aptidao;
import com.boinatela.domain.enums.CategoriaAnimal;
import com.boinatela.domain.enums.Raca;
import com.boinatela.domain.enums.TipoVenda;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

    // O default no banco cobre as linhas que já existiam quando a coluna foi criada.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'ATIVO'")
    private AnuncioStatus status = AnuncioStatus.ATIVO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_vendedor", nullable = false)
    private Usuario vendedor;

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

    public AnuncioStatus getStatus() {
        return status;
    }

    public void setStatus(AnuncioStatus status) {
        this.status = status;
    }

    public Usuario getVendedor() {
        return vendedor;
    }

    public void setVendedor(Usuario vendedor) {
        this.vendedor = vendedor;
    }
}
