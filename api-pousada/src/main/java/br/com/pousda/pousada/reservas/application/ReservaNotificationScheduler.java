package br.com.pousda.pousada.reservas.application;

import br.com.pousda.pousada.notificacoes.application.recipients.NotificationRecipientsService;
import br.com.pousda.pousada.notificacoes.reserva.application.ReservaNotifier;
import br.com.pousda.pousada.reservas.domain.Reserva;
import br.com.pousda.pousada.reservas.domain.StatusReserva;
import br.com.pousda.pousada.reservas.infra.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaNotificationScheduler {

    private final ReservaRepository reservaRepository;
    private final ReservaNotifier reservaNotifier;
    private final NotificationRecipientsService recipientsService;

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");

    // 📅 Resumo da véspera - 18:00 (SÓ SE TIVER RESERVAS)
    @Scheduled(cron = "0 0 18 * * ?", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void notificarResumoVespera() {
        try {
            LocalDate amanha = LocalDate.now(ZONE_ID).plusDays(1);
            log.info("🔔 Verificando reservas para amanhã: {}", amanha);

            List<Reserva> reservasAmanha = reservaRepository.findByDataEntradaAndStatusIn(
                    amanha, List.of(StatusReserva.PENDENTE, StatusReserva.CONFIRMADA)
            );

            log.info("📊 Reservas encontradas para amanhã: {}", reservasAmanha.size());

            // ✅ SÓ NOTIFICA SE TIVER RESERVAS
            if (reservasAmanha.isEmpty()) {
                log.info("✅ Nenhuma reserva para amanhã - Notificação omitida");
                return;
            }

            // Formatar lista de quartos de forma amigável
            String listaQuartos = formatarListaQuartosAmigavel(reservasAmanha);

            Set<Long> destinatarios = recipientsService.getOperationalRecipients();

            reservaNotifier.resumoVespera(reservasAmanha.size(), listaQuartos, destinatarios);
            log.info("✅ Resumo véspera enviado: {} reservas - Quartos: {}",
                    reservasAmanha.size(), listaQuartos);

        } catch (Exception e) {
            log.error("❌ Erro ao enviar resumo véspera: {}", e.getMessage(), e);
        }
    }

    // 📋 Check-ins pendentes hoje - 08:00 (SÓ SE TIVER RESERVAS)
    @Scheduled(cron = "0 0 8 * * ?", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void notificarHojePendente() {
        try {
            LocalDate hoje = LocalDate.now(ZONE_ID);
            log.info("🔔 Verificando check-ins pendentes para hoje: {}", hoje);

            List<Reserva> reservasPendentes = reservaRepository.findPendentesParaHoje(hoje);

            log.info("📊 Check-ins pendentes encontrados: {}", reservasPendentes.size());

            // ✅ SÓ NOTIFICA SE TIVER RESERVAS PENDENTES
            if (reservasPendentes.isEmpty()) {
                log.info("✅ Nenhum check-in pendente hoje - Notificação omitida");
                return;
            }

            // Formatar lista de quartos de forma amigável
            String listaQuartos = formatarListaQuartosAmigavel(reservasPendentes);

            Set<Long> destinatarios = recipientsService.getOperationalRecipients();

            reservaNotifier.hojePendente(reservasPendentes.size(), listaQuartos, destinatarios);
            log.info("✅ Check-ins pendentes enviados: {} reservas - Quartos: {}",
                    reservasPendentes.size(), listaQuartos);

        } catch (Exception e) {
            log.error("❌ Erro ao enviar check-ins pendentes: {}", e.getMessage(), e);
        }
    }

    // ⏰ Última chamada - 20:30 (SÓ SE TIVER RESERVAS PENDENTES)
    @Scheduled(cron = "0 30 20 * * ?", zone = "America/Sao_Paulo")
    @Transactional(readOnly = true)
    public void notificarUltimaChamada() {
        try {
            LocalDate hoje = LocalDate.now(ZONE_ID);
            log.info("🔔 Verificando últimas chamadas para hoje: {}", hoje);

            List<Reserva> reservasPendentes = reservaRepository.findPendentesParaHoje(hoje);

            log.info("📊 Reservas para última chamada: {}", reservasPendentes.size());

            // ✅ SÓ NOTIFICA SE TIVER RESERVAS PENDENTES
            if (reservasPendentes.isEmpty()) {
                log.info("✅ Nenhuma reserva para última chamada - Notificação omitida");
                return;
            }

            Set<Long> destinatarios = recipientsService.getOperationalRecipients();

            // Notificação em lote
            reservaNotifier.ultimaChamadaLote(reservasPendentes.size(), destinatarios);

            // Notificações individuais para cada reserva
            for (Reserva reserva : reservasPendentes) {
                reservaNotifier.ultimaChamada(
                        reserva.getId(),
                        reserva.getCodigo(),
                        reserva.getNome(),
                        reserva.getQuarto() != null ? reserva.getQuarto().getNumero() : "Sem quarto",
                        destinatarios
                );
            }

            log.info("✅ Últimas chamadas enviadas: {} reservas", reservasPendentes.size());
        } catch (Exception e) {
            log.error("❌ Erro ao enviar últimas chamadas: {}", e.getMessage(), e);
        }
    }

    // ❌ Cancelamento automático - 23:00 (SÓ SE TIVER RESERVAS PENDENTES)
    @Scheduled(cron = "0 0 23 * * ?", zone = "America/Sao_Paulo")
    @Transactional
    public void cancelarReservasNaoConfirmadas() {
        try {
            LocalDate hoje = LocalDate.now(ZONE_ID);
            log.info("🔔 Verificando cancelamentos automáticos para hoje: {}", hoje);

            List<Reserva> reservasPendentes = reservaRepository.findPendentesParaHoje(hoje);

            log.info("📊 Reservas para cancelamento automático: {}", reservasPendentes.size());

            // ✅ SÓ CANCELA SE TIVER RESERVAS PENDENTES
            if (reservasPendentes.isEmpty()) {
                log.info("✅ Nenhuma reserva para cancelamento - Processo omitido");
                return;
            }

            Set<Long> destinatarios = recipientsService.getOperationalRecipients();

            for (Reserva reserva : reservasPendentes) {
                // Cancelar a reserva
                reserva.setStatus(StatusReserva.CANCELADA);
                reserva.setCancelledEm(LocalDateTime.now(ZONE_ID));
                reserva.setMotivoCancelamento("Cancelada automaticamente por falta de confirmação");

                reservaRepository.save(reserva);

                // Notificar cancelamento individual
                reservaNotifier.naoConfirmadaCancelada(
                        reserva.getId(),
                        reserva.getCodigo(),
                        reserva.getNome(),
                        destinatarios
                );

                log.info("❌ Reserva cancelada: {}", reserva.getCodigo());
            }

            // Notificação em lote
            reservaNotifier.naoConfirmadaCanceladaLote(reservasPendentes.size(), destinatarios);

            log.info("✅ Cancelamentos automáticos concluídos: {} reservas", reservasPendentes.size());
        } catch (Exception e) {
            log.error("❌ Erro ao cancelar reservas automaticamente: {}", e.getMessage(), e);
        }
    }

    // 🎯 MÉTODO AUXILIAR: Formatar lista de quartos de forma amigável
    private String formatarListaQuartosAmigavel(List<Reserva> reservas) {
        List<String> quartos = reservas.stream()
                .map(r -> r.getQuarto() != null ? r.getQuarto().getNumero() : "Sem quarto")
                .distinct()
                .collect(Collectors.toList());

        if (quartos.isEmpty()) {
            return "Nenhum quarto";
        }

        if (quartos.size() == 1) {
            return quartos.get(0);
        }

        if (quartos.size() == 2) {
            return quartos.get(0) + " e " + quartos.get(1);
        }

        // Para 3 ou mais: "101, 102 e 103"
        String todosMenosUltimo = String.join(", ", quartos.subList(0, quartos.size() - 1));
        return todosMenosUltimo + " e " + quartos.get(quartos.size() - 1);
    }
}