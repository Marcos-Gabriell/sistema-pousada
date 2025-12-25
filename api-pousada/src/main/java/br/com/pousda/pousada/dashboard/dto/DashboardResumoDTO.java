package br.com.pousda.pousada.dashboard.dto;

import br.com.pousda.pousada.dashboard.domain.DashboardPeriodo;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DashboardResumoDTO {

    private DashboardPeriodo periodo;
    private LocalDate periodoInicio;
    private LocalDate periodoFim;

    // KPIs (FILTRADOS POR PERÍODO)
    private long hospedagensAtivas;
    private long quartosAtivos;
    private long reservasPendentes;
    private long usuariosAtivos;

    // Financeiro (FILTRADO POR PERÍODO)
    private Double saldoAtual;            // saldo do período (entradas - saídas)
    private Double saldoPrefeitura;       // saldo do período da prefeitura
    private Double saldoDemaisClientes;   // saldo do período demais clientes

    private Double recebimentosPrefeitura;
    private Double recebimentosCorporativos;
    private Double recebimentosComuns;

    private Double saidasTotal; // ✅ para o card de saídas (filtrado)

    // Últimas movimentações (SEMPRE últimos 90 dias)
    private List<MovimentacaoResumoDTO> ultimasMovimentacoes;

    // Séries (FILTRADAS POR PERÍODO)
    private List<SerieFinanceiraDiaDTO> serieFinanceiro;
    private List<SerieOcupacaoDiaDTO> serieOcupacao;

    // Ações do dia (mantém - mas se quiser filtrar depois, dá)
    private long checkinsPendentesHoje;
    private long checkoutsPendentesHoje;

    // Taxa ocupação (FILTRADA POR PERÍODO - média do período)
    private Double taxaOcupacaoPercentual;
}
