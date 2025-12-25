package br.com.pousda.pousada.notificacoes.api;

import br.com.pousda.pousada.notificacoes.application.UsersQueryPort;
import br.com.pousda.pousada.notificacoes.core.domain.dto.NotificationDTO;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationStatus;
import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import br.com.pousda.pousada.notificacoes.core.formatter.NotificationFormatterRegistry;
import br.com.pousda.pousada.notificacoes.core.infra.model.NotificationRead;
import br.com.pousda.pousada.notificacoes.core.infra.repo.NotificationReadRepository;
import br.com.pousda.pousada.notificacoes.core.infra.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationRepository repo;
    private final NotificationReadRepository readRepo;
    private final UsersQueryPort users;
    private final NotificationFormatterRegistry formatterRegistry;

    /* =========================================================
       BADGE: Não lidas (por usuário, ignorando as já marcadas lidas)
       ========================================================= */
    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public Map<String, Integer> unreadCount() {
        long uid = users.currentUserId();
        if (uid == 0L) return Collections.singletonMap("count", 0);

        Instant now = Instant.now();
        List<Notification> ativas = repo.findActiveForUser(uid, now);

        Set<Long> ids = ativas.stream().map(Notification::getId).collect(Collectors.toSet());
        Set<Long> lidas = readRepo.findIdsLidas(uid, ids);

        int count = 0;
        for (Notification n : ativas) {
            if (lidas.contains(n.getId())) continue;
            if (n.getStatus() == NotificationStatus.NOVO) count++;
        }
        return Collections.singletonMap("count", count);
    }

    /* =========================================================
       LISTA com filtros, paginação e status por usuário (NOVO/LIDO)
       ========================================================= */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> listar(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "5") int size,
                                    @RequestParam(required = false) String q) {

        long uid = users.currentUserId();
        if (uid == 0L) {
            return ResponseEntity.status(401)
                    .body(Collections.singletonMap("error", "Usuário não autenticado"));
        }

        if (page < 0) page = 0;
        if (size <= 0) size = 5;
        if (size > 100) size = 100;

        NotificationStatus st = parseStatusParam(status);
        if (status != null && !status.trim().isEmpty() && st == null) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "Status inválido: " + status));
        }

        Instant now = Instant.now();

        List<Notification> base = repo.findActiveForUser(uid, now);

        String query = (q == null) ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            final String qq = query;
            base = base.stream()
                    .filter(n -> contains(n.getTitle(), qq) || contains(n.getBody(), qq))
                    .collect(Collectors.toList());
        }

        Set<Long> ids = base.stream().map(Notification::getId).collect(Collectors.toSet());
        Set<Long> lidas = readRepo.findIdsLidas(uid, ids);

        List<NotificationDTO> mapped = new ArrayList<>(base.size());
        for (Notification n : base) {
            NotificationDTO d = toDto(n);
            d.setStatus(lidas.contains(n.getId()) ? NotificationStatus.LIDO : NotificationStatus.NOVO);
            mapped.add(d);
        }

        if (st != null) {
            mapped = mapped.stream().filter(d -> d.getStatus() == st).collect(Collectors.toList());
        }

        int totalItems = mapped.size();
        int from = Math.max(0, page * size);
        int to = Math.min(totalItems, from + size);
        List<NotificationDTO> pageOut = (from < to) ? mapped.subList(from, to) : Collections.emptyList();

        Map<String, Object> body = new HashMap<>();
        body.put("items", pageOut);
        body.put("totalItems", totalItems);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(totalItems))
                .body(body);
    }

    /* =========================================================
       Buscar UMA notificação específica (com verificação de acesso)
       ========================================================= */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {
        long uid = users.currentUserId();
        if (uid == 0L) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "Não autenticado"));
        }

        Optional<Notification> opt = repo.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Notification n = opt.get();

        // Verificar se usuário tem acesso a esta notificação
        if (n.getRecipients() == null || !n.getRecipients().contains(uid)) {
            return ResponseEntity.status(403).body(Collections.singletonMap("error", "Sem acesso a esta notificação"));
        }

        // Verificar se está expirada
        if (n.getExpiresAt() != null && n.getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.status(410).body(Collections.singletonMap("error", "Notificação expirada"));
        }

        NotificationDTO dto = toDto(n);

        // Verificar se já foi lida
        boolean jaLida = readRepo.findByNotificationAndUserId(n, uid).isPresent();
        dto.setStatus(jaLida ? NotificationStatus.LIDO : NotificationStatus.NOVO);

        return ResponseEntity.ok(dto);
    }

    /* =========================================================
       Estatísticas das notificações (para debug)
       ========================================================= */
    @GetMapping("/estatisticas")
    @Transactional(readOnly = true)
    public ResponseEntity<?> estatisticas() {
        long uid = users.currentUserId();
        if (uid == 0L) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "Não autenticado"));
        }

        try {
            Instant now = Instant.now();
            List<Notification> ativas = repo.findActiveForUser(uid, now);
            Set<Long> ids = ativas.stream().map(Notification::getId).collect(Collectors.toSet());
            Set<Long> lidas = readRepo.findIdsLidas(uid, ids);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalAtivas", ativas.size());
            stats.put("naoLidas", (int) ativas.stream()
                    .filter(n -> !lidas.contains(n.getId()))
                    .count());
            stats.put("lidas", lidas.size());

            // Agrupar por tipo - CORREÇÃO: converter NotificationType para String
            Map<String, Long> porTipo = ativas.stream()
                    .collect(Collectors.groupingBy(
                            n -> n.getType().name(),  // Converter enum para string
                            Collectors.counting()
                    ));
            stats.put("porTipo", porTipo);

            // Últimas 5 notificações
            List<Map<String, Object>> ultimas = ativas.stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .limit(5)
                    .map(n -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", n.getId());
                        info.put("type", n.getType().name()); // Converter para string
                        info.put("title", n.getTitle());
                        info.put("createdAt", n.getCreatedAt());
                        info.put("lida", lidas.contains(n.getId()));
                        return info;
                    })
                    .collect(Collectors.toList());
            stats.put("ultimas", ultimas);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Erro ao buscar estatísticas: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Collections.singletonMap("error", "Erro ao buscar estatísticas"));
        }
    }

    /* =========================================================
       Marcar UMA como lida (por usuário)
       ========================================================= */
    @PatchMapping("/{id}/lida")
    @Transactional
    public ResponseEntity<?> marcarComoLida(@PathVariable Long id) {
        long uid = users.currentUserId();
        if (uid == 0L) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "Não autenticado"));
        }

        Optional<Notification> opt = repo.findById(id);
        if (!opt.isPresent()) return ResponseEntity.notFound().build();

        Notification n = opt.get();
        if (n.getRecipients() == null || !n.getRecipients().contains(uid)) {
            return ResponseEntity.status(403).body(Collections.singletonMap("error", "Sem acesso a esta notificação"));
        }

        if (!readRepo.findByNotificationAndUserId(n, uid).isPresent()) {
            readRepo.save(NotificationRead.builder()
                    .notification(n)
                    .userId(uid)
                    .readAt(Instant.now())
                    .build());
        }
        return ResponseEntity.noContent().build();
    }

    /* =========================================================
       Marcar TODAS como lidas (por usuário)
       ========================================================= */
    @PostMapping("/marcar-todas-como-lidas")
    @Transactional
    public ResponseEntity<?> marcarTodasComoLidas() {
        long uid = users.currentUserId();
        if (uid == 0L) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "Não autenticado"));
        }

        Instant now = Instant.now();
        List<Notification> ativas = repo.findActiveForUser(uid, now);

        Set<Long> jaLidas = readRepo.findIdsLidas(uid,
                ativas.stream().map(Notification::getId).collect(Collectors.toSet()));

        for (Notification n : ativas) {
            if (jaLidas.contains(n.getId())) continue;
            readRepo.save(NotificationRead.builder()
                    .notification(n)
                    .userId(uid)
                    .readAt(Instant.now())
                    .build());
        }
        return ResponseEntity.noContent().build();
    }


    /* ================== Helpers ================== */

    private boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private NotificationStatus parseStatusParam(String status) {
        if (status == null || status.trim().isEmpty()) return null;
        String s = status.trim().toUpperCase(Locale.ROOT);
        if ("NAO_LIDA".equals(s) || "NAO_LIDO".equals(s) || "UNREAD".equals(s)) return NotificationStatus.NOVO;
        if ("LIDA".equals(s) || "LIDO".equals(s) || "READ".equals(s)) return NotificationStatus.LIDO;
        try { return NotificationStatus.valueOf(s); } catch (IllegalArgumentException ex) { return null; }
    }

    private NotificationDTO toDto(Notification n) {
        NotificationDTO d = new NotificationDTO();
        d.setId(n.getId());
        d.setType(n.getType());
        d.setTitle(n.getTitle());
        d.setBody(n.getBody());
        d.setBodyFormatted(formatterRegistry.format(n));
        d.setLink(n.getLink());
        d.setAction(n.getAction());
        d.setItemId(n.getItemId());
        d.setDate(n.getDate());
        d.setOrigin(n.getOrigin());
        d.setStatus(n.getStatus());
        d.setRecipients(n.getRecipients());
        d.setRecipientsCount(d.getRecipients() != null ? d.getRecipients().size() : 0);
        d.setRecipientsLabel(labelRecipients(n.getRecipients()));
        d.setCreatedAt(n.getCreatedAt());
        d.setExpiresAt(n.getExpiresAt());

        if (n.getExpiresAt() != null) {
            long secsLeft = n.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
            int days = (int) Math.max(0, secsLeft / 86400);
            d.setExpiresInDays(days);
            d.setExpiresInLabel(days > 0 ? "expira em " + days + " dias" : "expirada");
        }
        return d;
    }

    /** Agora inclui GERENTE no label para ficar coerente com os destinatários. */
    private String labelRecipients(Set<Long> recipients) {
        if (recipients == null || recipients.isEmpty()) return "-";
        try {
            Set<Long> admins   = users.adminIds();
            Set<Long> devs     = users.devIds();
            Set<Long> gerentes = users.gerenteIds();

            boolean vaiParaDev     = devs     != null && !Collections.disjoint(recipients, devs);
            boolean vaiParaGerente = gerentes != null && !Collections.disjoint(recipients, gerentes);
            boolean vaiParaAdmin   = admins   != null && !Collections.disjoint(recipients, admins);

            List<String> tags = new ArrayList<>();
            if (vaiParaDev)     tags.add("DEV");
            if (vaiParaGerente) tags.add("GERENTE");
            if (vaiParaAdmin)   tags.add("ADMIN");

            if (!tags.isEmpty()) return String.join(", ", tags);
        } catch (Throwable ignored) {}
        return "ids: " + recipients;
    }
}