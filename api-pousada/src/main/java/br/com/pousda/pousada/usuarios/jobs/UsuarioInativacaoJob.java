package br.com.pousda.pousada.usuarios.jobs;

import br.com.pousda.pousada.notificacoes.application.facade.NotifierFacade;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UsuarioInativacaoJob {

    private final UsuarioRepository repo;
    private final NotifierFacade notifier;

    // roda todo dia 09:00 BRT
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Sao_Paulo")
    public void processarInativos() {
        List<Usuario> inativos = repo.findAll(); // ideal: crie um finder específico!
        LocalDateTime agora = LocalDateTime.now();

        for (Usuario u : inativos) {
            if (u.isAtivo() || u.getInativadoEm() == null) continue;

            long diasInativo = Duration.between(u.getInativadoEm(), agora).toDays();
            long diasRestantes = 30 - diasInativo;

            if (diasRestantes <= 0) {
                try { notifier.usuarioExcluidoAutomaticamente(u.getId(), u.getNome()); } catch (Exception ignored) {}
                repo.deleteById(u.getId());
                continue;
            }

            if (diasRestantes == 3 || diasRestantes == 2) {
                try { notifier.usuarioExclusaoEmDias(u.getId(), u.getNome(), (int) diasRestantes); } catch (Exception ignored) {}
            }
        }
    }
}
