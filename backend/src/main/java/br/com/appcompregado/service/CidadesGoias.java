package br.com.appcompregado.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Municípios de Goiás, Entorno do DF e cidades de MG vizinhas, além de estados
 * citados sem município (Minas Gerais, Distrito Federal).
 * "Goiás" (a cidade) fica de fora: quase todo grupo escreve o estado e cairia na cidade errada.
 * Sem match em texto nem no grupo → {@link #SEM_CIDADE} = "Local a combinar" (nunca inventa Formosa).
 */
public final class CidadesGoias {

    /**
     * Quando não há município/estado explícito no grupo nem na mensagem.
     * Valor gravado: "Local a combinar" (legado SEM_CIDADE / "A confirmar" ainda é reconhecido).
     */
    public static final String SEM_CIDADE = "Local a combinar";

    public record Local(String cidade, String uf) {
    }

    private static final List<Local> MUNICIPIOS = new ArrayList<>();
    /**
     * Apelidos / typos do produtor → município oficial.
     * Ex.: "cabeceira go" (sem S) → Cabeceiras - GO. Chaves mais longas primeiro.
     */
    private static final Map<String, Local> APELIDOS = new LinkedHashMap<>();
    /** Estado citado no texto (ex.: "em Minas Gerais", "estou no DF") — só sem município. */
    private static final List<Local> ESTADOS = List.of(
            new Local("Minas Gerais", "MG"),
            new Local("Distrito Federal", "DF"));

    static {
        Local cabeceiras = new Local("Cabeceiras", "GO");
        // Ordem: frases mais longas antes de "cabeceira" solto.
        APELIDOS.put("cabeceiras de goias", cabeceiras);
        APELIDOS.put("cabeceiras goias", cabeceiras);
        APELIDOS.put("cabeceiras go", cabeceiras);
        APELIDOS.put("cabeceira de goias", cabeceiras);
        APELIDOS.put("cabeceira goias", cabeceiras);
        APELIDOS.put("cabeceira go", cabeceiras);
        APELIDOS.put("cabeceira", cabeceiras);

        for (String cidade : List.of(
                "Abadia de Goiás", "Abadiânia", "Acreúna", "Adelândia", "Água Fria de Goiás", "Água Limpa",
                "Águas Lindas de Goiás", "Alexânia", "Aloândia", "Alto Horizonte", "Alto Paraíso de Goiás",
                "Alvorada do Norte", "Amaralina", "Americano do Brasil", "Amorinópolis", "Anápolis", "Anhanguera",
                "Anicuns", "Aparecida de Goiânia", "Aparecida do Rio Doce", "Aporé", "Araçu", "Aragarças",
                "Aragoiânia", "Araguapaz", "Arenópolis", "Aruanã", "Aurilândia", "Avelinópolis", "Baliza",
                "Barro Alto", "Bela Vista de Goiás", "Bom Jardim de Goiás", "Bom Jesus de Goiás", "Bonfinópolis",
                "Bonópolis", "Brazabrantes", "Britânia", "Buriti Alegre", "Buriti de Goiás", "Buritinópolis",
                "Cabeceiras", "Cachoeira Alta", "Cachoeira de Goiás", "Cachoeira Dourada", "Caçu", "Caiapônia",
                "Caldas Novas", "Caldazinha", "Campestre de Goiás", "Campinaçu", "Campinorte", "Campo Alegre de Goiás",
                "Campo Limpo de Goiás", "Campos Belos", "Campos Verdes", "Carmo do Rio Verde", "Castelândia",
                "Catalão", "Caturaí", "Cavalcante", "Ceres", "Cezarina", "Chapadão do Céu", "Cidade Ocidental",
                "Cocalzinho de Goiás", "Colinas do Sul", "Córrego do Ouro", "Corumbá de Goiás", "Corumbaíba",
                "Cristalina", "Cristianópolis", "Crixás", "Cromínia", "Cumari", "Damianópolis", "Damolândia",
                "Davinópolis", "Diorama", "Divinópolis de Goiás", "Doverlândia", "Edealina", "Edéia",
                "Estrela do Norte", "Faina", "Fazenda Nova", "Firminópolis", "Flores de Goiás", "Formosa",
                "Formoso", "Gameleira de Goiás", "Goianápolis", "Goiandira", "Goianésia", "Goiânia", "Goianira",
                "Goiatuba", "Gouvelândia", "Guapó", "Guaraíta", "Guarani de Goiás", "Guarinos", "Heitoraí",
                "Hidrolândia", "Hidrolina", "Iaciara", "Inaciolândia", "Indiara", "Inhumas", "Ipameri",
                "Ipiranga de Goiás", "Iporá", "Israelândia", "Itaberaí", "Itaguari", "Itaguaru", "Itajá",
                "Itapaci", "Itapirapuã", "Itapuranga", "Itarumã", "Itauçu", "Itumbiara", "Ivolândia", "Jandaia",
                "Jaraguá", "Jataí", "Jaupaci", "Jesúpolis", "Joviânia", "Jussara", "Lagoa Santa",
                "Leopoldo de Bulhões", "Luziânia", "Mairipotaba", "Mambaí", "Mara Rosa", "Marzagão", "Matrinchã",
                "Maurilândia", "Mimoso de Goiás", "Minaçu", "Mineiros", "Moiporá", "Monte Alegre de Goiás",
                "Montes Claros de Goiás", "Montividiu", "Montividiu do Norte", "Morrinhos", "Morro Agudo de Goiás",
                "Mossâmedes", "Mozarlândia", "Mundo Novo", "Mutunópolis", "Nazário", "Nerópolis", "Niquelândia",
                "Nova América", "Nova Aurora", "Nova Crixás", "Nova Glória", "Nova Iguaçu de Goiás", "Nova Roma",
                "Nova Veneza", "Novo Brasil", "Novo Gama", "Novo Planalto", "Orizona", "Ouro Verde de Goiás",
                "Ouvidor", "Padre Bernardo", "Palestina de Goiás", "Palmeiras de Goiás", "Palmelo", "Palminópolis",
                "Panamá", "Paranaiguara", "Paraúna", "Perolândia", "Petrolina de Goiás", "Pilar de Goiás",
                "Piracanjuba", "Piranhas", "Pirenópolis", "Pires do Rio", "Pontalina", "Porangatu", "Porteirão",
                "Portelândia", "Posse", "Professor Jamil", "Quirinópolis", "Rialma", "Rianápolis", "Rio Quente",
                "Rio Verde", "Rubiataba", "Sanclerlândia", "Santa Bárbara de Goiás", "Santa Cruz de Goiás",
                "Santa Fé de Goiás", "Santa Helena de Goiás", "Santa Isabel", "Santa Rita do Araguaia",
                "Santa Rita do Novo Destino", "Santa Rosa de Goiás", "Santa Tereza de Goiás", "Santa Terezinha de Goiás",
                "Santo Antônio da Barra", "Santo Antônio de Goiás", "Santo Antônio do Descoberto", "São Domingos",
                "São Francisco de Goiás", "São João d'Aliança", "São João da Paraúna", "São Luís de Montes Belos",
                "São Luíz do Norte", "São Miguel do Araguaia", "São Miguel do Passa Quatro", "São Patrício",
                "São Simão", "Senador Canedo", "Serranópolis", "Silvânia", "Simolândia", "Sítio d'Abadia",
                "Taquaral de Goiás", "Teresina de Goiás", "Terezópolis de Goiás", "Três Ranchos", "Trindade",
                "Trombas", "Turvânia", "Turvelândia", "Uirapuru", "Uruaçu", "Uruana", "Urutaí",
                "Valparaíso de Goiás", "Varjão", "Vianópolis", "Vicentinópolis", "Vila Boa", "Vila Propício")) {
            MUNICIPIOS.add(new Local(cidade, "GO"));
        }
        for (String cidade : List.of(
                "Brasília", "Guará", "Ceilândia", "Taguatinga", "Samambaia", "Planaltina", "Gama",
                "Sobradinho", "Brazlândia", "Santa Maria", "São Sebastião", "Recanto das Emas",
                "Riacho Fundo", "Candangolândia", "Núcleo Bandeirante", "Lago Sul", "Lago Norte",
                "Cruzeiro", "Águas Claras", "Vicente Pires", "Sudoeste", "Park Way", "Arniqueira",
                "Sol Nascente", "Jardim Botânico", "Fercal")) {
            MUNICIPIOS.add(new Local(cidade, "DF"));
        }
        for (String cidade : List.of(
                "Unaí", "Paracatu", "João Pinheiro", "Buritis", "Arinos", "Cabeceira Grande",
                "Bonfinópolis de Minas", "Dom Bosco", "Uberaba", "Uberlândia", "Patos de Minas",
                "Monte Carmelo", "Coromandel", "Vazante", "Lagamar")) {
            MUNICIPIOS.add(new Local(cidade, "MG"));
        }
    }

    private CidadesGoias() {
    }

    /** A cidade mais específica cujo nome aparece inteiro no título do grupo. */
    public static Optional<String> inferir(String nomeGrupo) {
        return inferirLocal(nomeGrupo).map(Local::cidade);
    }

    /**
     * Cidade e UF lidas do texto (grupo ou anúncio). Município ganha do estado.
     * "Minas Gerais" / "em MG" viram o estado quando não há município.
     */
    public static Optional<Local> inferirLocal(String textoBruto) {
        Optional<Local> municipio = inferirMunicipio(textoBruto);
        if (municipio.isPresent()) {
            return municipio;
        }
        return inferirEstado(textoBruto);
    }

    /** Só município (ex.: Luziânia, Cabeceiras). Não devolve estado. */
    public static Optional<Local> inferirMunicipio(String textoBruto) {
        if (textoBruto == null || textoBruto.isBlank()) {
            return Optional.empty();
        }
        String texto = normalizar(textoBruto);
        Optional<Local> peloNome = MUNICIPIOS.stream()
                .filter(local -> contemPalavra(texto, normalizar(local.cidade())))
                .max(Comparator.comparingInt(local -> normalizar(local.cidade()).length()));
        Optional<Local> peloApelido = Optional.empty();
        int melhorApelido = -1;
        for (Map.Entry<String, Local> entrada : APELIDOS.entrySet()) {
            if (contemPalavra(texto, entrada.getKey()) && entrada.getKey().length() > melhorApelido) {
                melhorApelido = entrada.getKey().length();
                peloApelido = Optional.of(entrada.getValue());
            }
        }
        // "Cabeceira Grande" (MG) ganha de apelido "cabeceira" → Cabeceiras.
        if (peloNome.isPresent() && peloApelido.isPresent()) {
            int lenNome = normalizar(peloNome.get().cidade()).length();
            return lenNome >= melhorApelido ? peloNome : peloApelido;
        }
        if (peloNome.isPresent()) {
            return peloNome;
        }
        return peloApelido;
    }

    /** Estado sem município (ex.: "em Minas Gerais", "estou no DF", "MG"). */
    public static Optional<Local> inferirEstado(String textoBruto) {
        if (textoBruto == null || textoBruto.isBlank()) {
            return Optional.empty();
        }
        String texto = normalizar(textoBruto);
        Optional<Local> estado = ESTADOS.stream()
                .filter(local -> contemPalavra(texto, normalizar(local.cidade())))
                .max(Comparator.comparingInt(local -> normalizar(local.cidade()).length()));
        if (estado.isPresent()) {
            return estado;
        }
        if (contemPalavra(texto, "mg")) {
            return Optional.of(new Local("Minas Gerais", "MG"));
        }
        if (contemPalavra(texto, "df")) {
            return Optional.of(new Local("Distrito Federal", "DF"));
        }
        return Optional.empty();
    }

    /** True se o nome é só o estado (ex.: Minas Gerais / DF), não um município. */
    public static boolean ehNomeEstado(String nome) {
        if (nome == null || nome.isBlank() || ehSemCidade(nome)) {
            return false;
        }
        String normal = normalizar(nome.replaceAll("(?i)\\s*-\\s*[a-z]{2}$", "").trim());
        return ESTADOS.stream().anyMatch(local -> normalizar(local.cidade()).equals(normal))
                || normal.equals("mg")
                || normal.equals("df");
    }

    /** True para nulo, vazio, "Local a combinar", legado SEM_CIDADE ou "A confirmar". */
    public static boolean ehSemCidade(String cidade) {
        if (cidade == null || cidade.isBlank()) {
            return true;
        }
        String normal = cidade.trim().toLowerCase(Locale.ROOT);
        return normal.equals("sem_cidade")
                || normal.startsWith("sem_cidade")
                || normal.startsWith("a confirmar")
                || normal.equals("local a combinar")
                || normal.startsWith("local a combinar");
    }

    static String normalizar(String valor) {
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "");
    }

    private static boolean contemPalavra(String texto, String alvo) {
        int de = 0;
        while (de <= texto.length() - alvo.length()) {
            int indice = texto.indexOf(alvo, de);
            if (indice < 0) {
                return false;
            }
            boolean antes = indice == 0 || !Character.isLetterOrDigit(texto.charAt(indice - 1));
            int fim = indice + alvo.length();
            boolean depois = fim >= texto.length() || !Character.isLetterOrDigit(texto.charAt(fim));
            if (antes && depois) {
                return true;
            }
            de = indice + 1;
        }
        return false;
    }
}
