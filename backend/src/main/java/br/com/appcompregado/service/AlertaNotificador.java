package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Dispara o radar depois do commit, para o link não apontar para um anúncio que a transação desfez. */
@Service
public class AlertaNotificador {

    private final AlertaMatchService match;
    private final ObjectProvider<AlertaNotificador> eu;
    private final Executor executor;

    public AlertaNotificador(
            AlertaMatchService match,
            ObjectProvider<AlertaNotificador> eu,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.match = match;
        this.eu = eu;
        this.executor = executor;
    }

    public void agendar(Anuncio anuncio) {
        if (anuncio.getId() == null
                || !anuncio.isAtivo()
                || anuncio.getStatusProcessamento() == StatusProcessamento.RASCUNHO_SEM_FOTO) {
            return;
        }
        NovoAnuncio dados = new NovoAnuncio(
                anuncio.getId(),
                anuncio.getRaca() == null ? "" : anuncio.getRaca().name(),
                anuncio.getCidade(),
                anuncio.getCategoriaAnimal() == null ? "" : anuncio.getCategoriaAnimal().name(),
                anuncio.getQuantidade(),
                anuncio.getLinkWeb(),
                telefone(anuncio));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eu.getObject().enviar(dados);
                }
            });
            return;
        }
        enviar(dados);
    }

    public void enviar(NovoAnuncio dados) {
        executor.execute(() -> match.avisar(dados));
    }

    private static String telefone(Anuncio anuncio) {
        if (PhoneUtils.celularBrasileiro(anuncio.getTelefoneVendedorOriginal())) {
            return PhoneUtils.normalizar(anuncio.getTelefoneVendedorOriginal());
        }
        if (anuncio.getVendedor() != null && PhoneUtils.celularBrasileiro(anuncio.getVendedor().getWhatsapp())) {
            return PhoneUtils.normalizar(anuncio.getVendedor().getWhatsapp());
        }
        return "";
    }

    public record NovoAnuncio(
            long id,
            String raca,
            String cidade,
            String categoria,
            Integer quantidade,
            String link,
            String telefoneVendedor) {
    }
}
