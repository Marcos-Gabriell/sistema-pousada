package br.com.pousda.pousada;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 👈 habilita os @Scheduled
public class PousadaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PousadaApplication.class, args);
    }

}
