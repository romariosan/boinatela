package br.com.appcompregado.service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Municípios de Goiás para ler o nome do grupo. "Goiás" (a cidade) fica de fora: quase todo grupo
 * escreve o estado e cairia na cidade errada. Sem match, quem chama usa Formosa.
 */
public final class CidadesGoias {

    private static final List<String> MUNICIPIOS = List.of(
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
            "Valparaíso de Goiás", "Varjão", "Vianópolis", "Vicentinópolis", "Vila Boa", "Vila Propício");

    private CidadesGoias() {
    }

    /** A cidade mais específica cujo nome aparece inteiro no título do grupo. */
    public static Optional<String> inferir(String nomeGrupo) {
        if (nomeGrupo == null || nomeGrupo.isBlank()) {
            return Optional.empty();
        }
        String texto = normalizar(nomeGrupo);
        return MUNICIPIOS.stream()
                .filter(cidade -> contemPalavra(texto, normalizar(cidade)))
                .max(Comparator.comparingInt(cidade -> normalizar(cidade).length()));
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
