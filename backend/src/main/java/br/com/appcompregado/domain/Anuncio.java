package br.com.appcompregado.domain;

import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusProcessamento;
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
import java.util.Locale;
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

    /** Padrão "Cidade - UF", para a busca que já filtra pelo sufixo. A UF também fica na coluna uf. */
    @Column(nullable = false)
    private String cidade;

    @Column(length = 2)
    private String uf;

    /** Capa. Nos lotes do app coincide com a primeira de fotos. */
    @Column(length = 500)
    private String urlFotoPrincipal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @ColumnDefault("'PUBLICADO'")
    private StatusProcessamento statusProcessamento = StatusProcessamento.PUBLICADO;

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

    /** WhatsApp de quem publicou no grupo, já normalizado. Nulo nos anúncios criados no app. */
    @Column(length = 20)
    private String telefoneVendedorOriginal;

    /** WHATSAPP no grupo. FACEBOOK quando a curadoria cola o anúncio com a tag FB:. */
    @Column(nullable = false, length = 20)
    @ColumnDefault("'WHATSAPP'")
    private String origem = "WHATSAPP";

    /** O dono confirmou o lote pelo código enviado no WhatsApp. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean reivindicado = false;

    /** Quando o Chip 2 gravou o anúncio. Nulo = publicado pelo app, não pela escuta. */
    private LocalDateTime dataCaptura;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean mensagemConsultorEnviada = false;

    private LocalDateTime dataAbordagemConsultor;

    /** Já mandamos o lembrete de 15 dias perguntando se o lote foi vendido. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean mensagemRetencaoEnviada = false;

    @Column(nullable = false)
    @ColumnDefault("0")
    private int quantidadeCliquesContato = 0;

    /** false quando o lote sai de circulação (vendido). Não é o status PAUSADO. */
    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean ativo = true;

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
        if (cidade != null && cidade.length() >= 5) {
            int corte = cidade.length() - 5;
            if (cidade.regionMatches(true, corte, " - ", 0, 3)) {
                String sigla = cidade.substring(cidade.length() - 2);
                if (sigla.chars().allMatch(Character::isLetter)) {
                    this.uf = sigla.toUpperCase(Locale.ROOT);
                }
            }
        }
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf == null ? null : uf.trim().toUpperCase(Locale.ROOT);
    }

    public String getUrlFotoPrincipal() {
        return urlFotoPrincipal;
    }

    public void setUrlFotoPrincipal(String urlFotoPrincipal) {
        this.urlFotoPrincipal = urlFotoPrincipal;
    }

    public StatusProcessamento getStatusProcessamento() {
        return statusProcessamento;
    }

    public void setStatusProcessamento(StatusProcessamento statusProcessamento) {
        this.statusProcessamento = statusProcessamento;
    }

    public boolean publicadoNaVitrine() {
        return ativo
                && status == AnuncioStatus.ATIVO
                && statusProcessamento != StatusProcessamento.RASCUNHO_SEM_FOTO;
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

    public String getTelefoneVendedorOriginal() {
        return telefoneVendedorOriginal;
    }

    public void setTelefoneVendedorOriginal(String telefoneVendedorOriginal) {
        this.telefoneVendedorOriginal = telefoneVendedorOriginal;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(String origem) {
        this.origem = origem == null || origem.isBlank() ? "WHATSAPP" : origem.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isReivindicado() {
        return reivindicado;
    }

    public void setReivindicado(boolean reivindicado) {
        this.reivindicado = reivindicado;
    }

    public LocalDateTime getDataCaptura() {
        return dataCaptura;
    }

    public void setDataCaptura(LocalDateTime dataCaptura) {
        this.dataCaptura = dataCaptura;
    }

    public boolean isMensagemConsultorEnviada() {
        return mensagemConsultorEnviada;
    }

    public void setMensagemConsultorEnviada(boolean mensagemConsultorEnviada) {
        this.mensagemConsultorEnviada = mensagemConsultorEnviada;
    }

    public LocalDateTime getDataAbordagemConsultor() {
        return dataAbordagemConsultor;
    }

    public void setDataAbordagemConsultor(LocalDateTime dataAbordagemConsultor) {
        this.dataAbordagemConsultor = dataAbordagemConsultor;
    }

    public boolean isMensagemRetencaoEnviada() {
        return mensagemRetencaoEnviada;
    }

    public void setMensagemRetencaoEnviada(boolean mensagemRetencaoEnviada) {
        this.mensagemRetencaoEnviada = mensagemRetencaoEnviada;
    }

    public int getQuantidadeCliquesContato() {
        return quantidadeCliquesContato;
    }

    public void setQuantidadeCliquesContato(int quantidadeCliquesContato) {
        this.quantidadeCliquesContato = quantidadeCliquesContato;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    /** Página pública do lote, sem login. */
    public String getLinkWeb() {
        return LinksDoSite.baseUrl() + "/lote/" + id;
    }
}
