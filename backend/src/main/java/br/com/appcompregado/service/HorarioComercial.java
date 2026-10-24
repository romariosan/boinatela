package br.com.appcompregado.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Janela 8h–18h em America/Sao_Paulo. A primeira fala espera no mínimo duas horas. */
public final class HorarioComercial {

    public static final ZoneId ZONA = ZoneId.of(AssinaturaJobService.FUSO);
    private static final LocalTime ABRE = LocalTime.of(8, 0);
    private static final LocalTime FECHA = LocalTime.of(18, 0);

    private HorarioComercial() {
    }

    /** Instante da primeira mensagem: agora + 2 h, encaixado na janela, no máximo até as 18h do dia seguinte. */
    public static ZonedDateTime primeiraMensagem(ZonedDateTime agora) {
        ZonedDateTime base = agora.withZoneSameInstant(ZONA);
        ZonedDateTime minimo = base.plusHours(2);
        ZonedDateTime slot = encaixar(minimo);
        ZonedDateTime limite = base.toLocalDate().plusDays(1).atTime(FECHA).atZone(ZONA);
        if (slot.isAfter(limite) && !limite.isBefore(minimo)) {
            return limite;
        }
        return slot;
    }

    /** Próximo instante dentro da janela, sem a espera de duas horas. Vale para o lembrete do dia seguinte. */
    public static ZonedDateTime encaixar(ZonedDateTime instante) {
        ZonedDateTime local = instante.withZoneSameInstant(ZONA);
        LocalTime hora = local.toLocalTime();
        LocalDate dia = local.toLocalDate();
        if (!hora.isBefore(ABRE) && hora.isBefore(FECHA)) {
            return local;
        }
        if (hora.isBefore(ABRE)) {
            return dia.atTime(ABRE).atZone(ZONA);
        }
        return dia.plusDays(1).atTime(ABRE).atZone(ZONA);
    }

    public static String saudacao(int hora) {
        if (hora < 12) {
            return "Bom dia";
        }
        if (hora < 18) {
            return "Boa tarde";
        }
        return "Boa noite";
    }
}
