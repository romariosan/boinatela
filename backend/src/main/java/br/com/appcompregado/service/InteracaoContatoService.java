package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.repository.AnuncioRepository;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Conta o clique em "Chamar no WhatsApp" e avisa o vendedor pelo Chip 3, sem segurar o comprador. */
@Service
public class InteracaoContatoService {

    static final String AVISO = "Opa! Um comprador de Formosa/Região acabou de clicar para ver seu anúncio "
            + "no CompreGado. Fique atento ao seu WhatsApp!";

    private final AnuncioRepository anuncios;
    private final WhatsAppConsultantService consultor;
    private final Executor executor;

    public InteracaoContatoService(
            AnuncioRepository anuncios,
            WhatsAppConsultantService consultor,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.anuncios = anuncios;
        this.consultor = consultor;
        this.executor = executor;
    }

    @Transactional
    public void registrar(Long id) {
        Anuncio anuncio = anuncios.findByIdWithVendedor(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anúncio não encontrado"));
        anuncio.setQuantidadeCliquesContato(anuncio.getQuantidadeCliquesContato() + 1);
        String telefone = telefoneDoVendedor(anuncio);
        if (telefone == null) {
            return;
        }
        Runnable aviso = () -> {
            try {
                consultor.enviar(telefone, AVISO);
            } catch (RuntimeException ignored) {
                // O clique já foi contado. Uma falha no chip não desfaz a visita.
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(aviso);
                }
            });
            return;
        }
        executor.execute(aviso);
    }

    static String telefoneDoVendedor(Anuncio anuncio) {
        if (PhoneUtils.celularBrasileiro(anuncio.getTelefoneVendedorOriginal())) {
            return PhoneUtils.normalizar(anuncio.getTelefoneVendedorOriginal());
        }
        Usuario vendedor = anuncio.getVendedor();
        if (vendedor != null
                && !ContaCaptura.WHATSAPP.equals(vendedor.getWhatsapp())
                && PhoneUtils.celularBrasileiro(vendedor.getWhatsapp())) {
            return PhoneUtils.normalizar(vendedor.getWhatsapp());
        }
        return null;
    }
}
