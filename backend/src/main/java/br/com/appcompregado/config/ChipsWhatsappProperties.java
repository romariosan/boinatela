package br.com.appcompregado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Três chips: o sistema manda OTP, o ouvinte só escuta, o consultor prospecta. */
@Component
@ConfigurationProperties(prefix = "compregado.whatsapp")
public class ChipsWhatsappProperties {

    private Chip system = new Chip();
    private Chip scraper = new Chip();
    private Chip consultant = new Chip();

    public Chip getSystem() {
        return system;
    }

    public void setSystem(Chip system) {
        this.system = system;
    }

    public Chip getScraper() {
        return scraper;
    }

    public void setScraper(Chip scraper) {
        this.scraper = scraper;
    }

    public Chip getConsultant() {
        return consultant;
    }

    public void setConsultant(Chip consultant) {
        this.consultant = consultant;
    }

    public static class Chip {
        private String number = "";
        private String instance = "";

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number == null ? "" : number;
        }

        public String getInstance() {
            return instance;
        }

        public void setInstance(String instance) {
            this.instance = instance == null ? "" : instance;
        }
    }
}
