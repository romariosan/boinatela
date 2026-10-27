package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.StatusContato;
import br.com.appcompregado.domain.enums.StatusPublicacao;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.web.dto.AdminAnuncioEditarRequest;
import br.com.appcompregado.web.dto.AdminAnuncioResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnuncioService {

    private final AnuncioRepository anuncios;

    public AdminAnuncioService(AnuncioRepository anuncios) {
        this.anuncios = anuncios;
    }

    @Transactional(readOnly = true)
    public List<AdminAnuncioResponse> pendentesModeracao(Usuario admin) {
        exigirAdmin(admin);
        return anuncios.findPendentesPublicacao().stream().map(AdminAnuncioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAnuncioResponse> pendentesContato(Usuario admin) {
        exigirAdmin(admin);
        return anuncios.findAprovadosNaoContatados().stream().map(AdminAnuncioResponse::from).toList();
    }

    @Transactional
    public AdminAnuncioResponse alterarPublicacao(Usuario admin, Long id, StatusPublicacao status) {
        exigirAdmin(admin);
        if (status != StatusPublicacao.APROVADO && status != StatusPublicacao.REJEITADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use APROVADO ou REJEITADO");
        }
        Anuncio anuncio = anuncios.findByIdWithVendedor(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anúncio não encontrado"));
        if (status == StatusPublicacao.APROVADO) {
            // Legado SEM_CIDADE / vazio / A confirmar → "Local a combinar" (vale na vitrine).
            String cidadeAtual = anuncio.getCidade();
            if (cidadeAtual == null
                    || cidadeAtual.isBlank()
                    || cidadeAtual.trim().equalsIgnoreCase("SEM_CIDADE")
                    || cidadeAtual.trim().toLowerCase().startsWith("a confirmar")) {
                anuncio.setCidade(CidadesGoias.SEM_CIDADE);
            }
            if (!br.com.appcompregado.repository.AnuncioBusca.temProva(
                    anuncio.getUrlFotoPrincipal(), anuncio.getCidade())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Não dá para aprovar para a vitrine: falta foto");
            }
        }
        anuncio.setStatusPublicacao(status);
        if (status == StatusPublicacao.APROVADO
                && anuncio.getStatusContato() == null) {
            anuncio.setStatusContato(StatusContato.NAO_CONTATADO);
        }
        return AdminAnuncioResponse.from(anuncios.save(anuncio));
    }

    @Transactional
    public AdminAnuncioResponse alterarContato(Usuario admin, Long id, StatusContato status) {
        exigirAdmin(admin);
        if (status != StatusContato.CONTATADO && status != StatusContato.NAO_CONTATADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use CONTATADO ou NAO_CONTATADO");
        }
        Anuncio anuncio = anuncios.findByIdWithVendedor(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anúncio não encontrado"));
        if (anuncio.getStatusPublicacao() != StatusPublicacao.APROVADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só anúncio aprovado entra na prospecção");
        }
        anuncio.setStatusContato(status);
        return AdminAnuncioResponse.from(anuncios.save(anuncio));
    }

    /**
     * Edita campos do lote sem mudar status de publicação/contato.
     * Cidade em branco ou legado SEM_CIDADE vira {@link CidadesGoias#SEM_CIDADE}.
     */
    @Transactional
    public AdminAnuncioResponse editar(Usuario admin, Long id, AdminAnuncioEditarRequest body) {
        exigirAdmin(admin);
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corpo obrigatório");
        }
        Anuncio anuncio = anuncios.findByIdWithVendedor(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anúncio não encontrado"));
        if (body.titulo() != null) {
            String titulo = body.titulo().trim();
            if (titulo.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Título não pode ficar vazio");
            }
            anuncio.setTitulo(titulo);
        }
        if (body.descricao() != null) {
            anuncio.setDescricao(body.descricao().trim());
        }
        if (body.preco() != null) {
            if (body.preco().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço inválido");
            }
            anuncio.setPreco(body.preco());
        }
        if (body.cidade() != null) {
            anuncio.setCidade(normalizarCidadeAdmin(body.cidade()));
        }
        if (body.quantidade() != null) {
            if (body.quantidade() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade inválida");
            }
            anuncio.setQuantidade(body.quantidade());
        }
        if (body.categoriaAnimal() != null) {
            anuncio.setCategoriaAnimal(body.categoriaAnimal());
        }
        if (body.aptidao() != null) {
            anuncio.setAptidao(body.aptidao());
        }
        if (body.raca() != null) {
            anuncio.setRaca(body.raca());
        }
        return AdminAnuncioResponse.from(anuncios.save(anuncio));
    }

    static String normalizarCidadeAdmin(String cidade) {
        if (cidade == null || cidade.isBlank()) {
            return CidadesGoias.SEM_CIDADE;
        }
        String limpa = cidade.trim();
        if (limpa.equalsIgnoreCase("SEM_CIDADE")
                || limpa.toLowerCase().startsWith("sem_cidade")
                || limpa.toLowerCase().startsWith("a confirmar")) {
            return CidadesGoias.SEM_CIDADE;
        }
        return limpa;
    }

    static void exigirAdmin(Usuario usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login");
        }
        if (!usuario.isAdmin()) {
            // 400: o app trata 403 de sessão e desloga.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sem permissão de administrador");
        }
    }
}
