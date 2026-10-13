package com.boinatela.config;

import com.boinatela.domain.Anuncio;
import com.boinatela.domain.Usuario;
import com.boinatela.domain.enums.Aptidao;
import com.boinatela.domain.enums.CategoriaAnimal;
import com.boinatela.domain.enums.Raca;
import com.boinatela.domain.enums.TipoVenda;
import com.boinatela.repository.AnuncioRepository;
import com.boinatela.repository.UsuarioRepository;
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
        item.setVendedor(vendedor);
        return item;
    }
}
