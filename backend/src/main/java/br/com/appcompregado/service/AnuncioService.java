package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.repository.AnuncioBusca;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.web.dto.AnuncioRequest;
import br.com.appcompregado.web.dto.AnuncioResponse;
import br.com.appcompregado.web.dto.CategoriaEstoqueResponse;
import br.com.appcompregado.web.dto.ContatoResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnuncioService {

    public static final int MAXIMO_FOTOS = 5;

    private final AnuncioRepository anuncioRepository;
    private final ArmazenamentoLocalService armazenamento;

    public AnuncioService(AnuncioRepository anuncioRepository, ArmazenamentoLocalService armazenamento) {
        this.anuncioRepository = anuncioRepository;
        this.armazenamento = armazenamento;
    }

    @Transactional(readOnly = true)
    public List<AnuncioResponse> listar(
            String q, String cidade, String uf, CategoriaAnimal categoria, Aptidao aptidao, Raca raca) {
        return anuncioRepository
                .findAll(
                        AnuncioBusca.filtrar(q, cidade, uf, categoria, aptidao, raca),
                        Sort.by(Sort.Direction.DESC, "id"))
                .stream()
                .map(AnuncioResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnuncioResponse> doVendedor(Usuario vendedor) {
        return anuncioRepository.doVendedor(vendedor.getId()).stream()
                .map(AnuncioResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnuncioResponse buscarPorId(Long id) {
        return AnuncioResponse.from(carregar(id));
    }

    @Transactional(readOnly = true)
    public List<CategoriaEstoqueResponse> categoriasComEstoque() {
        return anuncioRepository.contarPorCategoriaComEstoque().stream()
                .map(row -> new CategoriaEstoqueResponse((CategoriaAnimal) row[0], (Long) row[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> cidadesComEstoque() {
        return anuncioRepository.cidadesComEstoque();
    }

    @Transactional
    public AnuncioResponse criar(AnuncioRequest request, List<MultipartFile> imagens, Usuario vendedor) {
        // 428 e não 403: o app trata 403 como sessão inválida e desloga.
        if (!vendedor.isTelefoneVerificado()) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED, "Confirme seu WhatsApp antes de publicar.");
        }
        List<MultipartFile> arquivos = arquivosEnviados(imagens);
        validarQuantidade(arquivos.size());
        arquivos.forEach(armazenamento::validar);

        List<String> salvas = new ArrayList<>();
        limparAoConcluir(List.of(), salvas);
        for (MultipartFile arquivo : arquivos) {
            salvas.add(armazenamento.salvar(arquivo));
        }
        Anuncio anuncio = new Anuncio();
        preencher(anuncio, request);
        anuncio.getFotos().addAll(salvas);
        anuncio.setVendedor(vendedor);
        return AnuncioResponse.from(anuncioRepository.saveAndFlush(anuncio));
    }

    @Transactional
    public AnuncioResponse atualizar(Long id, AnuncioRequest request, List<MultipartFile> imagens, Usuario usuario) {
        Anuncio anuncio = carregarDoDono(id, usuario);
        List<String> atuais = List.copyOf(anuncio.getFotos());
        List<String> mantidas = request.fotosMantidas() == null
                ? atuais
                : request.fotosMantidas().stream().distinct().toList();
        if (!atuais.containsAll(mantidas)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Foto informada não pertence ao anúncio.");
        }
        List<MultipartFile> novas = arquivosEnviados(imagens);
        validarQuantidade(mantidas.size() + novas.size());
        novas.forEach(armazenamento::validar);

        List<String> removidas = atuais.stream().filter(url -> !mantidas.contains(url)).toList();
        List<String> salvas = new ArrayList<>();
        limparAoConcluir(removidas, salvas);
        for (MultipartFile arquivo : novas) {
            salvas.add(armazenamento.salvar(arquivo));
        }
        preencher(anuncio, request);
        anuncio.getFotos().clear();
        anuncio.getFotos().addAll(mantidas);
        anuncio.getFotos().addAll(salvas);
        return AnuncioResponse.from(anuncioRepository.saveAndFlush(anuncio));
    }

    @Transactional
    public void excluir(Long id, Usuario usuario) {
        Anuncio anuncio = carregarDoDono(id, usuario);
        limparAoConcluir(List.copyOf(anuncio.getFotos()), List.of());
        anuncioRepository.delete(anuncio);
    }

    @Transactional
    public AnuncioResponse alterarStatus(Long id, AnuncioStatus status, Usuario usuario) {
        Anuncio anuncio = carregarDoDono(id, usuario);
        anuncio.setStatus(status);
        return AnuncioResponse.from(anuncio);
    }

    @Transactional(readOnly = true)
    public ContatoResponse contato(Long id) {
        Anuncio anuncio = carregar(id);
        if (anuncio.getStatus() == AnuncioStatus.VENDIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este anúncio já foi vendido.");
        }
        String whatsapp = anuncio.getVendedor().getWhatsapp();
        // Cidade no texto: quem tem vários anúncios identifica o lote sem precisar perguntar.
        String mensagem = "Olá! Vi no CompreGado o seu anúncio \"" + anuncio.getTitulo() + "\" ("
                + anuncio.getCidade() + ") e tenho interesse. Ainda está disponível?";
        String url = "https://wa.me/55" + whatsapp + "?text=" + encode(mensagem);
        return new ContatoResponse(whatsapp, mensagem, url);
    }

    private static void preencher(Anuncio anuncio, AnuncioRequest request) {
        anuncio.setTitulo(request.titulo().trim());
        anuncio.setDescricao(request.descricao() == null ? "" : request.descricao().trim());
        anuncio.setPreco(request.preco());
        anuncio.setCidade(request.cidade().trim());
        anuncio.setCategoriaAnimal(request.categoriaAnimal());
        anuncio.setAptidao(request.aptidao());
        anuncio.setRaca(request.raca());
        anuncio.setTipoVenda(request.tipoVenda());
        anuncio.setQuantidade(request.quantidade());
        anuncio.setPesoMedioKg(request.pesoMedioKg());
    }

    private static List<MultipartFile> arquivosEnviados(List<MultipartFile> imagens) {
        return imagens == null
                ? List.of()
                : imagens.stream().filter(imagem -> imagem != null && !imagem.isEmpty()).toList();
    }

    private static void validarQuantidade(int total) {
        if (total == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie pelo menos uma foto do animal.");
        }
        if (total > MAXIMO_FOTOS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Envie no máximo " + MAXIMO_FOTOS + " fotos por anúncio.");
        }
    }

    /**
     * Arquivos em disco não participam da transação: apaga {@code seConfirmar} só depois do commit e
     * {@code seDesfazer} (lista que pode ir sendo preenchida) se houver rollback.
     */
    private void limparAoConcluir(List<String> seConfirmar, List<String> seDesfazer) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                (status == STATUS_COMMITTED ? seConfirmar : seDesfazer).forEach(armazenamento::remover);
            }
        });
    }

    private Anuncio carregarDoDono(Long id, Usuario usuario) {
        Anuncio anuncio = carregar(id);
        if (!anuncio.getVendedor().getId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este anúncio não é seu.");
        }
        return anuncio;
    }

    private Anuncio carregar(Long id) {
        return anuncioRepository.findByIdWithVendedor(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anúncio não encontrado"));
    }

    private static String encode(String texto) {
        return java.net.URLEncoder.encode(texto, java.nio.charset.StandardCharsets.UTF_8);
    }
}
