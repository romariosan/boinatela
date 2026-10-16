package br.com.appcompregado.web;

import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.service.AnuncioService;
import br.com.appcompregado.service.InteracaoContatoService;
import br.com.appcompregado.service.LimiteAnunciosAtingidoException;
import br.com.appcompregado.service.ReivindicacaoService;
import br.com.appcompregado.web.dto.AnuncioRequest;
import br.com.appcompregado.web.dto.AnuncioResponse;
import br.com.appcompregado.web.dto.CategoriaEstoqueResponse;
import br.com.appcompregado.web.dto.ContatoResponse;
import br.com.appcompregado.web.dto.ReivindicarRequest;
import br.com.appcompregado.web.dto.StatusRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AnuncioController {

    private final AnuncioService anuncioService;
    private final ReivindicacaoService reivindicacao;
    private final InteracaoContatoService interacao;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public AnuncioController(
            AnuncioService anuncioService,
            ReivindicacaoService reivindicacao,
            InteracaoContatoService interacao,
            ObjectMapper objectMapper,
            Validator validator) {
        this.anuncioService = anuncioService;
        this.reivindicacao = reivindicacao;
        this.interacao = interacao;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping("/anuncios")
    public List<AnuncioResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) CategoriaAnimal categoria,
            @RequestParam(required = false) Aptidao aptidao,
            @RequestParam(required = false) Raca raca) {
        return anuncioService.listar(q, cidade, uf, categoria, aptidao, raca);
    }

    @GetMapping("/anuncios/meus")
    public List<AnuncioResponse> meus(@AuthenticationPrincipal Usuario vendedor) {
        return anuncioService.doVendedor(vendedor);
    }

    @GetMapping("/anuncios/{id}")
    public AnuncioResponse buscar(@PathVariable Long id) {
        return anuncioService.buscarPorId(id);
    }

    @PostMapping("/anuncios/{id}/reivindicar")
    public AnuncioResponse reivindicar(@PathVariable Long id, @Valid @RequestBody ReivindicarRequest request) {
        return reivindicacao.reivindicar(id, request.whatsapp(), request.otp());
    }

    @PostMapping("/anuncios/{id}/registrar-intere-contato")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarInteresse(@PathVariable Long id) {
        interacao.registrar(id);
    }

    @PutMapping("/anuncios/{id}/marcar-vendido")
    public AnuncioResponse marcarVendido(
            @PathVariable Long id,
            @RequestParam(required = false) String token,
            @AuthenticationPrincipal Usuario usuario) {
        return anuncioService.marcarVendido(id, usuario, token);
    }

    @GetMapping("/categorias")
    public List<CategoriaEstoqueResponse> categorias() {
        return anuncioService.categoriasComEstoque();
    }

    @GetMapping("/cidades")
    public List<String> cidades() {
        return anuncioService.cidadesComEstoque();
    }

    @PostMapping(path = "/anuncios", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AnuncioResponse criar(
            @RequestPart("imagens") List<MultipartFile> imagens,
            @RequestPart("dados") String anuncioJson,
            @AuthenticationPrincipal Usuario vendedor) {
        return anuncioService.criar(lerDados(anuncioJson), imagens, vendedor);
    }

    @PutMapping(path = "/anuncios/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnuncioResponse atualizar(
            @PathVariable Long id,
            @RequestPart(value = "imagens", required = false) List<MultipartFile> imagens,
            @RequestPart("dados") String anuncioJson,
            @AuthenticationPrincipal Usuario usuario) {
        return anuncioService.atualizar(id, lerDados(anuncioJson), imagens, usuario);
    }

    @DeleteMapping("/anuncios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        anuncioService.excluir(id, usuario);
    }

    @PatchMapping("/anuncios/{id}/status")
    public AnuncioResponse alterarStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request,
            @AuthenticationPrincipal Usuario usuario) {
        return anuncioService.alterarStatus(id, request.status(), usuario);
    }

    @PostMapping("/anuncios/{id}/contato")
    public ContatoResponse contato(@PathVariable Long id, @AuthenticationPrincipal Usuario ignorado) {
        return anuncioService.contato(id);
    }

    /** 400 e não 403: o app trata 403 como sessão inválida e desloga. */
    @ExceptionHandler(LimiteAnunciosAtingidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> limiteAtingido(LimiteAnunciosAtingidoException e) {
        Map<String, String> corpo = new LinkedHashMap<>();
        corpo.put("erro", "LIMITE_ANUNCIOS_ATINGIDO");
        corpo.put("mensagem", e.getMessage());
        return corpo;
    }

    private AnuncioRequest lerDados(String anuncioJson) {
        AnuncioRequest request;
        try {
            request = objectMapper.readValue(anuncioJson, AnuncioRequest.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados do anúncio inválidos.");
        }
        Set<ConstraintViolation<AnuncioRequest>> erros = validator.validate(request);
        if (!erros.isEmpty()) {
            String campos = erros.stream()
                    .map(erro -> erro.getPropertyPath().toString())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campos inválidos: " + campos);
        }
        return request;
    }
}
