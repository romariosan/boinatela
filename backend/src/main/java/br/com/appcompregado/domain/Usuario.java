package br.com.appcompregado.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true, length = 20)
    private String whatsapp;

    @JsonIgnore
    @Column(nullable = false)
    private String senha;

    @Column(nullable = false)
    private boolean isPremium = false;

    /** Último dia do plano PRO; na madrugada seguinte o AssinaturaJobService rebaixa. Nulo = não expira. */
    private LocalDate dataExpiracaoPremium;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean telefoneVerificado = false;

    /** Painel de moderação e prospecção (/api/admin). Cadastro normal fica false. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean admin = false;

    /** Ex.: CG-482913. Some depois da verificação. */
    @JsonIgnore
    @Column(length = 16)
    private String codigoVerificacao;

    /** Vai no JWT (claim "ver"); trocar a senha incrementa e derruba os tokens emitidos antes. */
    @JsonIgnore
    @Column(nullable = false)
    @ColumnDefault("0")
    private int versaoSessao = 0;

    /** Erros seguidos da senha atual ao trocar a senha; zera ao acertar ou ao bloquear. */
    @JsonIgnore
    @Column(nullable = false)
    @ColumnDefault("0")
    private int tentativasSenhaAtual = 0;

    @JsonIgnore
    private LocalDateTime trocaSenhaBloqueadaAte;

    /** Última mensagem do consultor (Chip 3) para este número. Nulo = nunca abordado. */
    @JsonIgnore
    private LocalDateTime dataUltimaAbordagemBot;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(String whatsapp) {
        this.whatsapp = whatsapp;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public boolean isPremium() {
        return isPremium;
    }

    public void setPremium(boolean premium) {
        isPremium = premium;
    }

    public LocalDate getDataExpiracaoPremium() {
        return dataExpiracaoPremium;
    }

    public void setDataExpiracaoPremium(LocalDate dataExpiracaoPremium) {
        this.dataExpiracaoPremium = dataExpiracaoPremium;
    }

    public boolean isTelefoneVerificado() {
        return telefoneVerificado;
    }

    public void setTelefoneVerificado(boolean telefoneVerificado) {
        this.telefoneVerificado = telefoneVerificado;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public String getCodigoVerificacao() {
        return codigoVerificacao;
    }

    public void setCodigoVerificacao(String codigoVerificacao) {
        this.codigoVerificacao = codigoVerificacao;
    }

    public int getVersaoSessao() {
        return versaoSessao;
    }

    /** Senha nova: as sessões abertas em outros aparelhos precisam entrar de novo. */
    public void encerrarSessoes() {
        versaoSessao++;
    }

    public int getTentativasSenhaAtual() {
        return tentativasSenhaAtual;
    }

    public void setTentativasSenhaAtual(int tentativasSenhaAtual) {
        this.tentativasSenhaAtual = tentativasSenhaAtual;
    }

    public LocalDateTime getTrocaSenhaBloqueadaAte() {
        return trocaSenhaBloqueadaAte;
    }

    public void setTrocaSenhaBloqueadaAte(LocalDateTime trocaSenhaBloqueadaAte) {
        this.trocaSenhaBloqueadaAte = trocaSenhaBloqueadaAte;
    }

    public LocalDateTime getDataUltimaAbordagemBot() {
        return dataUltimaAbordagemBot;
    }

    public void setDataUltimaAbordagemBot(LocalDateTime dataUltimaAbordagemBot) {
        this.dataUltimaAbordagemBot = dataUltimaAbordagemBot;
    }
}
