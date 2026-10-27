package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.GrupoWhatsapp;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusContato;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.StatusPublicacao;
import br.com.appcompregado.domain.enums.TipoVenda;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.config.GeminiProperties;
import br.com.appcompregado.service.GeminiExtractionService.ExtracaoAnuncio;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Chip 2: só grava. Não responde no grupo. */
@Service
public class ScraperAnuncioService {

    private static final Logger log = LoggerFactory.getLogger(ScraperAnuncioService.class);
    private static final Duration RECENCIA = Duration.ofMinutes(10);
    private static final int MAXIMO_FOTO_BYTES = 5 * 1024 * 1024;

    private final AnuncioRepository anuncios;
    private final UsuarioRepository usuarios;
    private final GeminiProperties geminiProps;
    private final PasswordEncoder passwordEncoder;
    private final AlertaNotificador radar;
    private final GrupoWhatsappService grupos;
    private final ArmazenamentoLocalService armazenamento;
    private final LeadAbordagemService abordagem;
    private final TransactionTemplate transacao;
    private final Executor executor;
    private final GeminiEnrichmentJobService enriquecimento;

    public ScraperAnuncioService(
            AnuncioRepository anuncios,
            UsuarioRepository usuarios,
            GeminiProperties geminiProps,
            PasswordEncoder passwordEncoder,
            AlertaNotificador radar,
            GrupoWhatsappService grupos,
            ArmazenamentoLocalService armazenamento,
            LeadAbordagemService abordagem,
            TransactionTemplate transacao,
            @Qualifier("applicationTaskExecutor") Executor executor,
            @Lazy GeminiEnrichmentJobService enriquecimento) {
        this.anuncios = anuncios;
        this.usuarios = usuarios;
        this.geminiProps = geminiProps;
        this.passwordEncoder = passwordEncoder;
        this.radar = radar;
        this.grupos = grupos;
        this.armazenamento = armazenamento;
        this.abordagem = abordagem;
        this.transacao = transacao;
        this.executor = executor;
        this.enriquecimento = enriquecimento;
    }

    public ResultadoCaptura ingerir(JsonNode corpo) {
        Preparo preparo = preparar(corpo);
        if (preparo.recusa() != null) {
            return preparo.recusa();
        }
        // Webhook responde rápido: ExtracaoLocal na hora; Gemini só em segundo plano.
        ExtracaoAnuncio extraido = preparo.texto().isBlank()
                ? null
                : ExtracaoLocal.extrair(preparo.texto(), preparo.cidadePadrao(), preparo.ufPadrao())
                        .orElse(null);
        ExtracaoAnuncio lido = extraido;
        boolean enfileirarGemini = chaveGemini() && !preparo.texto().isBlank();
        ResultadoCaptura gravado = transacao.execute(status -> gravar(preparo, lido, enfileirarGemini));
        if (gravado == null) {
            return ResultadoCaptura.recusado("falha ao gravar");
        }
        if (enfileirarGemini && gravado.aceito() && gravado.anuncioId() != null) {
            Long id = gravado.anuncioId();
            executor.execute(() -> {
                try {
                    enriquecimento.tentarUm(id);
                } catch (RuntimeException e) {
                    log.warn("Primeira tentativa Gemini em segundo plano falhou para anúncio {}: {}",
                            id, e.toString());
                }
            });
        }
        return gravado;
    }

    private boolean chaveGemini() {
        return geminiProps.getKey() != null && !geminiProps.getKey().isBlank();
    }

    private Preparo preparar(JsonNode corpo) {
        if (corpo == null || corpo.isNull()) {
            return Preparo.recusa("corpo vazio");
        }
        JsonNode data = corpo.has("data") ? corpo.get("data") : corpo;
        if (data.path("key").path("fromMe").asBoolean(false)) {
            return Preparo.recusa("mensagem do próprio chip");
        }
        if (data.has("key") && !data.path("key").path("remoteJid").asText("").endsWith("@g.us")) {
            return Preparo.recusa("não é mensagem de grupo");
        }

        String texto = textoDaMensagem(data, corpo);
        if (texto.isBlank() && !temMidia(data, corpo)) {
            return Preparo.recusa("sem texto");
        }
        Long timestamp = timestampDe(data, corpo);
        if (!recente(timestamp, Instant.now())) {
            return Preparo.recusa("mensagem com mais de 10 minutos");
        }

        String remetente = remetenteDe(data, corpo);
        String telefoneRemetente = PhoneUtils.normalizar(remetente);
        if (!PhoneUtils.celularBrasileiro(telefoneRemetente)) {
            return Preparo.recusa("remetente sem celular válido");
        }

        String jid = jidDoGrupo(data, corpo);
        String nomeGrupoVivo = nomeDoGrupo(data, corpo);
        GrupoWhatsapp grupo = grupos.resolver(jid, nomeGrupoVivo);
        // Nome vivo da mensagem (não o antigo no banco) para inferir Luziânia etc.
        String nomeParaCidade = nomeGrupoVivo.isBlank() ? grupo.getNomeGrupo() : nomeGrupoVivo;
        return new Preparo(
                null,
                data,
                corpo,
                texto.isBlank() ? "" : texto,
                telefoneRemetente,
                grupo.getCidadePadrao(),
                grupo.getUfPadrao(),
                nomeParaCidade);
    }

    private ResultadoCaptura gravar(Preparo preparo, ExtracaoAnuncio extraido, boolean enfileirarGemini) {
        JsonNode data = preparo.data();
        JsonNode corpo = preparo.corpo();
        String textoCompleto = preparo.texto();
        String telefoneCurado = telefoneDaCuradoria(extraido, textoCompleto);
        String telefone = telefoneCurado == null ? preparo.telefoneRemetente() : telefoneCurado;
        String origem = telefoneCurado == null ? "WHATSAPP" : "FACEBOOK";
        LocalDateTime agora = LocalDateTime.now(ZoneId.of(GeminiEnrichmentJobService.FUSO));

        Optional<Anuncio> repetido = anuncios
                .findFirstByTelefoneVendedorOriginalAndDataCapturaAfterOrderByDataCapturaDesc(
                        telefone, LocalDateTime.now().minusDays(14));
        if (repetido.isPresent()) {
            Anuncio existente = repetido.get();
            existente.setDataCaptura(agora);
            // Nova captura do Radar: volta ao limbo. Sem isto, um lote já APROVADO
            // no update de 14 dias reaparecia na vitrine sem nova moderação.
            existente.setStatusPublicacao(StatusPublicacao.PENDENTE);
            if (existente.getStatusContato() != StatusContato.REIVINDICADO) {
                existente.setStatusContato(StatusContato.NAO_CONTATADO);
            }
            aplicarExtracao(
                    existente,
                    extraido,
                    preparo.cidadePadrao(),
                    preparo.ufPadrao(),
                    textoCompleto,
                    "FACEBOOK".equals(origem),
                    preparo.nomeGrupo());
            boolean publicadoAgora = anexarFotos(existente, coletarFotos(data, corpo));
            if (enfileirarGemini) {
                marcarGeminiPendente(existente, agora);
            }
            anuncios.save(existente);
            if (publicadoAgora) {
                radar.agendar(existente);
                if (tituloProntoParaAbordagem(existente) && PhoneUtils.celularBrasileiro(telefone)) {
                    abordagem.agendarEstreia(existente, preparo.nomeGrupo());
                }
            }
            return new ResultadoCaptura(true, "anúncio atualizado", existente.getId());
        }

        String descricao = descricaoPublica(textoCompleto, "FACEBOOK".equals(origem));
        Anuncio anuncio = new Anuncio();
        if (extraido == null) {
            anuncio.setTitulo("Anúncio de gado");
            anuncio.setPreco(BigDecimal.ZERO);
            anuncio.setRaca(Raca.OUTRA);
            anuncio.setQuantidade(null);
            anuncio.setCidade(cidadeDoTextoOuPadrao(
                    textoCompleto, null, null, preparo.cidadePadrao(), preparo.ufPadrao(), preparo.nomeGrupo()));
            log.info("Captura local: anúncio gravado; Gemini tenta depois se a chave existir.");
        } else {
            anuncio.setTitulo(limitar(extraido.titulo(), 180));
            anuncio.setPreco(precoOuZero(extraido.preco()));
            anuncio.setRaca(extraido.raca() == null ? Raca.OUTRA : extraido.raca());
            anuncio.setQuantidade(extraido.quantidade());
            anuncio.setCidade(cidadeDoTextoOuPadrao(
                    textoCompleto,
                    extraido.cidade(),
                    extraido.uf(),
                    preparo.cidadePadrao(),
                    preparo.ufPadrao(),
                    preparo.nomeGrupo()));
        }
        anuncio.setDescricao(descricao);
        anuncio.setCategoriaAnimal(categoriaDe(descricao));
        anuncio.setAptidao(aptidaoDe(descricao));
        anuncio.setTipoVenda(anuncio.getQuantidade() != null && anuncio.getQuantidade() > 1
                ? TipoVenda.LOTE
                : TipoVenda.CABECA);
        anuncio.setStatus(AnuncioStatus.ATIVO);
        // Captura do grupo: entra pendente de moderação; só APROVADO vai à vitrine.
        anuncio.setStatusPublicacao(StatusPublicacao.PENDENTE);
        anuncio.setStatusContato(StatusContato.NAO_CONTATADO);
        anuncio.setTelefoneVendedorOriginal(telefone);
        anuncio.setOrigem(origem);
        anuncio.setReivindicado(false);
        anuncio.setDataCaptura(agora);
        anuncio.setVendedor(contaCaptura());
        List<String> fotos = coletarFotos(data, corpo);
        if (fotos.isEmpty()) {
            anuncio.setAtivo(false);
            anuncio.setStatusProcessamento(StatusProcessamento.RASCUNHO_SEM_FOTO);
        } else {
            anuncio.setUrlFotoPrincipal(fotos.get(0));
            anuncio.getFotos().addAll(fotos);
            if (prontoParaVitrine(anuncio)) {
                anuncio.setAtivo(true);
                anuncio.setStatusProcessamento(StatusProcessamento.PUBLICADO);
            } else {
                anuncio.setAtivo(false);
                anuncio.setStatusProcessamento(StatusProcessamento.RASCUNHO_SEM_FOTO);
                log.info("Captura com foto sem título/preço: fica rascunho até a extração preencher.");
            }
        }
        if (enfileirarGemini) {
            marcarGeminiPendente(anuncio, agora);
        }
        anuncio = anuncios.save(anuncio);
        if (anuncio.getStatusProcessamento() == StatusProcessamento.PUBLICADO) {
            radar.agendar(anuncio);
        }
        if (extraido != null && PhoneUtils.celularBrasileiro(telefone)) {
            abordagem.agendarEstreia(anuncio, preparo.nomeGrupo());
        }
        return new ResultadoCaptura(true, null, anuncio.getId());
    }

    static void marcarGeminiPendente(Anuncio anuncio, LocalDateTime agora) {
        if (anuncio == null) {
            return;
        }
        anuncio.setGeminiPendente(true);
        anuncio.setTentativasGemini(0);
        anuncio.setProximaTentativaGemini(agora);
    }

    private record Preparo(
            ResultadoCaptura recusa,
            JsonNode data,
            JsonNode corpo,
            String texto,
            String telefoneRemetente,
            String cidadePadrao,
            String ufPadrao,
            String nomeGrupo) {

        static Preparo recusa(String motivo) {
            return new Preparo(ResultadoCaptura.recusado(motivo), null, null, "", "", "", "", "");
        }
    }

    /**
     * Celular do produtor na tag FB:, já com o nono dígito. Nulo no anúncio comum de grupo:
     * aí o telefone continua sendo o de quem enviou a mensagem.
     */
    static String telefoneDaCuradoria(ExtracaoAnuncio extraido, String texto) {
        String cru = null;
        if (extraido != null && "FACEBOOK".equals(extraido.origem())) {
            cru = extraido.telefoneVendedorExtraido();
        }
        if (cru == null || cru.isBlank()) {
            cru = digitosDaTagFb(texto).orElse(null);
        }
        if (cru == null) {
            return null;
        }
        String normalizado = PhoneUtils.normalizar(cru);
        return PhoneUtils.celularBrasileiro(normalizado) ? normalizado : null;
    }

    static Optional<String> digitosDaTagFb(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String linha = texto.lines().findFirst().orElse("").trim();
        if (linha.length() < 4 || !linha.regionMatches(true, 0, "FB:", 0, 3)) {
            return Optional.empty();
        }
        String digitos = linha.substring(3).replaceAll("\\D", "");
        return digitos.isBlank() ? Optional.empty() : Optional.of(digitos);
    }

    private static String descricaoPublica(String texto, boolean facebook) {
        if (texto == null || texto.isBlank()) {
            return "Foto publicada no grupo.";
        }
        String corpo = texto;
        if (facebook) {
            String[] linhas = texto.split("\\R", 2);
            corpo = linhas.length > 1 ? linhas[1].trim() : "";
            if (corpo.isBlank()) {
                corpo = "Anúncio de gado.";
            }
        }
        return limitar(corpo, 4000);
    }

    /**
     * Prioridade (anti-alucinação, ordem fixa):
     * (1) local explícito na MENSAGEM (município, depois estado/DF);
     * (2) só se a mensagem não tiver local → município no NOME DO GRUPO;
     * (3) senão {@link CidadesGoias#SEM_CIDADE} ("Local a combinar"). Nunca inventa Formosa.
     */
    static String cidadeDoTextoOuPadrao(
            String texto,
            String cidadeExtraida,
            String ufExtraida,
            String cidadePadrao,
            String ufPadrao,
            String nomeGrupo) {
        Optional<CidadesGoias.Local> municipioTexto = CidadesGoias.inferirMunicipio(texto);
        if (municipioTexto.isPresent()) {
            return montarCidade(municipioTexto.get().cidade(), municipioTexto.get().uf(), null, null);
        }
        Optional<CidadesGoias.Local> estadoTexto = CidadesGoias.inferirEstado(texto);
        if (estadoTexto.isPresent()) {
            return montarCidade(estadoTexto.get().cidade(), estadoTexto.get().uf(), null, null);
        }
        if (cidadeExtraida != null
                && !CidadesGoias.ehSemCidade(cidadeExtraida)
                && !CidadesGoias.ehNomeEstado(cidadeExtraida)
                && CidadesGoias.inferirMunicipio(cidadeExtraida).isPresent()) {
            return montarCidade(cidadeExtraida, ufExtraida, null, null);
        }
        if (cidadeExtraida != null
                && !CidadesGoias.ehSemCidade(cidadeExtraida)
                && CidadesGoias.ehNomeEstado(cidadeExtraida)) {
            return montarCidade(cidadeExtraida, ufExtraida, null, null);
        }
        Optional<CidadesGoias.Local> municipioGrupo = CidadesGoias.inferirMunicipio(nomeGrupo);
        if (municipioGrupo.isPresent()) {
            return montarCidade(municipioGrupo.get().cidade(), municipioGrupo.get().uf(), null, null);
        }
        return CidadesGoias.SEM_CIDADE;
    }

    private static String montarCidade(String cidade, String uf, String cidadePadrao, String ufPadrao) {
        String nome = cidade == null || cidade.isBlank() ? cidadePadrao : cidade.trim();
        if (CidadesGoias.ehSemCidade(nome)) {
            return CidadesGoias.SEM_CIDADE;
        }
        String sigla = uf == null || !uf.matches("(?i)[a-z]{2}")
                ? (ufPadrao != null && ufPadrao.matches("(?i)[a-z]{2}") ? ufPadrao.trim().toUpperCase(Locale.ROOT) : "GO")
                : uf.trim().toUpperCase(Locale.ROOT);
        if (nome.matches("(?i).* - [a-z]{2}$")) {
            return nome.substring(0, nome.length() - 5).trim()
                    + " - "
                    + nome.substring(nome.length() - 2).toUpperCase(Locale.ROOT);
        }
        return nome + " - " + sigla;
    }

    /** Aceita até 10 minutos. Timestamp em segundos ou milissegundos. */
    static boolean recente(Long timestamp, Instant agora) {
        if (timestamp == null || timestamp <= 0) {
            return false;
        }
        Instant enviado = timestamp > 10_000_000_000L
                ? Instant.ofEpochMilli(timestamp)
                : Instant.ofEpochSecond(timestamp);
        if (enviado.isAfter(agora.plusSeconds(120))) {
            return false;
        }
        return !enviado.isBefore(agora.minus(RECENCIA));
    }

    private Usuario contaCaptura() {
        return usuarios.findByWhatsapp(ContaCaptura.WHATSAPP).orElseGet(() -> {
            Usuario novo = new Usuario();
            novo.setNome("CompreGado");
            novo.setWhatsapp(ContaCaptura.WHATSAPP);
            novo.setSenha(passwordEncoder.encode(UUID.randomUUID().toString()));
            novo.setTelefoneVerificado(true);
            novo.setPremium(false);
            try {
                return usuarios.saveAndFlush(novo);
            } catch (DataIntegrityViolationException e) {
                return usuarios.findByWhatsapp(ContaCaptura.WHATSAPP).orElseThrow();
            }
        });
    }

    private static String jidDoGrupo(JsonNode data, JsonNode corpo) {
        String jid = data.path("key").path("remoteJid").asText("").trim();
        if (jid.endsWith("@g.us")) {
            return jid;
        }
        String informado = primeiroTexto(corpo, "whatsappJid", "grupo");
        return informado.endsWith("@g.us") ? informado : "";
    }

    private static String nomeDoGrupo(JsonNode data, JsonNode corpo) {
        String nome = primeiroTexto(corpo, "nomeGrupo", "groupName", "subject");
        if (!nome.isBlank()) {
            return nome;
        }
        nome = data.path("groupMetadata").path("subject").asText("").trim();
        if (!nome.isBlank()) {
            return nome;
        }
        return data.path("groupSubject").asText("").trim();
    }

    static boolean temMidia(JsonNode data, JsonNode corpo) {
        if (!urlDaFoto(data, corpo).isBlank() || !base64DaFoto(data, corpo).isBlank()) {
            return true;
        }
        JsonNode midias = corpo.path("midias");
        if (!midias.isArray()) {
            return false;
        }
        for (JsonNode midia : midias) {
            if (!midia.path("base64").asText("").isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copia a extração nova para um anúncio que já existe. Extração vazia não apaga título,
     * preço nem cidade que já estavam bons. Preço zero e cidade em branco também não substituem.
     */
    static void aplicarExtracao(
            Anuncio anuncio,
            ExtracaoAnuncio extraido,
            String cidadePadrao,
            String ufPadrao,
            String texto,
            boolean facebook) {
        aplicarExtracao(anuncio, extraido, cidadePadrao, ufPadrao, texto, facebook, null);
    }

    static void aplicarExtracao(
            Anuncio anuncio,
            ExtracaoAnuncio extraido,
            String cidadePadrao,
            String ufPadrao,
            String texto,
            boolean facebook,
            String nomeGrupo) {
        if (anuncio == null || extraido == null) {
            return;
        }
        if (extraido.titulo() != null && !extraido.titulo().isBlank()) {
            anuncio.setTitulo(limitar(extraido.titulo(), 180));
        }
        if (extraido.preco() != null && extraido.preco().signum() > 0) {
            anuncio.setPreco(extraido.preco());
        }
        if (extraido.raca() != null) {
            anuncio.setRaca(extraido.raca());
        }
        if (extraido.quantidade() != null) {
            anuncio.setQuantidade(extraido.quantidade());
        }
        anuncio.setCidade(cidadeDoTextoOuPadrao(
                texto, extraido.cidade(), extraido.uf(), cidadePadrao, ufPadrao, nomeGrupo));
        if (texto == null || texto.isBlank()) {
            return;
        }
        String descricao = descricaoPublica(texto, facebook);
        anuncio.setDescricao(descricao);
        anuncio.setCategoriaAnimal(categoriaDe(descricao));
        anuncio.setAptidao(aptidaoDe(descricao));
        anuncio.setTipoVenda(anuncio.getQuantidade() != null && anuncio.getQuantidade() > 1
                ? TipoVenda.LOTE
                : TipoVenda.CABECA);
    }

    static boolean tituloProntoParaAbordagem(Anuncio anuncio) {
        if (anuncio == null || anuncio.getTitulo() == null) {
            return false;
        }
        String titulo = anuncio.getTitulo().trim();
        return !titulo.isEmpty() && !"Anúncio de gado".equals(titulo);
    }

    /** Vitrine só com título real ou preço preenchido. Foto sozinha não publica "Anúncio de gado". */
    static boolean prontoParaVitrine(Anuncio anuncio) {
        if (anuncio == null) {
            return false;
        }
        boolean precoOk = anuncio.getPreco() != null && anuncio.getPreco().signum() > 0;
        return tituloProntoParaAbordagem(anuncio) || precoOk;
    }

    /**
     * Anexa fotos a um anúncio já gravado. Rascunho vira publicado só com título ou preço reais.
     * Devolve true só nessa virada, para não repetir o alerta de um lote que já estava no ar.
     */
    static boolean anexarFotos(Anuncio anuncio, List<String> fotosNovas) {
        if (anuncio == null || fotosNovas == null || fotosNovas.isEmpty()) {
            return false;
        }
        boolean eraRascunho = anuncio.getStatusProcessamento() == StatusProcessamento.RASCUNHO_SEM_FOTO;
        for (String foto : fotosNovas) {
            if (foto == null || foto.isBlank() || anuncio.getFotos().contains(foto)) {
                continue;
            }
            if (anuncio.getFotos().size() >= 5) {
                break;
            }
            anuncio.getFotos().add(foto);
        }
        if (anuncio.getFotos().isEmpty()) {
            return false;
        }
        if (anuncio.getUrlFotoPrincipal() == null || anuncio.getUrlFotoPrincipal().isBlank()) {
            anuncio.setUrlFotoPrincipal(anuncio.getFotos().get(0));
        }
        if (!eraRascunho || anuncio.getStatus() == AnuncioStatus.VENDIDO) {
            return false;
        }
        if (!prontoParaVitrine(anuncio)) {
            return false;
        }
        anuncio.setAtivo(true);
        anuncio.setStatus(AnuncioStatus.ATIVO);
        anuncio.setStatusProcessamento(StatusProcessamento.PUBLICADO);
        return true;
    }

    private List<String> coletarFotos(JsonNode data, JsonNode corpo) {
        List<String> fotos = fotosDasMidias(corpo);
        if (!fotos.isEmpty()) {
            return fotos;
        }
        String foto = guardarFoto(data, corpo);
        return foto == null ? List.of() : List.of(foto);
    }

    private List<String> fotosDasMidias(JsonNode corpo) {
        JsonNode midias = corpo.path("midias");
        if (!midias.isArray() || midias.isEmpty()) {
            return List.of();
        }
        List<String> fotos = new ArrayList<>();
        for (JsonNode midia : midias) {
            if (fotos.size() >= 5) {
                break;
            }
            String tipo = midia.path("tipo").asText("image/jpeg");
            String salva = salvarBase64(midia.path("base64").asText(""), tipo);
            if (salva != null) {
                fotos.add(salva);
            }
        }
        return fotos;
    }

    private String salvarBase64(String base64, String tipo) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        int virgula = base64.indexOf(',');
        if (base64.startsWith("data:") && virgula > 0) {
            base64 = base64.substring(virgula + 1);
        }
        if (base64.length() > MAXIMO_FOTO_BYTES * 2) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length < 32 || bytes.length > MAXIMO_FOTO_BYTES) {
                return null;
            }
            if (tipo == null || tipo.isBlank()) {
                tipo = "image/jpeg";
            }
            return armazenamento.salvarBytes(bytes, tipo);
        } catch (IllegalArgumentException e) {
            log.info("Base64 de foto ilegível na captura.");
            return null;
        }
    }

    private String guardarFoto(JsonNode data, JsonNode corpo) {
        String url = urlDaFoto(data, corpo);
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return limitar(url, 500);
        }
        String base64 = base64DaFoto(data, corpo);
        if (base64.isBlank()) {
            return null;
        }
        int virgula = base64.indexOf(',');
        if (base64.startsWith("data:") && virgula > 0) {
            base64 = base64.substring(virgula + 1);
        }
        if (base64.length() > MAXIMO_FOTO_BYTES * 2) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length < 32 || bytes.length > MAXIMO_FOTO_BYTES) {
                return null;
            }
            String tipo = primeiroTexto(data.path("message").path("imageMessage"), "mimetype");
            if (tipo.isBlank()) {
                tipo = "image/jpeg";
            }
            return armazenamento.salvarBytes(bytes, tipo);
        } catch (IllegalArgumentException e) {
            log.info("Base64 de foto ilegível na captura.");
            return null;
        }
    }

    private static String urlDaFoto(JsonNode data, JsonNode corpo) {
        String direta = primeiroTexto(corpo, "foto", "urlFoto", "imageUrl");
        if (direta.startsWith("http://") || direta.startsWith("https://")) {
            return direta;
        }
        JsonNode imagem = data.path("message").path("imageMessage");
        String evolution = imagem.path("url").asText("").trim();
        if (evolution.startsWith("http://") || evolution.startsWith("https://")) {
            return evolution;
        }
        return "";
    }

    private static String base64DaFoto(JsonNode data, JsonNode corpo) {
        String direta = primeiroTexto(corpo, "fotoBase64", "base64");
        if (!direta.isBlank()) {
            return direta;
        }
        String evolution = data.path("message").path("base64").asText("").trim();
        if (!evolution.isBlank()) {
            return evolution;
        }
        return data.path("message").path("imageMessage").path("base64").asText("").trim();
    }

    private static String textoDaMensagem(JsonNode data, JsonNode corpo) {
        String simples = primeiroTexto(corpo, "mensagem", "texto", "text");
        if (!simples.isBlank()) {
            return simples;
        }
        JsonNode message = data.path("message");
        String conversa = message.path("conversation").asText("");
        if (!conversa.isBlank()) {
            return conversa;
        }
        String estendido = message.path("extendedTextMessage").path("text").asText("");
        if (!estendido.isBlank()) {
            return estendido;
        }
        return message.path("imageMessage").path("caption").asText("").trim();
    }

    private static String remetenteDe(JsonNode data, JsonNode corpo) {
        String simples = primeiroTexto(corpo, "remetente", "from", "sender");
        if (!simples.isBlank()) {
            return simples;
        }
        String participante = data.path("key").path("participant").asText("");
        if (!participante.isBlank()) {
            return participante;
        }
        return data.path("participant").asText("");
    }

    private static Long timestampDe(JsonNode data, JsonNode corpo) {
        JsonNode direto = corpo.get("timestamp");
        if (direto != null && !direto.isNull()) {
            return lerEpoch(direto);
        }
        JsonNode evolution = data.get("messageTimestamp");
        return evolution == null ? null : lerEpoch(evolution);
    }

    private static Long lerEpoch(JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        }
        String texto = node.asText("").trim();
        if (texto.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String primeiroTexto(JsonNode node, String... campos) {
        for (String campo : campos) {
            String valor = node.path(campo).asText("").trim();
            if (!valor.isBlank()) {
                return valor;
            }
        }
        return "";
    }

    private static CategoriaAnimal categoriaDe(String texto) {
        String t = texto.toLowerCase(Locale.ROOT);
        if (t.contains("garrote") || t.contains("novilho")) {
            return CategoriaAnimal.GARROTE;
        }
        if (t.contains("touro") || t.contains("reprodutor")) {
            return CategoriaAnimal.TOURO;
        }
        if (t.contains("vaca") || t.contains("novilha")) {
            return CategoriaAnimal.VACA;
        }
        return CategoriaAnimal.BEZERRO;
    }

    private static Aptidao aptidaoDe(String texto) {
        String t = texto.toLowerCase(Locale.ROOT);
        if (t.contains("leite")) {
            return Aptidao.LEITE;
        }
        if (t.contains("genetica") || t.contains("genética") || t.contains("reprod")) {
            return Aptidao.REPRODUCAO;
        }
        return Aptidao.CORTE;
    }

    private static BigDecimal precoOuZero(BigDecimal preco) {
        if (preco == null || preco.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return preco;
    }

    private static String limitar(String texto, int maximo) {
        String limpo = texto == null ? "" : texto.trim();
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo);
    }

    public record ResultadoCaptura(boolean aceito, String motivo, Long anuncioId) {
        static ResultadoCaptura recusado(String motivo) {
            return new ResultadoCaptura(false, motivo, null);
        }
    }
}
