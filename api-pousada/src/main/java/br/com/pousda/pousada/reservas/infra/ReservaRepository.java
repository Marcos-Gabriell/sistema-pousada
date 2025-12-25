package br.com.pousda.pousada.reservas.infra;

import br.com.pousda.pousada.quartos.domain.Quarto;
import br.com.pousda.pousada.reservas.domain.Reserva;
import br.com.pousda.pousada.reservas.domain.StatusReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findByCodigo(String codigo);

    @Query(
            "SELECT r FROM Reserva r WHERE r.quarto = :quarto " +
                    "  AND r.status IN :statuses " +
                    "  AND r.dataSaida > :dataEntradaNova " +
                    "  AND r.dataEntrada < :dataSaidaNova"
    )
    List<Reserva> findConflitos(
            @Param("quarto") Quarto quarto,
            @Param("statuses") List<StatusReserva> statuses,
            @Param("dataEntradaNova") LocalDate dataEntradaNova,
            @Param("dataSaidaNova") LocalDate dataSaidaNova
    );

    @Query(
            "SELECT r FROM Reserva r " +
                    "WHERE (:inicio IS NULL OR r.dataEntrada >= :inicio) " +
                    "AND   (:fim    IS NULL OR r.dataSaida   <= :fim)"
    )
    List<Reserva> findByPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim
    );

    @Query(
            "SELECT r FROM Reserva r " +
                    "WHERE (:inicio IS NULL OR r.dataEntrada >= :inicio) " +
                    "AND   (:fim    IS NULL OR r.dataSaida   <= :fim) " +
                    "AND   r.status = :status"
    )
    List<Reserva> findByPeriodoAndStatus(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("status") StatusReserva status
    );

    // ✅ Buscar reservas por data de entrada e status
    List<Reserva> findByDataEntradaAndStatusIn(LocalDate dataEntrada, List<StatusReserva> status);

    List<Reserva> findByDataEntradaAndStatus(LocalDate dataEntrada, StatusReserva status);

    List<Reserva> findByDataEntradaBeforeAndStatus(LocalDate data, StatusReserva status);

    int countByDataEntradaGreaterThanEqualAndDataEntradaLessThan(LocalDate inicio, LocalDate fimExclusivo);

    default int countBetween(LocalDate inicio, LocalDate fimExclusivo) {
        return countByDataEntradaGreaterThanEqualAndDataEntradaLessThan(inicio, fimExclusivo);
    }

    int countByDataEntradaLessThanAndDataSaidaGreaterThan(LocalDate fimExclusivo, LocalDate inicio);

    // ✅ Pendentes para HOJE (já existia)
    @Query(
            "SELECT r FROM Reserva r " +
                    "WHERE r.status = br.com.pousda.pousada.reservas.domain.StatusReserva.PENDENTE " +
                    "AND r.dataEntrada = :hoje"
    )
    List<Reserva> findPendentesParaHoje(@Param("hoje") LocalDate hoje);

    @Query(
            "SELECT r FROM Reserva r " +
                    "WHERE r.status = br.com.pousda.pousada.reservas.domain.StatusReserva.PENDENTE " +
                    "AND r.dataEntrada = :hoje"
    )
    List<Reserva> findPendentesUltimaChamada(@Param("hoje") LocalDate hoje);

    @Query(
            "SELECT r FROM Reserva r " +
                    "WHERE r.status = br.com.pousda.pousada.reservas.domain.StatusReserva.PENDENTE " +
                    "AND r.dataEntrada = :hoje"
    )
    List<Reserva> findPendentesParaCancelamentoHoje(@Param("hoje") LocalDate hoje);

    List<Reserva> findByStatus(StatusReserva status);

    List<Reserva> findByStatusIn(List<StatusReserva> statuses);

    List<Reserva> findByDataEntradaBetween(LocalDate start, LocalDate end);

    Optional<Reserva> findByCpf(String cpf);

    List<Reserva> findByCreatedBy(Long usuarioId);

    Optional<Reserva> findTopByCodigoStartingWithOrderByCodigoDesc(String prefixo);

    long countByStatus(StatusReserva status);

    // ✅ KPI FILTRADO POR PERÍODO: reservas pendentes dentro do intervalo
    @Query(
            "SELECT COUNT(r) FROM Reserva r " +
                    "WHERE r.status = :status " +
                    "AND r.dataEntrada BETWEEN :inicio AND :fim"
    )
    long countPendentesNoPeriodo(@Param("status") StatusReserva status,
                                 @Param("inicio") LocalDate inicio,
                                 @Param("fim") LocalDate fim);
}
