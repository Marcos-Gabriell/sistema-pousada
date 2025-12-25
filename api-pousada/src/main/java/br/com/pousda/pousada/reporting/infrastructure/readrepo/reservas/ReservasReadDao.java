package br.com.pousda.pousada.reporting.infrastructure.readrepo.reservas;

import br.com.pousda.pousada.reservas.domain.Reserva;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservasReadDao extends Repository<Reserva, Long> {

    @Query(value =
            "SELECT " +
                    "  r.codigo, " +
                    "  r.nome, " +                                  // <- era nome_hospede
                    "  COALESCE(q.numero,'') AS numero_quarto, " +
                    "  CAST(r.created_at AS date)      AS criada_em, " +
                    "  CAST(r.data_entrada AS date)    AS checkin_previsto, " +
                    "  CAST(r.status AS text)          AS status " +
                    "FROM reserva r " +
                    "LEFT JOIN quarto q ON q.id = r.quarto_id " +
                    "WHERE CAST(r.created_at AS date) BETWEEN :ini AND :fim " +
                    "ORDER BY r.created_at DESC",
            nativeQuery = true)
    List<Object[]> listar(@Param("ini") LocalDate ini, @Param("fim") LocalDate fim);

    @Query(value =
            "SELECT COUNT(*) FROM reserva " +
                    "WHERE status = 'PENDENTE' " +
                    "  AND CAST(created_at AS date) BETWEEN :ini AND :fim",
            nativeQuery = true)
    long countPendentes(@Param("ini") LocalDate ini, @Param("fim") LocalDate fim);

    @Query(value =
            "SELECT COUNT(*) FROM reserva " +
                    "WHERE status = 'CONFIRMADA' " +
                    "  AND CAST(created_at AS date) BETWEEN :ini AND :fim",
            nativeQuery = true)
    long countConfirmadas(@Param("ini") LocalDate ini, @Param("fim") LocalDate fim);

    @Query(value =
            "SELECT COUNT(*) FROM reserva " +
                    "WHERE status = 'CANCELADA' " +
                    "  AND CAST(created_at AS date) BETWEEN :ini AND :fim",
            nativeQuery = true)
    long countCanceladas(@Param("ini") LocalDate ini, @Param("fim") LocalDate fim);
}
