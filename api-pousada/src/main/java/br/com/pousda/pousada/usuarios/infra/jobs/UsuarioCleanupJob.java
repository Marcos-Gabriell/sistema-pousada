package br.com.pousda.pousada.usuarios.infra.jobs;


import br.com.pousda.pousada.notificacoes.application.facade.NotifierFacade;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsuarioCleanupJob {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private final UsuarioRepository repo;
    private final NotifierFacade notifier;

    // todos os dias às 02:20 BRT
    @Scheduled(cron = "0 20 2 * * *", zone = "America/Sao_Paulo")
    public void excluirInativosComMaisDe7Dias() {
        LocalDateTime limite = LocalDateTime.now(SP).minusDays(7);
        List<Usuario> candidatos = repo.findAllByAtivoFalseAndInativadoEmBefore(limite);

        int excluidos = 0;
        for (Usuario u : candidatos) {
            if (u.isBootstrapAdmin()) continue; // nunca
            // regra extra opcional: não excluir ADMINs
            if ("ADMIN".equals(u.getRole())) continue;

            Long id = u.getId();
            String nome = u.getNome();
            repo.delete(u);
            excluidos++;

            // notificar admins sobre auto-exclusão
            try {
                notifier.usuarioAutoExcluido(u, "Usuário permaneceu inativo por 7 dias e foi removido automaticamente.");
            } catch (Exception e) {
                log.warn("Falha ao notificar auto-exclusão do usuário id={} nome={}: {}", id, nome, e.getMessage());
            }
        }
        if (excluidos > 0) {
            log.info("[02:20] Auto-exclusão de usuários inativos (>7 dias): {}", excluidos);
        }
    }
}
