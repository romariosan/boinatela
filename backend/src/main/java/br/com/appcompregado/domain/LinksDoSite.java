package br.com.appcompregado.domain;

/** Base do site, preenchida na subida a partir de compregado.web.base-url. */
public final class LinksDoSite {

    private static volatile String baseUrl = "https://appcompregado.com.br";

    private LinksDoSite() {
    }

    public static void definirBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String limpa = url.trim();
        while (limpa.endsWith("/")) {
            limpa = limpa.substring(0, limpa.length() - 1);
        }
        baseUrl = limpa;
    }

    public static String baseUrl() {
        return baseUrl;
    }
}
