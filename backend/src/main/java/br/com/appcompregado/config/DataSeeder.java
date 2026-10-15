package br.com.appcompregado.config;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.TipoVenda;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("!prod")
public class DataSeeder {

    @Bean
    CommandLineRunner seedAnuncios(
            UsuarioRepository usuarioRepository,
            AnuncioRepository anuncioRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            if (anuncioRepository.count() > 0) {
                return;
            }

            Usuario vendedor = usuarioRepository.findByWhatsapp("61999999999").orElseGet(() -> {
                Usuario novo = new Usuario();
                novo.setNome("João da Fazenda");
                novo.setWhatsapp("61999999999");
                novo.setSenha(passwordEncoder.encode("123456"));
                novo.setPremium(false);
                return usuarioRepository.save(novo);
            });

            anuncioRepository.save(anuncio(
                    "Lote de bezerros Nelore — Formosa",
                    "20 bezerros desmamados, bons de pasto, documentação em dia. Retirada na fazenda.",
                    new BigDecimal("2800.00"),
                    "Formosa - GO",
                    CategoriaAnimal.BEZERRO,
                    Aptidao.CORTE,
                    Raca.NELORE,
                    TipoVenda.CABECA,
                    20,
                    180,
                    vendedor));
            anuncioRepository.save(anuncio(
                    "Vacas Girolando leiteiras",
                    "Vacas em lactação, média 18 litros/dia. Ideal para sítio no Entorno.",
                    new BigDecimal("6500.00"),
                    "Planaltina - GO",
                    CategoriaAnimal.VACA,
                    Aptidao.LEITE,
                    Raca.GIROLANDO,
                    TipoVenda.CABECA,
                    6,
                    450,
                    vendedor));
            anuncioRepository.save(anuncio(
                    "Touro Angus reprodução",
                    "Touro jovem, fertilidade comprovada. Negociação via WhatsApp.",
                    new BigDecimal("12000.00"),
                    "Formosa - GO",
                    CategoriaAnimal.TOURO,
                    Aptidao.REPRODUCAO,
                    Raca.ANGUS,
                    TipoVenda.CABECA,
                    1,
                    850,
                    vendedor));
        };
    }

    private static Anuncio anuncio(
            String titulo,
            String descricao,
            BigDecimal preco,
            String cidade,
            CategoriaAnimal categoria,
            Aptidao aptidao,
            Raca raca,
            TipoVenda tipoVenda,
            Integer quantidade,
            Integer pesoMedioKg,
            Usuario vendedor) {
        Anuncio item = new Anuncio();
        item.setTitulo(titulo);
        item.setDescricao(descricao);
        item.setPreco(preco);
        item.setCidade(cidade);
        item.setCategoriaAnimal(categoria);
        item.setAptidao(aptidao);
        item.setRaca(raca);
        item.setTipoVenda(tipoVenda);
        item.setQuantidade(quantidade);
        item.setPesoMedioKg(pesoMedioKg);
        item.setVendedor(vendedor);
        return item;
    }
}
