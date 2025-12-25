package br.com.pousda.pousada.reporting.infrastructure.readrepo.financeiro;

import br.com.pousda.pousada.financeiro.domain.LancamentoFinanceiro;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.LocalDateTime;
import java.util.List;

public interface FinancialMovementReadDao extends Repository<LancamentoFinanceiro, Long> {

    // Retorna UMA linha com: [total_entradas, total_saidas],
    // mas o Spring Data entrega como List<Object[]>
    @Query(
            value =
                    "SELECT " +
                            "  COALESCE(SUM(CASE WHEN fm.tipo = 'ENTRADA' THEN fm.valor ELSE 0 END), 0) AS total_entradas, " +
                            "  COALESCE(SUM(CASE WHEN fm.tipo = 'SAIDA'   THEN fm.valor ELSE 0 END), 0) AS total_saidas " +
                            "FROM lancamento_financeiro fm " +
                            "WHERE fm.data_hora >= :ini " +
                            "  AND fm.data_hora < :fim " +
                            "  AND (:tipo IS NULL OR fm.tipo = :tipo) " +
                            "  AND fm.excluido_em IS NULL",
            nativeQuery = true
    )
    List<Object[]> sumTotais(LocalDateTime ini, LocalDateTime fim, String tipo);

    @Query(
            value =
                    "SELECT " +
                            "  fm.id, " +            // [0]
                            "  fm.data_hora, " +     // [1]
                            "  fm.descricao, " +     // [2]
                            "  fm.valor, " +         // [3]
                            "  fm.tipo, " +          // [4]
                            "  fm.criado_por_nome, " + // [5]
                            "  fm.codigo " +         // [6]
                            "FROM lancamento_financeiro fm " +
                            "WHERE fm.data_hora >= :ini " +
                            "  AND fm.data_hora < :fim " +
                            "  AND (:tipo IS NULL OR fm.tipo = :tipo) " +
                            "  AND fm.excluido_em IS NULL " +
                            "ORDER BY fm.data_hora DESC",
            nativeQuery = true
    )
    List<Object[]> findLinhas(LocalDateTime ini, LocalDateTime fim, String tipo);
}
