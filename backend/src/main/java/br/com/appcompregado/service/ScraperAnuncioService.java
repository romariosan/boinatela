package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.GrupoWhatsapp;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.TipoVenda;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.service.GeminiExtractionService.ExtracaoAnuncio;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final GeminiExtractionService gemini;
    private final PasswordEncoder passwordEncoder;
    private final AlertaNotificador radar;
    private final GrupoWhatsappService grupos;
    private final ArmazenamentoLocalService armazenamento;
    private final LeadAbordagemService abordagem;
    private final TransactionTemplate transacao;

    public ScraperAnuncioService(
            AnuncioRepository anuncios,
            UsuarioRepository usuarios,
            GeminiExtractionService gemini,
            PasswordEncoder passwordEncoder,
            AlertaNotificador radar,
            GrupoWhatsappService grupos,
            ArmazenamentoLocalService armazenamento,
            LeadAbordagemService abordagem,
            TransactionTemplate transacao) {
        this.anuncios = anuncios;
        this.usuarios = usuarios;
        this.gemini = gemini;
        this.passwordEncoder = passwordEncoder;
        this.radar = radar;
        this.grupos = grupos;
        this.armazenamento = armazenamento;
        this.abordagem = abordagem;
        this.transacao = transacao;
    }

    public ResultadoCaptura ingerir(JsonNode corpo) {
        Preparo preparo = preparar(corpo);
        if (preparo.recusa() != null) {
            return preparo.recusa();
        }
        ExtracaoAnuncio extraido = preparo.texto().isBlank()
                ? null
                : gemini.extrair(preparo.texto(), preparo.cidadePadrao(), preparo.ufPadrao()).orElse(null);
        ExtracaoAnuncio lido = extraido;
        ResultadoCaptura gravado = transacao.execute(status -> gravar(preparo, lido));
        return gravado == null ? ResultadoCaptura.recusado("falha ao gravar") : gravado;
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
        GrupoWhatsapp grupo = grupos.resolver(jid, nomeDoGrupo(data, corpo));
        return new Preparo(
                null,
                data,
                corpo,
                texto.isBlank() ? "" : texto,
                telefoneRemetente,
                grupo.getCidadePadrao(),
                grupo.getUfPadrao(),
                grupo.getNomeGrupo());
    }

    private ResultadoCaptura gravar(Preparo preparo, ExtracaoAnuncio extraido) {
        JsonNode data = preparo.data();
        JsonNode corpo = preparo.corpo();
        String textoCompleto = preparo.texto();
        String telefoneCurado = telefoneDaCuradoria(extraido, textoCompleto);
        String telefone = telefoneCurado == null ? preparo.telefoneRemetente() : telefoneCurado;
        String origem = telefoneCurado == null ? "WHATSAPP" : "FACEBOOK";

        Optional<Anuncio> repetido = anuncios
                .findFirstByTelefoneVendedorOriginalAndDataCapturaAfterOrderByDataCapturaDesc(
                        telefone, LocalDateTime.now().minusDays(14));
        if (repetido.isPresent()) {
            Anuncio existente = repetido.get();
            existente.setDataCaptura(LocalDateTime.now());
            return new ResultadoCaptura(true, "anúncio atualizado", existente.getId());
        }

        String descricao = descricaoPublica(textoCompleto, "FACEBOOK".equals(origem));
        Anuncio anuncio = new Anuncio();
        if (extraido == null) {
            anuncio.setTitulo("Anúncio de gado");
            anuncio.setPreco(BigDecimal.ZERO);
            anuncio.setRaca(Raca.OUTRA);
            anuncio.setQuantidade(null);
            anuncio.setCidade(preparo.cidadePadrao() + " - " + preparo.ufPadrao());
            log.info("Captura sem Gemini: anúncio gravado com o texto original.");
        } else {
            anuncio.setTitulo(limitar(extraido.titulo(), 180));
            anuncio.setPreco(precoOuZero(extraido.preco()));
            anuncio.setRaca(extraido.raca() == null ? Raca.OUTRA : extraido.raca());
            anuncio.setQuantidade(extraido.quantidade());
            anuncio.setCidade(montarCidade(extraido.cidade(), extraido.uf(), preparo.cidadePadrao(), preparo.ufPadrao()));
        }
        anuncio.setDescricao(descricao);
        anuncio.setCategoriaAnimal(categoriaDe(descricao));
        anuncio.setAptidao(aptidaoDe(descricao));
        anuncio.setTipoVenda(anuncio.getQuantidade() != null && anuncio.getQuantidade() > 1
                ? TipoVenda.LOTE
                : TipoVenda.CABECA);
        anuncio.setStatus(AnuncioStatus.ATIVO);
        anuncio.setTelefoneVendedorOriginal(telefone);
        anuncio.setOrigem(origem);
        anuncio.setReivindicado(false);
        anuncio.setDataCaptura(LocalDateTime.now());
        anuncio.setVendedor(contaCaptura());
        List<String> fotos = fotosDasMidias(corpo);
        if (fotos.isEmpty()) {
            String foto = guardarFoto(data, corpo);
            if (foto != null) {
                fotos = List.of(foto);
            }
        }
        if (fotos.isEmpty()) {
            anuncio.setAtivo(false);
            anuncio.setStatusProcessamento(StatusProcessamento.RASCUNHO_SEM_FOTO);
        } else {
            anuncio.setAtivo(true);
            anuncio.setStatusProcessamento(StatusProcessamento.PUBLICADO);
            anuncio.setUrlFotoPrincipal(fotos.get(0));
            anuncio.getFotos().addAll(fotos);
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

    private static String montarCidade(String cidade, String uf, String cidadePadrao, String ufPadrao) {
        String nome = cidade == null || cidade.isBlank() ? cidadePadrao : cidade.trim();
        String sigla = uf == null || !uf.matches("(?i)[a-z]{2}") ? ufPadrao : uf.trim().toUpperCase(Locale.ROOT);
        if (nome.matches("(?i).* - [a-z]{2}$")) {
            return nome.substring(0, nome.length() - 5).trim() + " - " + nome.substring(nome.length() - 2).toUpperCase(Locale.ROOT);
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

    private static boolean temMidia(JsonNode data, JsonNode corpo) {
        return !urlDaFoto(data, corpo).isBlank() || !base64DaFoto(data, corpo).isBlank();
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
