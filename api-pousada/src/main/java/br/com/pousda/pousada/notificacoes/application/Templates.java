package br.com.pousda.pousada.notificacoes.application;

import org.springframework.stereotype.Component;

@Component
public class Templates {

    /* * MÉTODOS DE RESERVA (criada, atualizada, etc.)
     * FORAM REMOVIDOS
     *
     * A lógica agora está dividida entre:
     * 1. ReservaNotifierImpl (que cria o body neutro)
     * 2. ReservaNotificationFormatter (que aplica o "por [Autor]")
     */


    // ===== RESERVAS AUTOMÁTICAS / JOBS (Estes ainda são usados) =====

    public String reservaResumoVespera(int qtd, String listaQuartos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Lembrete – amanhã temos ").append(qtd).append(qtd == 1 ? " reserva." : " reservas.");
        if (listaQuartos != null && !listaQuartos.isBlank()) {
            sb.append(" Quartos: ").append(listaQuartos);
        }
        return sb.toString();
    }

    public String reservaHojePendente(int qtd, String listaQuartos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Check-ins pendentes hoje: ").append(qtd);
        if (listaQuartos != null && !listaQuartos.isBlank()) {
            sb.append(" – Quartos: ").append(listaQuartos);
        }
        return sb.toString();
    }

    public String reservaUltimaChamada(String codigo, String hospede, String quarto) {
        StringBuilder sb = new StringBuilder();
        sb.append("Última chamada para confirmar a reserva ").append(codigo);
        if (hospede != null && !hospede.isBlank()) {
            sb.append(" (").append(hospede).append(")");
        }
        if (quarto != null && !quarto.isBlank()) {
            sb.append(" – Quarto ").append(quarto);
        }
        return sb.toString();
    }

    public String reservaUltimaChamadaLote(int qtd) {
        if (qtd <= 1) {
            return "Última chamada para confirmar a reserva pendente.";
        }
        return "Última chamada para confirmar " + qtd + " reservas pendentes.";
    }

    public String reservaNaoConfirmadaCancelada(String codigo, String hospede) {
        String base = "Reserva " + codigo + " cancelada por falta de confirmação";
        if (hospede != null && !hospede.isBlank()) {
            return base + " (" + hospede + ")";
        }
        return base;
    }

    public String reservaNaoConfirmadaCanceladaLote(int qtd) {
        if (qtd <= 1) {
            return "Reserva cancelada por falta de confirmação.";
        }
        return qtd + " reservas foram canceladas por falta de confirmação.";
    }

    /* ===== HOSPEDAGEM ===== */
    public String hospedagemCriada(String hospede, String quarto, String checkin, String checkout) {
        return String.format("Hospedagem criada para %s — Quarto %s (%s → %s)", hospede, quarto, checkin, checkout);
    }
    public String hospedagemAtualizada(String hospede, String campo, String de, String para) {
        return String.format("Hospedagem de %s atualizada: %s (%s → %s)", hospede, campo, de, para);
    }
    public String hospedagemCheckout11h(String quarto, String hospede) {
        return String.format("Quarto %s — %s. Lembrete de checkout às 11h.", quarto, hospede);
    }
    public String hospedagemAlertaReservaAmanha(String hospede, String quarto) {
        return String.format("Amanhã: %s — Quarto %s", hospede, quarto);
    }

    public String hospedagemCheckoutConcluidoAuto(String hospede, String quarto, String data) {
        return "Checkout automático concluído para " + (hospede == null ? "-" : hospede)
                + " — Quarto " + (quarto == null ? "-" : quarto)
                + " (" + (data == null ? "-" : data) + ")";
    }


    /* ===== QUARTO ===== */
    public String quartoEntrouManutencao(String numero, String motivo) {
        return String.format("Quarto %s entrou em manutenção. Motivo: %s", numero, motivo);
    }
    public String quartoSeteDiasManutencao(String numero) {
        return String.format("Quarto %s está em manutenção há 7 dias.", numero);
    }
    public String quartoVoltouDisponivel(String numero) {
        return String.format("Quarto %s voltou a ficar disponível.", numero);
    }
    public String quartoCriado(String numero, String tipo) {
        return String.format("Quarto %s criado (%s).", numero, tipo);
    }
    public String quartoAtualizado(String numero, String campo, String de, String para) {
        return String.format("Quarto %s atualizado: %s (%s → %s)", numero, campo, de, para);
    }
    public String quartoExcluido(String numero) {
        return String.format("Quarto %s foi excluído.", numero);
    }

    /* ===== FINANCEIRO (SAÍDAS) ===== */
    public String saidaCriada(String categoria, String descricao, String valor) {
        return String.format("Saída %s: %s — R$ %s", categoria, descricao, valor);
    }
    public String saidaAtualizada(Long id, String campo, String de, String para) {
        return String.format("Saída %d atualizada: %s (%s → %s)", id, campo, de, para);
    }
    public String saidaExcluida(Long id, String descricao) {
        return String.format("Saída %d (%s) foi excluída.", id, descricao);
    }

    /* ===== USUÁRIO ===== */
    public String usuarioCriado(Long id, String nome, String email) {
        long safeId = (id == null ? 0L : id);
        String n = (nome == null || nome.isBlank()) ? "-" : nome.trim();
        String e = (email == null || email.isBlank()) ? "-" : email.trim();
        return String.format("Alvo: %s (id=%d).\nE-mail: %s", n, safeId, e);
    }

    public String usuarioSenhaAtualizadaNoPrimeiroLogin(Long usuarioId, String nome) {
        return "Senha do usuário " + nome + " | " + usuarioId + " foi atualizada no primeiro login";
    }

    public String usuarioAtualizado(String mensagemRica) {
        return mensagemRica;
    }

    public String usuarioStatusAlterado(Long alvoId, String alvoNome, String novoStatus) {
        return String.format("Alvo: %s (id=%d)\nStatus: %s.", alvoNome, alvoId, novoStatus);
    }

    public String usuarioSenhaResetada(Long alvoId, String alvoNome) {
        return String.format("Alvo: %s (id=%d)\nSenha foi resetada.", alvoNome, alvoId);
    }

    public String usuarioAutoExcluido(String nome) {
        return String.format("Usuário %s solicitou autoexclusão e foi removido.", nome);
    }

    public String usuarioExcluido(Long alvoId, String alvoNome) {
        return String.format("Alvo: %s (id=%d)\nUsuário foi excluído.", alvoNome, alvoId);
    }

    public String usuarioAtualizouProprioPerfil(String nome, String campo, String de, String para) {
        return String.format("Mudanças: %s: %s → %s", campo, de, para);
    }

    public String usuarioSenhaAtualizadaPeloProprio(String nome) {
        return String.format("%s atualizou a própria senha.", nome);
    }

    public String usuarioSeusDadosAtualizados(String nome, String mudancas) {
        return String.format("Olá, %s. Seus dados de perfil foram atualizados por um administrador.\nMudanças: %s",
                nome, mudancas);
    }

    public String metaResumoSemanal(int totalSemanal) {
        return String.format("Total semanal: %d. Confira o desempenho.", totalSemanal);
    }

    public String metaResumoMensal(int totalMensal) {
        return String.format("Total mensal: %d. Veja os resultados.", totalMensal);
    }
}