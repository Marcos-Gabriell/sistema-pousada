package br.com.pousda.pousada.reporting.infrastructure.readrepo.quartos;

import br.com.pousda.pousada.quartos.domain.Quarto;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface QuartosReadDao extends Repository<Quarto, Long> {

    /** Lista básica dos quartos (para montar a grade e aplicar filtros no service). */
    @Query(value = "SELECT q.numero, CAST(q.tipo AS text) AS tipo, q.valor_diaria, CAST(q.status AS text) AS status FROM quarto q",
            nativeQuery = true)
    List<Object[]> listarTodos();

    // ============================
    // ✅ CONTADORES POR STATUS
    // ============================

    @Query(value = "SELECT COUNT(*) FROM quarto q WHERE CAST(q.status AS text) = :status", nativeQuery = true)
    long countByStatus(@Param("status") String status);

    /**
     * Ocupação média no período (percentual de dias com ao menos uma hospedagem ativa).
     * Evita o operador :: e usa CAST explícito para não quebrar via JPA.
     */
    @Query(value =
            "WITH dias AS ( " +
                    "   SELECT CAST(d AS date) AS dia " +
                    "   FROM generate_series( " +
                    "       CAST(:ini AS timestamp), " +
                    "       CAST(:fim AS timestamp), " +
                    "       interval '1 day' " +
                    "   ) AS d " +
                    "), marca AS ( " +
                    "   SELECT di.dia, " +
                    "          CASE WHEN EXISTS ( " +
                    "               SELECT 1 FROM hospedagem h " +
                    "               WHERE di.dia BETWEEN CAST(h.data_entrada AS date) AND CAST(h.data_saida AS date) " +
                    "          ) THEN 1 ELSE 0 END AS occ " +
                    "   FROM dias di " +
                    ") " +
                    "SELECT COALESCE(ROUND(CAST(100.0 * AVG(marca.occ) AS numeric), 2), 0) " +
                    "FROM marca",
            nativeQuery = true)
    Double ocupacaoMedia(@Param("ini") LocalDate ini, @Param("fim") LocalDate fim);

    /**
     * Quarto mais ocupado no período (mais aparições em hospedagens cujo created/intervalo cruza o período).
     */
    @Query(value =
            "SELECT q.numero " +
                    "FROM hospedagem h " +
                    "JOIN quarto q ON q.id = h.quarto_id " +
                    "WHERE CAST(h.data_entrada AS date) BETWEEN :ini AND :fim " +
                    "GROUP BY q.numero " +
                    "ORDER BY COUNT(*) DESC " +
                    "LIMIT 1",
            nativeQuery = true)
    String quartoMaisOcupado(@Param("ini") LocalDate ini, @Param("fim") LocalDate fim);
}
