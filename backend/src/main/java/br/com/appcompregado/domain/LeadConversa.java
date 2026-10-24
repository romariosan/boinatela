package br.com.appcompregado.domain;

import br.com.appcompregado.domain.enums.StatusLeadConversa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Uma conversa por anúncio capturado. Não é fila de envio: o envio continua em mensagem_saida. */
@Entity
@Table(name = "lead_conversa")
public class LeadConversa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "anuncio_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Anuncio anuncio;

    @Column(nullable = false, length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusLeadConversa status = StatusLeadConversa.CAPTURADO;

    @Column(nullable = false)
    private LocalDateTime dataCaptura;

    @Column(nullable = false)
    private LocalDateTime dataUltimaInteracao;

    /** Nome do grupo de onde o lote saiu, para o texto fixo. */
    @Column(length = 160)
    private String nomeGrupo;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean ofereceuLink = false;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean podeEnviarLink = false;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean lembreteEnviado = false;

    /** Venda ou encerramento sem ser recusa. Recusa continua em REJEITADO. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean encerrada = false;

    @Column(length = 2000)
    private String ultimaEntrada;

    @Column(length = 80)
    private String mensagemExternaId;

    public Long getId() {
        return id;
    }

    public Anuncio getAnuncio() {
        return anuncio;
    }

    public void setAnuncio(Anuncio anuncio) {
        this.anuncio = anuncio;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public StatusLeadConversa getStatus() {
        return status;
    }

    public void setStatus(StatusLeadConversa status) {
        this.status = status;
    }

    public LocalDateTime getDataCaptura() {
        return dataCaptura;
    }

    public void setDataCaptura(LocalDateTime dataCaptura) {
        this.dataCaptura = dataCaptura;
    }

    public LocalDateTime getDataUltimaInteracao() {
        return dataUltimaInteracao;
    }

    public void setDataUltimaInteracao(LocalDateTime dataUltimaInteracao) {
        this.dataUltimaInteracao = dataUltimaInteracao;
    }

    public String getNomeGrupo() {
        return nomeGrupo;
    }

    public void setNomeGrupo(String nomeGrupo) {
        this.nomeGrupo = nomeGrupo;
    }

    public boolean isOfereceuLink() {
        return ofereceuLink;
    }

    public void setOfereceuLink(boolean ofereceuLink) {
        this.ofereceuLink = ofereceuLink;
    }

    public boolean isPodeEnviarLink() {
        return podeEnviarLink;
    }

    public void setPodeEnviarLink(boolean podeEnviarLink) {
        this.podeEnviarLink = podeEnviarLink;
    }

    public boolean isLembreteEnviado() {
        return lembreteEnviado;
    }

    public void setLembreteEnviado(boolean lembreteEnviado) {
        this.lembreteEnviado = lembreteEnviado;
    }

    public boolean isEncerrada() {
        return encerrada;
    }

    public void setEncerrada(boolean encerrada) {
        this.encerrada = encerrada;
    }

    public String getUltimaEntrada() {
        return ultimaEntrada;
    }

    public void setUltimaEntrada(String ultimaEntrada) {
        this.ultimaEntrada = ultimaEntrada;
    }

    public String getMensagemExternaId() {
        return mensagemExternaId;
    }

    public void setMensagemExternaId(String mensagemExternaId) {
        this.mensagemExternaId = mensagemExternaId;
    }
}
