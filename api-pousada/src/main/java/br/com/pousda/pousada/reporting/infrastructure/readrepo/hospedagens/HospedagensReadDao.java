package br.com.pousda.pousada.reporting.infrastructure.readrepo.hospedagens;

import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDate;
import java.util.List;

@Repository
public class HospedagensReadDao {

    @PersistenceContext
    private EntityManager em;

    /**
     * Colunas retornadas (ordem esperada pelo service):
     * 0 codigo (TEXT)
     * 1 hospede (TEXT)
     * 2 quarto (TEXT)
     * 3 entrada (DATE)
     * 4 saída  (DATE)
     * 5 diarias (INTEGER)  -> calculada
     * 6 criado_por (TEXT)
     * 7 forma_pagamento (TEXT)
     * 8 observacao (TEXT)
     * 9 valor (NUMERIC)
     * 10 status (TEXT)     -> derivado
     * 11 tipo   (TEXT)
     */
    public List<Object[]> listar(LocalDate ini, LocalDate fim) {
        Query q = em.createNativeQuery(
                "SELECT " +
                        "  h.codigo_hospedagem                         AS codigo, " +
                        "  h.nome                                      AS hospede, " +
                        "  COALESCE(q.numero, '')                      AS quarto, " +
                        "  CAST(h.data_entrada AS date)                AS entrada, " +
                        "  CAST(h.data_saida   AS date)                AS saida, " +
                        // diárias: (date - date) em Postgres já retorna integer. Envolvemos em CAST explícito.
                        "  CAST(GREATEST(0, (CAST(h.data_saida AS date) - CAST(h.data_entrada AS date))) AS integer) AS diarias, " +
                        "  COALESCE(h.criado_por, '')                  AS criado_por, " +
                        "  COALESCE(h.forma_pagamento, '')             AS forma_pagamento, " +
                        "  COALESCE(h.observacoes, '')                 AS observacao, " +
                        "  CAST(COALESCE(h.valor_total, 0) AS numeric(18,2)) AS valor, " +
                        "  CASE " +
                        "    WHEN h.cancelado = true THEN 'CANCELADA' " +
                        "    WHEN CURRENT_DATE >  CAST(h.data_saida   AS date) THEN 'FINALIZADA' " +
                        "    WHEN CURRENT_DATE >= CAST(h.data_entrada AS date) " +
                        "     AND CURRENT_DATE <= CAST(h.data_saida   AS date) THEN 'ATIVA' " +
                        "    ELSE 'ATIVA' " +
                        "  END                                         AS status, " +
                        "  CAST(h.tipo AS varchar)                     AS tipo " +
                        "FROM hospedagem h " +
                        "LEFT JOIN quarto q ON q.id = h.quarto_id " +
                        "WHERE CAST(h.data_entrada AS date) <= :fim " +
                        "  AND CAST(h.data_saida   AS date) >= :ini"
        );
        q.setParameter("ini", ini);
        q.setParameter("fim", fim);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        return rows;
    }


    public long countAtivas() {
        Query q = em.createNativeQuery(
                "SELECT COUNT(*) " +
                        "FROM hospedagem h " +
                        "WHERE h.cancelado = false " +
                        "  AND CURRENT_DATE >= CAST(h.data_entrada AS date) " +
                        "  AND CURRENT_DATE <= CAST(h.data_saida AS date)"
        );

        Object result = q.getSingleResult();
        if (result == null) return 0L;
        return ((Number) result).longValue();
    }

}
