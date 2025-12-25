package br.com.pousda.pousada.reporting.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

// habilite só quando quiser rodar (ENABLE_CREATE_VIEWS=true)
@Component
@ConditionalOnProperty(name = "enable.create.views", havingValue = "true")
@RequiredArgsConstructor
public class CreateViewsRunner implements ApplicationRunner {

    @PersistenceContext
    private EntityManager em;

    @Override public void run(ApplicationArguments args) {
        em.createNativeQuery(
                "CREATE OR REPLACE VIEW vw_financial_movement AS " +
                        "SELECT id, created_at AS data, COALESCE(descricao,'') AS descricao, " +
                        "COALESCE(valor,0) AS valor, COALESCE(tipo,'') AS tipo, " +
                        "COALESCE(cancelado,false) AS cancelado " +
                        "FROM movimento_financeiro" // <--- ajuste nomes
        ).executeUpdate();
    }
}
