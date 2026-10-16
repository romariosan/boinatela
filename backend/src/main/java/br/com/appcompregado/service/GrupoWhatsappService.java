package br.com.appcompregado.service;

import br.com.appcompregado.domain.GrupoWhatsapp;
import br.com.appcompregado.repository.GrupoWhatsappRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrupoWhatsappService {

    static final String CIDADE_FALLBACK = "Formosa";
    static final String UF_FALLBACK = "GO";
    static final String JID_AVULSO = "avulso@g.us";

    private final GrupoWhatsappRepository grupos;

    public GrupoWhatsappService(GrupoWhatsappRepository grupos) {
        this.grupos = grupos;
    }

    @Transactional
    public GrupoWhatsapp resolver(String whatsappJid, String nomeInformado) {
        String jid = whatsappJid == null || whatsappJid.isBlank() ? JID_AVULSO : whatsappJid.trim();
        return grupos.findByWhatsappJid(jid).orElseGet(() -> criar(jid, nomeInformado));
    }

    private GrupoWhatsapp criar(String jid, String nomeInformado) {
        String nome = nomeInformado == null || nomeInformado.isBlank() ? "Grupo " + jid : nomeInformado.trim();
        if (nome.length() > 160) {
            nome = nome.substring(0, 160);
        }
        GrupoWhatsapp grupo = new GrupoWhatsapp();
        grupo.setWhatsappJid(jid);
        grupo.setNomeGrupo(nome);
        grupo.setCidadePadrao(CidadesGoias.inferir(nome).orElse(CIDADE_FALLBACK));
        grupo.setUfPadrao(UF_FALLBACK);
        grupo.setAtivo(true);
        try {
            return grupos.saveAndFlush(grupo);
        } catch (DataIntegrityViolationException e) {
            return grupos.findByWhatsappJid(jid).orElseThrow();
        }
    }
}
