package br.com.appcompregado.domain;

import br.com.appcompregado.domain.enums.ChipMensagem;
import br.com.appcompregado.domain.enums.StatusMensagem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Pedido para o robô local enviar uma mensagem de WhatsApp. */
@Entity
@Table(name = "mensagem_saida")
public class MensagemSaida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChipMensagem chip;

    @Column(nullable = false, length = 20)
    private String telefone;

    @Column(nullable = false, length = 4000)
    private String texto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusMensagem status = StatusMensagem.PENDENTE;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    @Column(nullable = false)
    private LocalDateTime expiraEm;

    private LocalDateTime enviadaEm;

    public Long getId() {
        return id;
    }

    public ChipMensagem getChip() {
        return chip;
    }

    public void setChip(ChipMensagem chip) {
        this.chip = chip;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public StatusMensagem getStatus() {
        return status;
    }

    public void setStatus(StatusMensagem status) {
        this.status = status;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public void setCriadaEm(LocalDateTime criadaEm) {
        this.criadaEm = criadaEm;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(LocalDateTime expiraEm) {
        this.expiraEm = expiraEm;
    }

    public LocalDateTime getEnviadaEm() {
        return enviadaEm;
    }

    public void setEnviadaEm(LocalDateTime enviadaEm) {
        this.enviadaEm = enviadaEm;
    }
}
