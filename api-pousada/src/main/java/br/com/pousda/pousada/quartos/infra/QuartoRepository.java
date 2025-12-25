package br.com.pousda.pousada.quartos.infra;

import br.com.pousda.pousada.quartos.domain.Quarto;
import br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto;
import br.com.pousda.pousada.quartos.domain.enuns.TipoQuarto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuartoRepository extends JpaRepository<Quarto, Long> {

    Optional<Quarto> findByNumero(String numero);
    boolean existsByNumero(String numero);

    List<Quarto> findByStatus(StatusQuarto status);
    List<Quarto> findByTipo(TipoQuarto tipo);
    List<Quarto> findByStatusAndTipo(StatusQuarto status, TipoQuarto tipo);

    @Query("SELECT q FROM Quarto q WHERE LOWER(q.numero) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(q.nome) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Quarto> searchByNumeroOrNome(@Param("term") String term);

    @Query("SELECT q FROM Quarto q WHERE q.status = :status AND (LOWER(q.numero) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(q.nome) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Quarto> searchByStatusAndTerm(@Param("status") StatusQuarto status, @Param("term") String term);

    @Query("SELECT q FROM Quarto q WHERE q.tipo = :tipo AND (LOWER(q.numero) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(q.nome) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Quarto> searchByTipoAndTerm(@Param("tipo") TipoQuarto tipo, @Param("term") String term);

    @Query("SELECT q FROM Quarto q WHERE q.status = :status AND q.tipo = :tipo AND (LOWER(q.numero) LIKE LOWER(CONCAT('%', :term, '%')) OR LOWER(q.nome) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Quarto> searchByStatusAndTipoAndTerm(@Param("status") StatusQuarto status, @Param("tipo") TipoQuarto tipo, @Param("term") String term);

    @Query("SELECT q FROM Quarto q WHERE q.status = 'MANUTENCAO' AND q.dataManutencaoDesde <= :dataLimite")
    List<Quarto> findEmManutencaoDesdeOuAntes(@Param("dataLimite") LocalDate dataLimite);

    @Query("SELECT COUNT(q) FROM Quarto q WHERE q.status = :status")
    long countByStatus(@Param("status") StatusQuarto status);


    @Query("select count(q) from Quarto q")
    long countQuartosAtivos();

}