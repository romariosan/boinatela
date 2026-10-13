package com.boinatela.service;

import com.boinatela.config.UploadProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class ArmazenamentoLocalService {

    public static final String CAMINHO_PUBLICO = "/uploads/";

    // A extensão vem desta lista, nunca do nome enviado, para o arquivo não ser servido como outro tipo.
    private static final Map<String, String> EXTENSOES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private final Path pasta;

    public ArmazenamentoLocalService(UploadProperties properties) {
        this.pasta = Paths.get(properties.getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(pasta);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar a pasta de uploads: " + pasta, e);
        }
    }

    public Path getPasta() {
        return pasta;
    }

    public void validar(MultipartFile imagem) {
        extensaoDe(imagem);
    }

    public String salvar(MultipartFile imagem) {
        String nome = UUID.randomUUID() + "." + extensaoDe(imagem);
        try (InputStream entrada = imagem.getInputStream()) {
            Files.copy(entrada, pasta.resolve(nome));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao salvar a foto.", e);
        }

        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(CAMINHO_PUBLICO)
                .path(nome)
                .toUriString();
    }

    private static String extensaoDe(MultipartFile imagem) {
        if (imagem == null || imagem.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie uma foto do animal.");
        }
        String extensao = EXTENSOES.get(imagem.getContentType());
        if (extensao == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use fotos JPG, PNG, WEBP ou GIF.");
        }
        return extensao;
    }

    public void remover(String url) {
        if (url == null) {
            return;
        }
        String nome = url.substring(url.lastIndexOf('/') + 1);
        Path arquivo = pasta.resolve(nome).normalize();
        if (nome.isBlank() || arquivo.equals(pasta) || !arquivo.startsWith(pasta)) {
            return;
        }
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException ignored) {
            // Arquivo órfão não impede a resposta de erro original.
        }
    }
}
