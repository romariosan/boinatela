package br.com.appcompregado.service;

import br.com.appcompregado.domain.GrupoWhatsapp;
import br.com.appcompregado.repository.GrupoWhatsappRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrupoWhatsappService {

    /** Sem município no título do grupo — não inventa Formosa. */
    static final String CIDADE_FALLBACK = CidadesGoias.SEM_CIDADE;
    static final String UF_FALLBACK = "GO";
    static final String JID_AVULSO = "avulso@g.us";

    private final GrupoWhatsappRepository grupos;

    public GrupoWhatsappService(GrupoWhatsappRepository grupos) {
        this.grupos = grupos;
    }

    @Transactional
    public GrupoWhatsapp resolver(String whatsappJid, String nomeInformado) {
        String jid = whatsappJid == null || whatsappJid.isBlank() ? JID_AVULSO : whatsappJid.trim();
        return grupos.findByWhatsappJid(jid)
                .map(grupo -> atualizarNomeECidade(grupo, nomeInformado))
                .orElseGet(() -> criar(jid, nomeInformado));
    }

    /**
     * Atualiza o nome vivo do grupo. Se o título citar um município (ex.: Luziânia),
     * a cidade padrão acompanha. Sem município no título, não inventa Formosa.
     */
    private GrupoWhatsapp atualizarNomeECidade(GrupoWhatsapp grupo, String nomeInformado) {
        if (nomeInformado == null || nomeInformado.isBlank()) {
            return grupo;
        }
        String nome = nomeInformado.trim();
        if (nome.length() > 160) {
            nome = nome.substring(0, 160);
        }
        boolean mudou = !nome.equals(grupo.getNomeGrupo());
        grupo.setNomeGrupo(nome);
        var municipio = CidadesGoias.inferirMunicipio(nome);
        if (municipio.isPresent()) {
            String cidade = municipio.get().cidade();
            String uf = municipio.get().uf();
            if (!cidade.equals(grupo.getCidadePadrao()) || !uf.equalsIgnoreCase(grupo.getUfPadrao())) {
                grupo.setCidadePadrao(cidade);
                grupo.setUfPadrao(uf);
                mudou = true;
            }
        }
        return mudou ? grupos.save(grupo) : grupo;
    }

    private GrupoWhatsapp criar(String jid, String nomeInformado) {
        String nome = nomeInformado == null || nomeInformado.isBlank() ? "Grupo " + jid : nomeInformado.trim();
        if (nome.length() > 160) {
            nome = nome.substring(0, 160);
        }
        GrupoWhatsapp grupo = new GrupoWhatsapp();
        grupo.setWhatsappJid(jid);
        grupo.setNomeGrupo(nome);
        var municipio = CidadesGoias.inferirMunicipio(nome);
        if (municipio.isPresent()) {
            grupo.setCidadePadrao(municipio.get().cidade());
            grupo.setUfPadrao(municipio.get().uf());
        } else {
            grupo.setCidadePadrao(CidadesGoias.inferir(nome).orElse(CIDADE_FALLBACK));
            grupo.setUfPadrao(UF_FALLBACK);
        }
        grupo.setAtivo(true);
        try {
            return grupos.saveAndFlush(grupo);
        } catch (DataIntegrityViolationException e) {
            return grupos.findByWhatsappJid(jid).orElseThrow();
        }
    }
}
