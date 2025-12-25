package br.com.pousda.pousada.usuarios.infra;

import br.com.pousda.pousada.usuarios.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findFirstByNomeIgnoreCase(String nome);

    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByNumero(String numero);

    Optional<Usuario> findByUsernameOrEmail(String username, String email);
    Optional<Usuario> findByEmailIgnoreCaseOrUsernameIgnoreCaseOrNumero(String email, String username, String numero);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByNumero(String numero);
    boolean existsByNome(String nome);

    // ======= usados na atualização (excluir o próprio id) =======
    boolean existsByUsernameAndIdNot(String username, Long id);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByNumeroAndIdNot(String numero, Long id);
    boolean existsByNomeAndIdNot(String nome, Long id);

    // ======= login flexível =======
    @Query("select u from Usuario u where lower(u.email) = lower(:login) or lower(u.username) = lower(:login) or u.numero = :login")
    Optional<Usuario> findByLogin(@Param("login") String login);

    // ======= destinatários por role =======
    @Query("select u.id from Usuario u where u.ativo = true and upper(u.role) in (:roles)")
    List<Long> findIdsByRoleInAndAtivoTrue(@Param("roles") Set<String> roles);

    @Query("select u.id from Usuario u where u.ativo = true and upper(u.role) like concat('%', upper(:term), '%')")
    List<Long> findIdsByRoleContainsAndAtivoTrue(@Param("term") String term);

    List<Usuario> findByAtivoTrue();

    List<Usuario> findAllByAtivoFalseAndInativadoEmBefore(LocalDateTime limite);

    long countByRole(String role);
    Optional<Usuario> findFirstByRole(String role);

    // Evita falha de criação de query em alguns adapters legados
    @Query(value = "select null", nativeQuery = true)
    Long currentLoggedUserIdOrNull();

    Optional<Usuario> findTopByCodigoStartingWithOrderByCodigoDesc(String prefixo);

    // ======= necessários para o código sequencial =======
    boolean existsByCodigo(String codigo);

    @Query(value = "select coalesce(max(cast(substring(u.codigo from 5) as integer)), 0) from usuario u", nativeQuery = true)
    Long maxCodigoSequencia();


    long countByAtivoTrue();
}
