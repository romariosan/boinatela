package br.com.appcompregado.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Forma única de gravar e comparar celular brasileiro. Tira máscara, JID do WhatsApp e DDI 55, e
 * coloca o nono dígito nos celulares antigos (10 dígitos) para o mesmo aparelho não virar dois cadastros.
 */
public final class PhoneUtils {

    private PhoneUtils() {
    }

    public static String normalizar(String numero) {
        if (numero == null || numero.isBlank()) {
            return "";
        }
        String digitos = numero.split("@", 2)[0].split(":", 2)[0].replaceAll("\\D", "");
        if (digitos.startsWith("55") && (digitos.length() == 12 || digitos.length() == 13)) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() == 10 && digitos.charAt(2) >= '6' && digitos.charAt(2) <= '9') {
            return digitos.substring(0, 2) + "9" + digitos.substring(2);
        }
        return digitos;
    }

    public static boolean mesmoNumero(String a, String b) {
        String na = normalizar(a);
        String nb = normalizar(b);
        return na.length() >= 10 && na.equals(nb);
    }

    /** DDD + 9 + 8 dígitos. "00000000000" e fixo não passam: não são WhatsApp de produtor. */
    public static boolean celularBrasileiro(String numero) {
        String canonico = normalizar(numero);
        if (canonico.length() != 11 || canonico.charAt(2) != '9') {
            return false;
        }
        int ddd = Integer.parseInt(canonico.substring(0, 2));
        return ddd >= 11 && ddd <= 99;
    }

    /** Cadastros gravados antes da padronização, sem o nono dígito. */
    public static List<String> variantesLegadas(String numero) {
        String canonico = normalizar(numero);
        List<String> variantes = new ArrayList<>();
        if (canonico.length() == 11 && canonico.charAt(2) == '9') {
            variantes.add(canonico.substring(0, 2) + canonico.substring(3));
        }
        return variantes;
    }
}
