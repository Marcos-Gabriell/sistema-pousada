package br.com.pousda.pousada.hospedagens.application.jobs;

import br.com.pousda.pousada.hospedagens.domain.Hospedagem;
import br.com.pousda.pousada.hospedagens.infra.HospedagemRepository;
import br.com.pousda.pousada.notificacoes.application.facade.NotifierFacade;
import br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class HospedagemCheckoutJobs {

    private static final ZoneId BAHIA = ZoneId.of("America/Bahia");

    private final HospedagemRepository repo;
    private final NotifierFacade notifier;

    /**
     * 11:00 — lembrete para quem tem checkout hoje e ainda está com o quarto OCUPADO
     */
    @Scheduled(cron = "0 0 11 * * *", zone = "America/Bahia")
    public void enviarLembretesCheckout11h() {
        LocalDate hoje = LocalDate.now(BAHIA);

        List<Hospedagem> pendentes = repo.findByDataSaida(hoje).stream()
                .filter(this::isAtivaParaCheckoutAutomatico)
                .collect(Collectors.toList());

        log.info("[JOB] Lembrete 11h: {} hospedagens pendentes", pendentes.size());

        pendentes.forEach(h -> {
            try {
                notifier.hospedagemLembreteCheckout11h(h);
            } catch (Exception e) {
                log.warn("[JOB] Falha ao notificar lembrete de checkout (id={}): {}", h.getId(), e.getMessage());
            }
        });
    }

    @Transactional
    @Scheduled(cron = "0 0 12 * * *", zone = "America/Bahia")
    public void checkoutAutomaticoAoMeioDia() {
        LocalDate hoje = LocalDate.now(BAHIA);

        List<Hospedagem> pendentes = repo.findByDataSaida(hoje).stream()
                .filter(this::isAtivaParaCheckoutAutomatico)
                .collect(Collectors.toList());

        log.info("[JOB] Checkout automático 12h: {} hospedagens", pendentes.size());

        for (Hospedagem h : pendentes) {
            // Recalcular dias hospedados (do check-in até hoje)
            long diasHospedados = ChronoUnit.DAYS.between(h.getDataEntrada(), hoje);
            if (diasHospedados <= 0) diasHospedados = 1;

            Double vd = h.getValorDiaria() != null ? h.getValorDiaria() : 0d;
            h.setValorTotal(vd * diasHospedados);

            // Liberar o quarto
            if (h.getQuarto() != null) {
                h.getQuarto().setStatus(StatusQuarto.DISPONIVEL);
            }

            repo.save(h);

            try {
                notifier.hospedagemCheckoutAutomaticoConcluido(h);
            } catch (Exception e) {
                log.warn("[JOB] Falha ao notificar checkout automático (id={}): {}", h.getId(), e.getMessage());
            }
        }
    }

    private boolean isAtivaParaCheckoutAutomatico(Hospedagem h) {
        if (h == null) return false;

        // Cancelada nunca é ativa
        if (Boolean.TRUE.equals(h.getCancelada()) || Boolean.TRUE.equals(h.getCancelado())) {
            return false;
        }

        LocalDate hoje = LocalDate.now(BAHIA);
        LocalDate dataSaida = h.getDataSaida();

        // Só considera ativa se a data de saída é HOJE
        if (dataSaida == null || !dataSaida.isEqual(hoje)) {
            return false;
        }

        // E o quarto ainda está OCUPADO (não fez checkout manual)
        StatusQuarto statusQuarto = (h.getQuarto() != null ? h.getQuarto().getStatus() : null);
        return statusQuarto == StatusQuarto.OCUPADO;
    }
}