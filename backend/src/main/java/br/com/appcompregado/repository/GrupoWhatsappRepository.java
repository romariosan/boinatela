package br.com.appcompregado.repository;

import br.com.appcompregado.domain.GrupoWhatsapp;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrupoWhatsappRepository extends JpaRepository<GrupoWhatsapp, Long> {

    Optional<GrupoWhatsapp> findByWhatsappJid(String whatsappJid);
}
