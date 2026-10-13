package com.boinatela.service;

/**
 * Compara números de celular brasileiros vindos de fontes diferentes: o cadastro guarda DDD + número
 * ("61999999999"), o WhatsApp manda o JID com DDI ("5561999999999@s.whatsapp.net") e, em contas antigas,
 * sem o nono dígito ("556199999999").
 */
final class TelefoneBr {

    private TelefoneBr() {
    }

    static boolean mesmoNumero(String a, String b) {
        String na = semNonoDigito(nacional(a));
        String nb = semNonoDigito(nacional(b));
        return na.length() >= 10 && na.equals(nb);
    }

    static String nacional(String numero) {
        if (numero == null) {
            return "";
        }
        // JID de multi-dispositivo: "5561999999999:12@s.whatsapp.net" — o ":12" é o aparelho, não o número.
        String digitos = numero.split("@", 2)[0].split(":", 2)[0].replaceAll("\\D", "");
        if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")) {
            return digitos.substring(2);
        }
        return digitos;
    }

    private static String semNonoDigito(String nacional) {
        if (nacional.length() == 11 && nacional.charAt(2) == '9') {
            return nacional.substring(0, 2) + nacional.substring(3);
        }
        return nacional;
    }
}
