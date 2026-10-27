package br.com.appcompregado.web;

import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.service.AdminAnuncioService;
import br.com.appcompregado.web.dto.AdminAnuncioEditarRequest;
import br.com.appcompregado.web.dto.AdminAnuncioResponse;
import br.com.appcompregado.web.dto.StatusContatoRequest;
import br.com.appcompregado.web.dto.StatusPublicacaoRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/anuncios")
public class AdminAnuncioController {

    private final AdminAnuncioService admin;

    public AdminAnuncioController(AdminAnuncioService admin) {
        this.admin = admin;
    }

    @GetMapping("/pendentes")
    public List<AdminAnuncioResponse> pendentes(@AuthenticationPrincipal Usuario usuario) {
        return admin.pendentesModeracao(usuario);
    }

    @GetMapping("/prospeccao")
    public List<AdminAnuncioResponse> prospeccao(@AuthenticationPrincipal Usuario usuario) {
        return admin.pendentesContato(usuario);
    }

    @PatchMapping("/{id}/publicacao")
    public AdminAnuncioResponse publicacao(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable Long id,
            @Valid @RequestBody StatusPublicacaoRequest body) {
        return admin.alterarPublicacao(usuario, id, body.status());
    }

    @PatchMapping("/{id}/contato")
    public AdminAnuncioResponse contato(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable Long id,
            @Valid @RequestBody StatusContatoRequest body) {
        return admin.alterarContato(usuario, id, body.status());
    }

    @PatchMapping("/{id}")
    public AdminAnuncioResponse editar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable Long id,
            @RequestBody AdminAnuncioEditarRequest body) {
        return admin.editar(usuario, id, body);
    }
}
