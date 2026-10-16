package br.com.appcompregado.web;

import br.com.appcompregado.service.AlertaBuscaService;
import br.com.appcompregado.web.dto.AlertaBuscaRequest;
import br.com.appcompregado.web.dto.AlertaBuscaResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaBuscaService alertas;

    public AlertaController(AlertaBuscaService alertas) {
        this.alertas = alertas;
    }

    @PostMapping
    public AlertaBuscaResponse criar(@Valid @RequestBody AlertaBuscaRequest request) {
        return AlertaBuscaResponse.from(alertas.criar(request));
    }
}
