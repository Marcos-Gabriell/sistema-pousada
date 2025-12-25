package br.com.pousda.pousada.financeiro.infra;

import br.com.pousda.pousada.financeiro.domain.LancamentoFinanceiro;
import br.com.pousda.pousada.financeiro.domain.enuns.OrigemLancamento;
import br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LancamentoFinanceiroRepository extends JpaRepository<LancamentoFinanceiro, Long> {

    List<LancamentoFinanceiro> findByTipoAndExcluidoEmIsNull(TipoLancamento tipo);

    List<LancamentoFinanceiro> findByDataBetweenAndExcluidoEmIsNull(LocalDate inicio, LocalDate fim);

    List<LancamentoFinanceiro> findByDataGreaterThanEqualAndExcluidoEmIsNull(LocalDate inicio);

    List<LancamentoFinanceiro> findByDataLessThanEqualAndExcluidoEmIsNull(LocalDate fim);

    List<LancamentoFinanceiro> findByDataBetweenAndExcluidoEmIsNullOrderByDataHoraDesc(LocalDate inicio, LocalDate fim);

    boolean existsByOrigemAndReferenciaIdAndExcluidoEmIsNull(OrigemLancamento origem, Long referenciaId);

    Optional<LancamentoFinanceiro> findFirstByOrigemAndReferenciaIdAndExcluidoEmIsNullOrderByIdDesc(
            OrigemLancamento origem, Long referenciaId);

    Optional<LancamentoFinanceiro> findTopByCodigoStartingWithAndExcluidoEmIsNullOrderByCodigoDesc(String prefix);

    List<LancamentoFinanceiro> findTop10ByExcluidoEmIsNullOrderByDataHoraDesc();

    List<LancamentoFinanceiro> findTop20ByExcluidoEmIsNullOrderByDataHoraDesc();

    @Query("SELECT l FROM LancamentoFinanceiro l WHERE l.excluidoEm IS NULL ORDER BY l.dataHora DESC")
    List<LancamentoFinanceiro> findTopNByExcluidoEmIsNullOrderByDataHoraDesc(Pageable pageable);

    // =========================
    // SALDOS (GERAL)
    // =========================

    // Saldo geral (tudo)
    @Query(
            "select coalesce(" +
                    "  sum(" +
                    "    case when l.tipo = br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento.ENTRADA " +
                    "         then l.valor " +
                    "         else -l.valor " +
                    "    end" +
                    "  ), 0" +
                    ") " +
                    "from LancamentoFinanceiro l " +
                    "WHERE l.excluidoEm IS NULL"
    )
    Double calcularSaldoAtual();

    // Saldo somente do que é prefeitura
    @Query(
            "select coalesce(" +
                    "  sum(" +
                    "    case when l.tipo = br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento.ENTRADA " +
                    "         then l.valor " +
                    "         else -l.valor " +
                    "    end" +
                    "  ), 0" +
                    ") " +
                    "from LancamentoFinanceiro l " +
                    "where l.excluidoEm is null " +
                    "  and l.prefeitura = true"
    )
    Double calcularSaldoAtualPrefeitura();

    // =========================
    // SALDOS (POR PERÍODO)  ✅ ADICIONADO
    // =========================

    @Query(
            "select coalesce(" +
                    "  sum(" +
                    "    case when l.tipo = br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento.ENTRADA " +
                    "         then l.valor " +
                    "         else -l.valor " +
                    "    end" +
                    "  ), 0" +
                    ") " +
                    "from LancamentoFinanceiro l " +
                    "where l.excluidoEm is null " +
                    "  and l.data between :inicio and :fim"
    )
    Double calcularSaldoAtualEntre(@Param("inicio") LocalDate inicio,
                                   @Param("fim") LocalDate fim);

    @Query(
            "select coalesce(" +
                    "  sum(" +
                    "    case when l.tipo = br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento.ENTRADA " +
                    "         then l.valor " +
                    "         else -l.valor " +
                    "    end" +
                    "  ), 0" +
                    ") " +
                    "from LancamentoFinanceiro l " +
                    "where l.excluidoEm is null " +
                    "  and l.prefeitura = true " +
                    "  and l.data between :inicio and :fim"
    )
    Double calcularSaldoAtualPrefeituraEntre(@Param("inicio") LocalDate inicio,
                                             @Param("fim") LocalDate fim);

    // =========================
    // SÉRIES
    // =========================

    @Query(
            "select l.data as dia, l.tipo as tipo, sum(l.valor) " +
                    "from LancamentoFinanceiro l " +
                    "where l.data between :inicio and :fim " +
                    "  and l.excluidoEm IS NULL " +
                    "group by l.data, l.tipo " +
                    "order by l.data"
    )
    List<Object[]> totalPorDiaEntre(@Param("inicio") LocalDate inicio,
                                    @Param("fim") LocalDate fim);

    List<LancamentoFinanceiro> findByExcluidoEmIsNullOrderByDataHoraDesc();

    List<LancamentoFinanceiro> findByExcluidoEmIsNotNullOrderByExcluidoEmDesc();

    long countByTipoAndExcluidoEmIsNull(TipoLancamento tipo);

    Optional<LancamentoFinanceiro> findTopByCodigoStartingWithOrderByCodigoDesc(String prefix);

    // =========================
    // RECEBIMENTOS POR TIPO (GERAL)
    // =========================

    // Recebimentos da prefeitura (somente entradas)
    @Query(
            "select coalesce(sum(l.valor), 0) " +
                    "from LancamentoFinanceiro l " +
                    "where l.excluidoEm is null " +
                    "and l.tipo = br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento.ENTRADA " +
                    "and l.prefeitura = true"
    )
    Double calcularRecebimentosPrefeitura();

    // Recebimentos comuns (somente entradas, não prefeitura)
    @Query(
            "select coalesce(sum(l.valor), 0) " +
                    "from LancamentoFinanceiro l " +
                    "where l.excluidoEm is null " +
                    "and l.tipo = br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento.ENTRADA " +
                    "and l.prefeitura = false"
    )
    Double calcularRecebimentosComuns();

    // Recebimentos corporativos - sempre zero pois não temos essa categoria
    default Double calcularRecebimentosCorporativos() {
        return 0.0;
    }

    // =========================
    // RECEBIMENTOS POR TIPO (POR PERÍODO) ✅ ADICIONADO
    // =========================

    @Query(
            "select coalesce(sum(l.valor), 0) " +
                    "from LancamentoFinanceiro l " +
                    "where l.excluidoEm is null " +
                    "and l.tipo = br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento.ENTRADA " +
                    "and l.prefeitura = true " +
                    "and l.data between :inicio and :fim"
    )
    Double calcularRecebimentosPrefeituraEntre(@Param("inicio") LocalDate inicio,
                                               @Param("fim") LocalDate fim);

    @Query(
            "select coalesce(sum(l.valor), 0) " +
                    "from LancamentoFinanceiro l " +
                    "where l.excluidoEm is null " +
                    "and l.tipo = br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento.ENTRADA " +
                    "and l.prefeitura = false " +
                    "and l.data between :inicio and :fim"
    )
    Double calcularRecebimentosComunsEntre(@Param("inicio") LocalDate inicio,
                                           @Param("fim") LocalDate fim);

    default Double calcularRecebimentosCorporativosEntre(LocalDate inicio, LocalDate fim) {
        return 0.0;
    }

    // =========================
    // OUTROS
    // =========================
    boolean existsByReferenciaIdAndExcluidoEmIsNull(Long referenciaId);
    List<LancamentoFinanceiro> findByReferenciaIdAndExcluidoEmIsNull(Long referenciaId);
}
