package br.com.appcompregado;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CompreGadoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompreGadoApplication.class, args);
    }
}
