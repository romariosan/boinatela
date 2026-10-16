package br.com.appcompregado.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;

/** Comprador que pediu aviso quando aparecer um lote com esta raça, categoria ou cidade. */
@Entity
@Table(name = "alerta_busca")
public class AlertaBusca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String whatsapp;

    @Column(nullable = false, length = 40)
    private String raca = "";

    @Column(nullable = false, length = 40)
    private String categoriaPilar = "";

    @Column(nullable = false, length = 80)
    private String cidade = "";

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean ativo = true;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    public Long getId() {
        return id;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(String whatsapp) {
        this.whatsapp = whatsapp;
    }

    public String getRaca() {
        return raca;
    }

    public void setRaca(String raca) {
        this.raca = raca;
    }

    public String getCategoriaPilar() {
        return categoriaPilar;
    }

    public void setCategoriaPilar(String categoriaPilar) {
        this.categoriaPilar = categoriaPilar;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }
}
