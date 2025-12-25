package br.com.pousda.pousada.notificacoes.metas.application;


import br.com.pousda.pousada.notificacoes.application.NotificationService;
import br.com.pousda.pousada.notificacoes.application.Templates;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationOrigin;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MetasNotifierImpl implements MetasNotifier {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private final NotificationService notifications;
    private final Templates tpl;

    private String today() { return LocalDate.now(SP).toString(); }

    @Override
    public void resumoSemanal(int total, Set<Long> destinatarios) {
        notifications.send(
                NotificationType.META_RESUMO_SEMANAL, "Resumo semanal de metas",
                tpl.metaResumoSemanal(total), "/metas?range=semana", "VER_METAS", null,
                today(), null, null, NotificationOrigin.AUTOMATICO, destinatarios
        );
    }

    @Override
    public void resumoMensal(int total, Set<Long> destinatarios) {
        notifications.send(
                NotificationType.META_RESUMO_MENSAL, "Resumo mensal de metas",
                tpl.metaResumoMensal(total), "/metas?range=mes", "VER_METAS", null,
                today(), null, null, NotificationOrigin.AUTOMATICO, destinatarios
        );
    }
}
