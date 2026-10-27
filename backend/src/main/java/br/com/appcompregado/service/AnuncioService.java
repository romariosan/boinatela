package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.LeadContato;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusContato;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.StatusPublicacao;
import br.com.appcompregado.repository.AnuncioBusca;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.LeadContatoRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.web.dto.AnuncioRequest;
import br.com.appcompregado.web.dto.AnuncioResponse;
import br.com.appcompregado.web.dto.CategoriaEstoqueResponse;
import br.com.appcompregado.web.dto.ContatoResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
    private final UsuarioRepository usuarioRepository;
    private final LeadContatoRepository leads;
    private final InteracaoContatoService interacao;
    private final ArmazenamentoLocalService armazenamento;
    private final AlertaNotificador radar;
    private final VendidoToken vendidoToken;
    private final int limiteAnunciosGratis;

    public AnuncioService(
            AnuncioRepository anuncioRepository,
            UsuarioRepository usuarioRepository,
            LeadContatoRepository leads,
            InteracaoContatoService interacao,
            ArmazenamentoLocalService armazenamento,
            AlertaNotificador radar,
            VendidoToken vendidoToken,
            @Value("${compregado.limite.anuncios.gratis}") int limiteAnunciosGratis) {
        this.anuncioRepository = anuncioRepository;
        this.usuarioRepository = usuarioRepository;
        this.leads = leads;
        this.interacao = interacao;
        this.armazenamento = armazenamento;
        this.radar = radar;
        this.vendidoToken = vendidoToken;
        this.limiteAnunciosGratis = limiteAnunciosGratis;
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
        Anuncio anuncio = carregar(id);
        // Detalhe público: mesmo critério da vitrine — limbo PENDENTE não abre por URL.
        if (anuncio.getStatusPublicacao() != StatusPublicacao.APROVADO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Anúncio não encontrado");
        }
        return AnuncioResponse.from(anuncio);
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
        verificarLimiteGratis(vendedor);
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
        anuncio.setUrlFotoPrincipal(salvas.get(0));
        anuncio.setStatusProcessamento(StatusProcessamento.PUBLICADO);
        // Publicação pelo app (usuário verificado): já é de confiança — vai direto à vitrine.
        // Captura do Radar continua PENDENTE (ver ScraperAnuncioService).
        anuncio.setStatusPublicacao(StatusPublicacao.APROVADO);
        anuncio.setStatusContato(StatusContato.REIVINDICADO);
        anuncio.setReivindicado(true);
        anuncio.setVendedor(vendedor);
        anuncio = anuncioRepository.saveAndFlush(anuncio);
        radar.agendar(anuncio);
        return AnuncioResponse.from(anuncio);
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
        if (!anuncio.getFotos().isEmpty()) {
            anuncio.setUrlFotoPrincipal(anuncio.getFotos().get(0));
            if (anuncio.getStatusProcessamento() == StatusProcessamento.RASCUNHO_SEM_FOTO) {
                anuncio.setStatusProcessamento(StatusProcessamento.PUBLICADO);
                anuncio.setAtivo(anuncio.getStatus() != AnuncioStatus.VENDIDO);
            }
        }
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
        // Sem isto, "vender" e "reativar" contornaria o limite: o reativado volta a ocupar uma vaga.
        if (status == AnuncioStatus.ATIVO && anuncio.getStatus() != AnuncioStatus.ATIVO) {
            verificarLimiteGratis(usuario);
        }
        anuncio.setStatus(status);
        anuncio.setAtivo(status != AnuncioStatus.VENDIDO);
        return AnuncioResponse.from(anuncio);
    }

    /** Dono logado ou o link assinado que o Chip 3 mandou no lembrete de 15 dias. */
    @Transactional
    public AnuncioResponse marcarVendido(Long id, Usuario logado, String token) {
        Anuncio anuncio = carregar(id);
        boolean dono = logado != null
                && anuncio.getVendedor() != null
                && anuncio.getVendedor().getId().equals(logado.getId());
        if (!dono && !vendidoToken.confere(id, token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Não foi possível confirmar este lote.");
        }
        anuncio.setStatus(AnuncioStatus.VENDIDO);
        anuncio.setAtivo(false);
        return AnuncioResponse.from(anuncio);
    }

    /**
     * Relê o vendedor do banco com trava (o do token pode ter virado PRO depois do login) e conta só os
     * anúncios ATIVOS: o VENDIDO saiu da vitrine e libera a vaga.
     */
    private void verificarLimiteGratis(Usuario logado) {
        Usuario vendedor = usuarioRepository.travarPorId(logado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (vendedor.isPremium()) {
            return;
        }
        long ativos = anuncioRepository.countByVendedorIdAndStatus(vendedor.getId(), AnuncioStatus.ATIVO);
        if (ativos >= limiteAnunciosGratis) {
            throw new LimiteAnunciosAtingidoException(limiteAnunciosGratis);
        }
    }

    @Transactional
    public ContatoResponse contato(Long id, String whatsappInformado) {
        Anuncio anuncio = carregar(id);
        if (anuncio.getStatus() == AnuncioStatus.VENDIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este anúncio já foi vendido.");
        }
        if (anuncio.getStatus() == AnuncioStatus.PAUSADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este anúncio está pausado.");
        }
        String comprador = PhoneUtils.normalizar(whatsappInformado);
        if (!PhoneUtils.celularBrasileiro(comprador)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um WhatsApp com DDD.");
        }
        LeadContato lead = new LeadContato();
        lead.setAnuncio(anuncio);
        lead.setWhatsapp(comprador);
        lead.setDataCriacao(LocalDateTime.now());
        leads.save(lead);

        String whatsapp = anuncio.getTelefoneVendedorOriginal();
        if (whatsapp == null || whatsapp.isBlank()) {
            whatsapp = anuncio.getVendedor().getWhatsapp();
        }
        whatsapp = PhoneUtils.normalizar(whatsapp);
        String mensagem = "Olá, vi seu lote de " + anuncio.getTitulo() + " no CompreGado e tenho interesse.";
        String url = "https://wa.me/55" + whatsapp + "?text=" + encode(mensagem);
        interacao.registrar(id);
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
