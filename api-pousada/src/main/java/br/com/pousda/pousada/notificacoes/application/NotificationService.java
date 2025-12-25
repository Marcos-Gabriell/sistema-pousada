package br.com.pousda.pousada.notificacoes.application;

import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationOrigin;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationStatus;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import br.com.pousda.pousada.notificacoes.core.infra.model.NotificationRead;
import br.com.pousda.pousada.notificacoes.core.infra.repo.NotificationReadRepository;
import br.com.pousda.pousada.notificacoes.core.infra.repo.NotificationRepository;
import br.com.pousda.pousada.security.AuthPrincipal;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;
    private final NotificationPublisher publisher;
    private final NotificationReadRepository readRepo;
    private final UsersQueryPort users;              // <-- só a porta (adapter @Primary cuidará da impl)
    private final UsuarioRepository usuarioRepo;

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(SP);

    private static final long TTL_SECONDS_90_DIAS = 90L * 24L * 60L * 60L;

    /**
     * Cria e publica uma nova notificação.
     */
    public Notification send(
            NotificationType type,
            String title,
            String body,
            String link,
            String action,
            Long itemId,
            String dateText,
            Long autorId,
            String autorJson,
            NotificationOrigin origin,
            Set<?> recipients
    ) {
        final Instant now = Instant.now();

        // -------- autor --------
        Long fromCtx = resolveFromSecurityContext();
        Long fromPort = (fromCtx == null) ? resolveFromUsersPort() : null;

        if (autorId == null || autorId <= 0) {
            autorId = (fromCtx != null) ? fromCtx : fromPort;
        }
        log.debug("[NotificationService] Autor resolvido -> explicit={}, ctx={}, port={}, usado={}",
                autorId, fromCtx, fromPort, autorId);

        // -------- autorJson --------
        if ((autorJson == null || autorJson.isBlank()) && autorId != null && autorId > 0) {
            autorJson = buildAutorJson(autorId);
        }

        // -------- recipients --------
        Set<Long> recips = toLongSet(recipients);
        log.debug("[NotificationService] Enviando '{}' -> recipients={}, autorId={}, origin={}",
                (type != null ? type.name() : "NULL"), recips, autorId,
                (origin != null ? origin : NotificationOrigin.AUTOMATICO));

        Notification n = new Notification();
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setAction(action);
        n.setItemId(itemId);
        n.setDate((dateText != null && !dateText.isBlank()) ? dateText : DTF.format(now));
        n.setOrigin(origin != null ? origin : NotificationOrigin.AUTOMATICO);
        n.setStatus(NotificationStatus.NOVO);
        n.setRecipients(recips);
        n.setAutorId(autorId);
        n.setAutorJson(autorJson);
        n.setCreatedAt(now);
        n.setExpiresAt(now.plusSeconds(TTL_SECONDS_90_DIAS));

        Notification saved = repo.save(n);

        marcarAutorComoLido(saved);
        publisher.publish(saved);
        return saved;
    }

    // ================= helpers =================

    /** Marca o autor como “lido” se ele também for destinatário. */
    private void marcarAutorComoLido(Notification saved) {
        try {
            if (saved.getAutorId() != null &&
                    saved.getRecipients() != null &&
                    saved.getRecipients().contains(saved.getAutorId()) &&
                    !readRepo.findByNotificationAndUserId(saved, saved.getAutorId()).isPresent()) {

                readRepo.save(NotificationRead.builder()
                        .notification(saved)
                        .userId(saved.getAutorId())
                        .readAt(Instant.now())
                        .build());

                log.debug("[NotificationService] autorId={} marcado como LIDO para notificationId={}",
                        saved.getAutorId(), saved.getId());
            }
        } catch (Exception e) {
            log.warn("[NotificationService] Falha ao marcar autor como LIDO: {}", e.getMessage());
        }
    }

    /** Converte qualquer Set<?> em Set<Long> (compatível com Java 11). */
    private Set<Long> toLongSet(Set<?> input) {
        if (input == null) return Collections.emptySet();
        Set<Long> out = new HashSet<>();
        for (Object o : input) {
            if (o == null) continue;
            if (o instanceof Long) out.add((Long) o);
            else if (o instanceof Integer) out.add(((Integer) o).longValue());
            else if (o instanceof Number) out.add(((Number) o).longValue());
            else if (o instanceof String) {
                try { out.add(Long.parseLong(((String) o).trim())); }
                catch (NumberFormatException nfe) {
                    log.warn("[NotificationService] Recipient inválido (String não numérica): {}", o);
                }
            } else {
                log.warn("[NotificationService] Recipient tipo não suportado: {} ({})",
                        o, o.getClass().getName());
            }
        }
        return out;
    }

    /** Resolve autorId do SecurityContext. */
    private Long resolveFromSecurityContext() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthPrincipal) {
                Long id = ((AuthPrincipal) auth.getPrincipal()).getId();
                log.debug("[NotificationService] SecurityContext autorId={}", id);
                return id;
            }
        } catch (Exception e) {
            log.debug("[NotificationService] SecurityContext indisponível: {}", e.getMessage());
        }
        return null;
    }

    /** Resolve autorId via UsersQueryPort (sem ID fixo). */
    private Long resolveFromUsersPort() {
        try {
            long id = users.currentUserId();
            if (id > 0) {
                log.debug("[NotificationService] UsersQueryPort autorId={}", id);
                return id;
            }
        } catch (Exception e) {
            log.debug("[NotificationService] UsersQueryPort falhou: {}", e.getMessage());
        }
        return null;
    }

    /** Monta JSON simples do autor usando o banco. */
    private String buildAutorJson(Long id) {
        try {
            Optional<Usuario> opt = usuarioRepo.findById(id);
            if (opt.isPresent()) {
                Usuario u = opt.get();
                return asAutorJson(u);
            }
        } catch (Exception e) {
            log.warn("[NotificationService] Falha ao buscar autor {}: {}", id, e.getMessage());
        }
        return "{\"id\":" + id + ",\"nome\":\"-\"}";
    }

    private String asAutorJson(Usuario u) {
        String nome = (u.getNome() == null) ? "-" : escape(u.getNome());
        return "{\"id\":" + u.getId() + ",\"nome\":\"" + nome + "\"}";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
