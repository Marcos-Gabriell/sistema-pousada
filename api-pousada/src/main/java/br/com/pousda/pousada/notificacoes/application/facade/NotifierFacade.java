package br.com.pousda.pousada.notificacoes.application.facade;

import br.com.pousda.pousada.hospedagens.domain.Hospedagem;
import br.com.pousda.pousada.notificacoes.application.context.UserContextService;
import br.com.pousda.pousada.notificacoes.application.parsing.ResumoParserService;
import br.com.pousda.pousada.notificacoes.application.recipients.NotificationRecipientsService;
import br.com.pousda.pousada.notificacoes.financeiro.application.FinanceiroNotifier;
import br.com.pousda.pousada.notificacoes.hospedagem.application.HospedagemNotifier;
import br.com.pousda.pousada.notificacoes.quarto.application.QuartoDiffFormatter;
import br.com.pousda.pousada.notificacoes.quarto.application.QuartoNotifier;
import br.com.pousda.pousada.notificacoes.reserva.application.ReservaDiff;
import br.com.pousda.pousada.notificacoes.reserva.application.ReservaNotifier;
import br.com.pousda.pousada.notificacoes.usuarios.application.UsuarioNotifier;
import br.com.pousda.pousada.quartos.domain.Quarto;
import br.com.pousda.pousada.quartos.dtos.QuartoChangeSet;
import br.com.pousda.pousada.reservas.domain.Reserva;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class NotifierFacade {

    private static final Logger log = LoggerFactory.getLogger(NotifierFacade.class);

    // Notifiers específicos por domínio
    private final ReservaNotifier reservaNotifier;
    private final HospedagemNotifier hospedagemNotifier;
    private final UsuarioNotifier usuarioNotifier;
    private final FinanceiroNotifier financeiroNotifier;
    private final QuartoNotifier quartoNotifier;

    // Serviços extraídos
    private final NotificationRecipientsService recipientsService;
    private final UserContextService userContextService;

    @Qualifier("parsingResumoParserService")
    private final ResumoParserService resumoParserService;

    /* ======================= RESERVA (ADMIN+GERENTE+DEV + AUTOR) ======================= */
    public void reservaCriada(Reserva r, Usuario autor) {
        try {
            String periodo = buildPeriodo(r);
            Long autorId = (autor != null) ? autor.getId() : userContextService.getCurrentUserId();
            String numeroQuarto = (r.getQuarto() != null) ? safe(r.getQuarto().getNumero()) : "-";

            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] reservaCriada -> recipients={}, autorId={}", dest, autorId);

            reservaNotifier.criada(
                    r.getId(),
                    safe(r.getCodigo()),
                    safe(r.getNome()),
                    numeroQuarto,
                    periodo,
                    autorId,
                    userContextService.getUserJson(autor),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar criação de reserva: {}", e.getMessage(), e);
        }
    }

    public void reservaAtualizada(Reserva before, Reserva after, Usuario autor) {
        try {
            String diff = ReservaDiff.diff(before, after);
            Long autorId = (autor != null) ? autor.getId() : userContextService.getCurrentUserId();

            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] reservaAtualizada -> recipients={}, autorId={}", dest, autorId);

            reservaNotifier.atualizada(
                    after.getId(),
                    safe(after.getCodigo()),
                    "dados",
                    "-",
                    diff,
                    autorId,
                    userContextService.getUserJson(autor),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar atualização de reserva: {}", e.getMessage(), e);
        }
    }

    // fallback simples
    public void reservaAtualizada(Reserva r) {
        try {
            Long autorId = userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] reservaAtualizada(fallback) -> recipients={}, autorId={}", dest, autorId);

            reservaNotifier.atualizada(
                    r.getId(),
                    safe(r.getCodigo()),
                    "dados",
                    "-",
                    "-",
                    autorId,
                    userContextService.getCurrentUserJson(),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar atualização de reserva (fallback): {}", e.getMessage(), e);
        }
    }

    public void reservaCancelada(Reserva r, Usuario autor, String motivo) {
        try {
            Long autorId = (autor != null) ? autor.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] reservaCancelada -> recipients={}, autorId={}", dest, autorId);

            reservaNotifier.cancelada(
                    r.getId(),
                    safe(r.getCodigo()),
                    safe(motivo),
                    autorId,
                    userContextService.getUserJson(autor),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar cancelamento de reserva: {}", e.getMessage(), e);
        }
    }

    public void reservaConfirmada(Reserva r, Usuario autor) {
        try {
            Long autorId = (autor != null) ? autor.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] reservaConfirmada -> recipients={}, autorId={}", dest, autorId);

            reservaNotifier.confirmada(
                    r.getId(),
                    safe(r.getCodigo()),
                    safe(r.getNome()),
                    autorId,
                    userContextService.getUserJson(autor),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar confirmação de reserva: {}", e.getMessage(), e);
        }
    }

    /* ===================== HOSPEDAGEM (ADMIN+GERENTE+DEV + AUTOR) ====================== */
    public void hospedagemCriada(Hospedagem h) {
        try {
            Long autorId = userContextService.getCurrentUserId();
            String autorNome = userContextService.getCurrentUserName();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] hospedagemCriada -> recipients={}, autorId={}", dest, autorId);

            hospedagemNotifier.criada(
                    h.getId(),
                    safe(h.getCodigoHospedagem()),
                    safe(autorNome),
                    safe(h.getNome()),
                    (h.getQuarto() != null ? safe(h.getQuarto().getNumero()) : "-"),
                    safeDate(h.getDataEntrada()),
                    safeDate(h.getDataSaida()),
                    autorId,
                    userContextService.getCurrentUserJson(),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar criação de hospedagem: {}", e.getMessage(), e);
        }
    }

    public void hospedagemAtualizada(Hospedagem h, String resumo) {
        try {
            Long autorId = userContextService.getCurrentUserId();
            String autorNome = userContextService.getCurrentUserName();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] hospedagemAtualizada -> recipients={}, autorId={}", dest, autorId);

            if (resumo != null && resumo.toLowerCase().contains("pagamento")) {
                ResumoParserService.Change ch = resumoParserService.parseResumoPagamento(resumo);
                hospedagemNotifier.atualizada(
                        h.getId(),
                        safe(h.getCodigoHospedagem()),
                        safe(autorNome),
                        safe(h.getNome()),
                        ch.campo,
                        ch.de,
                        ch.para,
                        autorId,
                        userContextService.getCurrentUserJson(),
                        dest
                );
            } else {
                ResumoParserService.Change ch = resumoParserService.parseResumoDePara(resumo);
                hospedagemNotifier.atualizada(
                        h.getId(),
                        safe(h.getCodigoHospedagem()),
                        safe(autorNome),
                        safe(h.getNome()),
                        ch.campo,
                        ch.de,
                        ch.para,
                        autorId,
                        userContextService.getCurrentUserJson(),
                        dest
                );
            }
        } catch (Exception e) {
            log.error("Erro ao notificar atualização de hospedagem: {}", e.getMessage(), e);
        }
    }

    /* ===================== HOSPEDAGEM - CHECKOUT MANUAL ====================== */
    public void hospedagemCheckoutManual(Hospedagem h, String motivo) {
        try {
            Long autorId = userContextService.getCurrentUserId();
            String autorNome = userContextService.getCurrentUserName();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] hospedagemCheckoutManual -> recipients={}, autorId={}", dest, autorId);

            hospedagemNotifier.checkoutManual(
                    h.getId(),
                    safe(h.getCodigoHospedagem()),
                    safe(autorNome),
                    safe(h.getNome()),
                    (h.getQuarto() != null ? safe(h.getQuarto().getNumero()) : "-"),
                    safeDate(h.getDataSaida()),
                    safe(motivo),
                    autorId,
                    userContextService.getCurrentUserJson(),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar checkout manual de hospedagem: {}", e.getMessage(), e);
        }
    }

    /** Lembrete 11h (automático) */
    public void hospedagemLembreteCheckout11h(Hospedagem h) {
        try {
            Set<Long> dest = recipientsService.getOperationalRecipients();
            log.debug("[NOTIFIER] hospedagemLembreteCheckout11h -> recipients={}", dest);

            String quarto = (h.getQuarto() != null) ? safe(h.getQuarto().getNumero()) : "-";
            hospedagemNotifier.checkoutLembrete11h(
                    h.getId(),
                    safe(h.getCodigoHospedagem()),
                    safe(h.getNome()),
                    quarto,
                    safeDate(h.getDataSaida()),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar lembrete de checkout: {}", e.getMessage(), e);
        }
    }

    /** Checkout automático concluído (automático) */
    public void hospedagemCheckoutAutomaticoConcluido(Hospedagem h) {
        try {
            Set<Long> dest = recipientsService.getOperationalRecipients();
            log.debug("[NOTIFIER] hospedagemCheckoutAutomaticoConcluido -> recipients={}", dest);

            String quarto = (h.getQuarto() != null) ? safe(h.getQuarto().getNumero()) : "-";
            hospedagemNotifier.checkoutAutomaticoConcluido(
                    h.getId(),
                    safe(h.getCodigoHospedagem()),
                    safe(h.getNome()),
                    quarto,
                    safeDate(h.getDataSaida()),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar checkout automático: {}", e.getMessage(), e);
        }
    }

    /* ======================== USUÁRIO (ADMIN+DEV + AUTOR) ====================== */
    public void usuarioCriado(Usuario ator, Usuario alvo) {
        try {
            Long autorId = (ator != null) ? ator.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getUserRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] usuarioCriado -> recipients={}, autorId={}", dest, autorId);

            usuarioNotifier.criado(
                    alvo.getId(),
                    safe(alvo.getNome()),
                    safe(alvo.getEmail()),
                    autorId,
                    userContextService.getUserJson(ator),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar criação de usuário: {}", e.getMessage(), e);
        }
    }

    public void usuarioAtualizado(Usuario ator, Usuario alvo, String mensagemRica) {
        try {
            Long autorId = (ator != null) ? ator.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getUserRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] usuarioAtualizado -> recipients={}, autorId={}", dest, autorId);

            usuarioNotifier.atualizado(
                    alvo.getId(),
                    safe(alvo.getNome()),
                    mensagemRica,
                    autorId,
                    userContextService.getUserJson(ator),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar atualização de usuário: {}", e.getMessage(), e);
        }
    }

    public void usuarioStatusAlterado(Usuario ator, Usuario alvo, boolean novoAtivo, String motivo) {
        try {
            Long autorId = (ator != null) ? ator.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getUserRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] usuarioStatusAlterado -> recipients={}, autorId={}", dest, autorId);

            usuarioNotifier.statusAlterado(
                    alvo.getId(),
                    safe(alvo.getNome()),
                    novoAtivo ? "ATIVO" : "INATIVO",
                    autorId,
                    userContextService.getUserJson(ator),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar alteração de status de usuário: {}", e.getMessage(), e);
        }
    }

    public void usuarioSenhaResetada(Usuario ator, Usuario alvo) {
        try {
            Long autorId = (ator != null) ? ator.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getUserRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] usuarioSenhaResetada -> recipients={}, autorId={}", dest, autorId);

            usuarioNotifier.senhaResetada(
                    alvo.getId(),
                    safe(alvo.getNome()),
                    autorId,
                    userContextService.getUserJson(ator),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar reset de senha: {}", e.getMessage(), e);
        }
    }

    public void usuarioExcluido(Usuario ator, Usuario alvo) {
        try {
            Long autorId = (ator != null) ? ator.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getUserRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] usuarioExcluido -> recipients={}, autorId={}", dest, autorId);

            usuarioNotifier.excluido(
                    alvo.getId(),
                    safe(alvo.getNome()),
                    autorId,
                    userContextService.getUserJson(ator),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar exclusão de usuário: {}", e.getMessage(), e);
        }
    }

    public void usuarioAutoExcluido(Usuario u, String motivo) {
        try {
            Long autorId = (u != null) ? u.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getUserRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] usuarioAutoExcluido -> recipients={}, autorId={}", dest, autorId);

            usuarioNotifier.autoExcluido(
                    u.getId(),
                    safe(u.getNome()),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar autoexclusão de usuário: {}", e.getMessage(), e);
        }
    }

    public void usuarioAtualizouProprioPerfil(Usuario u, String campo, String de, String para) {
        try {
            Long autorId = (u != null) ? u.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getUserRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] usuarioAtualizouProprioPerfil -> recipients={}, autorId={}", dest, autorId);

            usuarioNotifier.atualizouProprioPerfil(
                    u.getId(),
                    safe(u.getNome()),
                    safe(campo),
                    safe(de),
                    safe(para),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar atualização de perfil próprio: {}", e.getMessage(), e);
        }
    }

    public void usuarioSenhaAtualizadaPeloProprio(Usuario u) {
        try {
            Long autorId = (u != null) ? u.getId() : userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getUserRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] usuarioSenhaAtualizadaPeloProprio -> recipients={}, autorId={}", dest, autorId);

            usuarioNotifier.senhaAtualizadaPeloProprio(
                    u.getId(),
                    safe(u.getNome()),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar atualização de senha própria: {}", e.getMessage(), e);
        }
    }

    public void usuarioSenhaAtualizadaNoPrimeiroLogin(Usuario u) {
        try {
            Set<Long> dest = recipientsService.getUserRecipients();
            log.debug("[NOTIFIER] usuarioSenhaAtualizadaNoPrimeiroLogin -> recipients={}", dest);

            usuarioNotifier.senhaAtualizadaNoPrimeiroLogin(
                    u.getId(),
                    safe(u.getNome()),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar atualização de senha no primeiro login: {}", e.getMessage(), e);
        }
    }

    public void usuarioExcluidoAutomaticamente(Long usuarioId, String nome) {
        try {
            Set<Long> dest = recipientsService.getUserRecipients();
            log.debug("[NOTIFIER] usuarioExcluidoAutomaticamente -> recipients={}", dest);

            usuarioNotifier.excluidoAutomaticamente(
                    usuarioId,
                    safe(nome),
                    dest
            );
        } catch (Exception e) {
            log.error("Erro ao notificar exclusão automática de usuário: {}", e.getMessage(), e);
        }
    }

    public void usuarioExclusaoEmDias(Long usuarioId, String nome, int dias) {
        try {
            Set<Long> dest = recipientsService.getUserRecipients();
            log.debug("[NOTIFIER] usuarioExclusaoEmDias(dias={}) -> recipients={}", dias, dest);

            if (dias <= 0) {
                usuarioExcluidoAutomaticamente(usuarioId, nome);
                return;
            }
            if (dias <= 2) {
                usuarioNotifier.exclusaoEmDoisDias(usuarioId, safe(nome), dest);
                return;
            }
            usuarioNotifier.exclusaoEmDias(usuarioId, safe(nome), dias, dest);
        } catch (Exception e) {
            log.error("Erro ao notificar exclusão em dias de usuário: {}", e.getMessage(), e);
        }
    }

    /* === FINANCEIRO (ADMIN+GERENTE+DEV + AUTOR) === */
    public void finLancamentoCriado(Long id, String tipo, String origem,
                                    Double valor, String descricao,
                                    Long autorId, String autorJson, Set<Long> dest) {
        try {
            Set<Long> destinatarios = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] finLancamentoCriado -> recipients={}, autorId={}", destinatarios, autorId);

            financeiroNotifier.lancamentoCriado(
                    id, tipo, origem, valor, descricao,
                    autorId, autorJson, destinatarios
            );
        } catch (Exception e) {
            log.error("Erro ao notificar criação de lançamento: {}", e.getMessage(), e);
        }
    }

    public void finLancamentoAtualizado(Long id, String tipo, String origem,
                                        String deDescricao, Double deValor,
                                        String paraDescricao, Double paraValor,
                                        Long autorId, String autorJson, Set<Long> dest) {
        try {
            Set<Long> destinatarios = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] finLancamentoAtualizado -> recipients={}, autorId={}", destinatarios, autorId);

            financeiroNotifier.lancamentoAtualizado(
                    id, tipo, origem, deDescricao, deValor, paraDescricao, paraValor,
                    autorId, autorJson, destinatarios
            );
        } catch (Exception e) {
            log.error("Erro ao notificar atualização de lançamento: {}", e.getMessage(), e);
        }
    }

    public void finLancamentoCancelado(Long id, String tipo, String origem,
                                       Double valor, String motivo, String canceladoPorPerfil,
                                       Long autorId, String autorJson, Set<Long> dest) {
        try {
            Set<Long> destinatarios = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] finLancamentoCancelado -> recipients={}, autorId={}", destinatarios, autorId);

            financeiroNotifier.lancamentoCancelado(
                    id, tipo, origem, valor, motivo, canceladoPorPerfil,
                    autorId, autorJson, destinatarios
            );
        } catch (Exception e) {
            log.error("Erro ao notificar cancelamento de lançamento: {}", e.getMessage(), e);
        }
    }

    /* ========================= QUARTO (ADMIN+GERENTE+DEV + AUTOR) ======================= */
    public void quartoCriado(Quarto q) {
        try {
            Long autorId = userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] quartoCriado -> recipients={}, autorId={}", dest, autorId);
            quartoNotifier.criado(q, autorId, userContextService.getCurrentUserJson(), dest);
        } catch (Exception e) {
            log.error("Erro ao notificar criação de quarto: {}", e.getMessage(), e);
        }
    }

    public void quartoAtualizado(Quarto q, QuartoChangeSet changeSet) {
        try {
            final String resumo = (changeSet == null) ? "dados" : QuartoDiffFormatter.format(changeSet);
            Long autorId = userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] quartoAtualizado -> recipients={}, autorId={}", dest, autorId);
            quartoNotifier.atualizado(q, resumo, autorId, userContextService.getCurrentUserJson(), dest);
        } catch (Exception e) {
            log.error("Erro ao notificar atualização de quarto: {}", e.getMessage(), e);
        }
    }

    public void quartoEntrouManutencao(Quarto q) {
        try {
            Long autorId = userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] quartoEntrouManutencao -> recipients={}, autorId={}", dest, autorId);
            quartoNotifier.entrouManutencao(q, autorId, userContextService.getCurrentUserJson(), dest);
        } catch (Exception e) {
            log.error("Erro ao notificar entrada em manutenção: {}", e.getMessage(), e);
        }
    }

    public void quartoVoltouDisponivel(Quarto q) {
        try {
            Long autorId = userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] quartoVoltouDisponivel -> recipients={}, autorId={}", dest, autorId);
            quartoNotifier.voltouDisponivel(q, autorId, userContextService.getCurrentUserJson(), dest);
        } catch (Exception e) {
            log.error("Erro ao notificar volta de disponibilidade: {}", e.getMessage(), e);
        }
    }

    public void quartoExcluido(Quarto q) {
        try {
            Long autorId = userContextService.getCurrentUserId();
            Set<Long> dest = recipientsService.getOperationalRecipientsWithAuthor(autorId);
            log.debug("[NOTIFIER] quartoExcluido -> recipients={}, autorId={}", dest, autorId);
            quartoNotifier.excluido(q, autorId, userContextService.getCurrentUserJson(), dest);
        } catch (Exception e) {
            log.error("Erro ao notificar exclusão de quarto: {}", e.getMessage(), e);
        }
    }

    /* ========================== Helpers ===================== */
    private String buildPeriodo(Reserva r) {
        if (r == null) return "-";
        if (r.getDataEntrada() != null && r.getDataSaida() != null) {
            return r.getDataEntrada() + " até " + r.getDataSaida();
        }
        if (r.getDataEntrada() != null) {
            return r.getDataEntrada().toString();
        }
        return "-";
    }

    private String safe(String s) {
        return (s == null) ? "-" : s;
    }

    private String safeDate(LocalDate d) {
        return (d == null) ? "-" : d.toString();
    }
}