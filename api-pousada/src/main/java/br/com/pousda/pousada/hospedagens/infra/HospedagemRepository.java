package br.com.pousda.pousada.hospedagens.infra;

import br.com.pousda.pousada.hospedagens.domain.Hospedagem;
import br.com.pousda.pousada.quartos.domain.Quarto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HospedagemRepository extends JpaRepository<Hospedagem, Long> {

    Optional<Hospedagem> findTopByQuartoOrderByIdDesc(Quarto terceiro);

    boolean existsByCodigoHospedagem(String codigoHospedagem);

    Optional<Hospedagem> findByCodigoHospedagem(String codigoHospedagem);

    List<Hospedagem> findByDataSaida(LocalDate dataSaida);

    boolean existsByQuartoAndDataEntradaLessThanEqualAndDataSaidaGreaterThan(
            Quarto quarto,
            LocalDate dataRef1,
            LocalDate dataRef2
    );

    int countByDataEntradaGreaterThanEqualAndDataEntradaLessThan(LocalDate inicio, LocalDate fimExclusivo);

    default int countBetween(LocalDate inicio, LocalDate fimExclusivo) {
        return countByDataEntradaGreaterThanEqualAndDataEntradaLessThan(inicio, fimExclusivo);
    }

    int countByDataEntradaLessThanAndDataSaidaGreaterThan(LocalDate fimExclusivo, LocalDate inicio);

    @Query("SELECT h FROM Hospedagem h LEFT JOIN FETCH h.quarto q")
    List<Hospedagem> findAllComQuarto();

    @Query("SELECT h FROM Hospedagem h LEFT JOIN FETCH h.quarto q WHERE h.id = :id")
    Optional<Hospedagem> findByIdComQuarto(@Param("id") Long id);

    @Query(
            "SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END " +
                    "FROM Hospedagem h " +
                    "WHERE h.quarto = :quarto " +
                    "AND (h.cancelada = false OR h.cancelada IS NULL) " +
                    "AND h.dataEntrada < :fim " +
                    "AND (h.dataSaida IS NULL OR h.dataSaida > :inicio)"
    )
    boolean existsConflitoIntervalo(@Param("quarto") Quarto quarto,
                                    @Param("inicio") LocalDate inicio,
                                    @Param("fim") LocalDate fim);

    Optional<Hospedagem> findTopByCodigoHospedagemStartingWithOrderByCodigoHospedagemDesc(String prefix);

    // ✅ Snapshot do momento (como já estava)
    @Query(
            "SELECT COUNT(h) FROM Hospedagem h " +
                    "JOIN h.quarto q " +
                    "WHERE (h.cancelada = false OR h.cancelada IS NULL) " +
                    "AND q.status = br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto.OCUPADO"
    )
    long countHospedagensAtivasComQuartoOcupado();

    // ✅ KPI FILTRADO POR PERÍODO: hospedagens ativas dentro do intervalo
    // Regra: entrou até o fim e ainda não saiu antes do início (overlap do intervalo)
    @Query(
            "SELECT COUNT(h) FROM Hospedagem h " +
                    "WHERE (h.cancelada = false OR h.cancelada IS NULL) " +
                    "AND h.dataEntrada <= :fim " +
                    "AND (h.dataSaida IS NULL OR h.dataSaida >= :inicio)"
    )
    long countHospedagensAtivasNoPeriodo(@Param("inicio") LocalDate inicio,
                                         @Param("fim") LocalDate fim);

    // ✅ Série ocupação por dia (período)
    // Obs: aqui eu mantive o conceito de reservas PENDENTES no mesmo dia.
    @Query(
            "select h.dataEntrada as dia, " +
                    "       count(h.id) as qtdHospedagens, " +
                    "       (select count(r.id) " +
                    "          from Reserva r " +
                    "         where r.dataEntrada = h.dataEntrada " +
                    "           and r.status = br.com.pousda.pousada.reservas.domain.StatusReserva.PENDENTE) " +
                    "from Hospedagem h " +
                    "where (h.cancelada = false or h.cancelada is null) " +
                    "and h.dataEntrada between :inicio and :fim " +
                    "group by h.dataEntrada " +
                    "order by h.dataEntrada"
    )
    List<Object[]> ocupacaoPorDiaEntre(@Param("inicio") LocalDate inicio,
                                       @Param("fim") LocalDate fim);

    // ✅ Checkouts pendentes (hoje) com quarto OCUPADO
    @Query(
            "SELECT COUNT(h) FROM Hospedagem h " +
                    "JOIN h.quarto q " +
                    "WHERE h.dataSaida = :dataSaida " +
                    "AND (h.cancelada = false OR h.cancelada IS NULL) " +
                    "AND q.status = br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto.OCUPADO"
    )
    long countCheckoutsPendentesParaData(@Param("dataSaida") LocalDate dataSaida);

    // Mantidos (compatibilidade)
    @Query(
            "SELECT COUNT(h) FROM Hospedagem h " +
                    "WHERE h.dataSaida = :dataSaida " +
                    "AND (h.cancelada = false OR h.cancelada IS NULL)"
    )
    long countCheckoutsAtivosParaData(@Param("dataSaida") LocalDate dataSaida);

    @Query(
            "select count(h) from Hospedagem h " +
                    "where (h.cancelada = false or h.cancelada is null) " +
                    "and (h.dataSaida is null or h.dataSaida >= :hoje)"
    )
    long countHospedagensAtivas(@Param("hoje") LocalDate hoje);
}
