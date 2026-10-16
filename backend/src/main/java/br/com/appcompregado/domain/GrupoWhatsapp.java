package br.com.appcompregado.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

/** Grupo de WhatsApp que o Chip 2 escuta. A cidade padrão vale quando a mensagem não cita outra. */
@Entity
@Table(name = "grupo_whatsapp")
public class GrupoWhatsapp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String whatsappJid;

    @Column(nullable = false, length = 160)
    private String nomeGrupo;

    @Column(nullable = false, length = 80)
    private String cidadePadrao;

    @Column(nullable = false, length = 2)
    private String ufPadrao;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean ativo = true;

    public Long getId() {
        return id;
    }

    public String getWhatsappJid() {
        return whatsappJid;
    }

    public void setWhatsappJid(String whatsappJid) {
        this.whatsappJid = whatsappJid;
    }

    public String getNomeGrupo() {
        return nomeGrupo;
    }

    public void setNomeGrupo(String nomeGrupo) {
        this.nomeGrupo = nomeGrupo;
    }

    public String getCidadePadrao() {
        return cidadePadrao;
    }

    public void setCidadePadrao(String cidadePadrao) {
        this.cidadePadrao = cidadePadrao;
    }

    public String getUfPadrao() {
        return ufPadrao;
    }

    public void setUfPadrao(String ufPadrao) {
        this.ufPadrao = ufPadrao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
