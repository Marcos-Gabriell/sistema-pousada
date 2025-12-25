package br.com.pousda.pousada.dashboard.application;

import br.com.pousda.pousada.dashboard.domain.DashboardPeriodo;
import br.com.pousda.pousada.dashboard.dto.DashboardResumoDTO;
import br.com.pousda.pousada.dashboard.dto.MovimentacaoResumoDTO;
import br.com.pousda.pousada.dashboard.dto.SerieFinanceiraDiaDTO;
import br.com.pousda.pousada.dashboard.dto.SerieOcupacaoDiaDTO;
import br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento;
import br.com.pousda.pousada.financeiro.infra.LancamentoFinanceiroRepository;
import br.com.pousda.pousada.hospedagens.infra.HospedagemRepository;
import br.com.pousda.pousada.quartos.infra.QuartoRepository;
import br.com.pousda.pousada.reservas.domain.StatusReserva;
import br.com.pousda.pousada.reservas.infra.ReservaRepository;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final HospedagemRepository hospedagens;
    private final QuartoRepository quartos;
    private final ReservaRepository reservas;
    private final UsuarioRepository usuarios;
    private final LancamentoFinanceiroRepository lancamentos;

    @Transactional(readOnly = true)
    public DashboardResumoDTO getResumo(DashboardPeriodo periodo) {

        if (periodo == null) periodo = DashboardPeriodo.ULTIMOS_7_DIAS;

        DashboardResumoDTO dto = new DashboardResumoDTO();

        LocalDate hoje = LocalDate.now(ZONE);
        LocalDate inicio = periodo.inicio(hoje);
        LocalDate fim = periodo.fim(hoje);

        dto.setPeriodo(periodo);
        dto.setPeriodoInicio(inicio);
        dto.setPeriodoFim(fim);

        // ======================
        // KPIs (FILTRADOS POR PERÍODO)
        // ======================

        long quartosAtivos = quartos.countQuartosAtivos(); // estoque/cadastro
        dto.setQuartosAtivos(quartosAtivos);

        // Hospedagens que “intersectam” o período (estavam ativas em algum momento do intervalo)
        long hospedagensNoPeriodo = hospedagens.countHospedagensAtivasNoPeriodo(inicio, fim);
        dto.setHospedagensAtivas(hospedagensNoPeriodo);

        // Reservas pendentes no período (dataEntrada no intervalo)
        long reservasPendentesPeriodo = reservas.countPendentesNoPeriodo(StatusReserva.PENDENTE, inicio, fim);
        dto.setReservasPendentes(reservasPendentesPeriodo);

        // Usuários ativos (depende do seu conceito; por enquanto mantém o atual)
        dto.setUsuariosAtivos(usuarios.countByAtivoTrue());

        // ======================
        // FINANCEIRO (FILTRADO POR PERÍODO) SEM MEXER NO REPO
        // ======================
        // Como você NÃO quer criar métodos no LancamentoFinanceiroRepository,
        // vamos calcular pelas linhas do totalPorDiaEntre(inicio, fim):
        // row[0]=LocalDate, row[1]=TipoLancamento, row[2]=Double total

        List<Object[]> rowsPeriodo = lancamentos.totalPorDiaEntre(inicio, fim);

        double entradasPeriodo = 0.0;
        double saidasPeriodo = 0.0;

        for (Object[] row : rowsPeriodo) {
            TipoLancamento tipo = (TipoLancamento) row[1];
            Double total = (Double) row[2];
            double v = total != null ? total : 0.0;

            if (tipo == TipoLancamento.ENTRADA) {
                entradasPeriodo += v;
            } else {
                saidasPeriodo += v;
            }
        }

        // Saldo do período = entradas - saídas
        double saldoPeriodo = entradasPeriodo - saidasPeriodo;

        // Mantém a estrutura do seu dashboard:
        // saldoAtual = saldo do período
        // prefeitura e demais (se quiser filtrar prefeitura depois, adiciona query no repo — aqui não mexe)
        dto.setSaldoAtual(saldoPeriodo);

        // Por enquanto (sem mexer no repo), mantém prefeitura separado como 0
        // e demais clientes = saldo total do período.
        // Se você já tiver formas de separar prefeitura no repo atual, me manda os métodos que existem e eu adapto.
        double saldoPref = 0.0;
        dto.setSaldoPrefeitura(saldoPref);
        dto.setSaldoDemaisClientes(saldoPeriodo - saldoPref);

        // Recebimentos do período:
        // (mantém os 3 campos, mas sem separar origem sem métodos no repo)
        dto.setRecebimentosPrefeitura(0.0);
        dto.setRecebimentosCorporativos(0.0);
        dto.setRecebimentosComuns(entradasPeriodo);

        // ======================
        // MOVIMENTAÇÕES (FORA DO FILTRO) -> sempre últimos 90 dias (como você quer)
        // ======================
        LocalDate movInicio = hoje.minusDays(89);
        dto.setUltimasMovimentacoes(buscarMovimentacoesPeriodo(movInicio, hoje));

        // ======================
        // SÉRIES (FILTRADAS)
        // ======================
        List<SerieFinanceiraDiaDTO> serieFin = montarSerieFinanceiro(inicio, fim);
        List<SerieOcupacaoDiaDTO> serieOcup = montarSerieOcupacao(inicio, fim);

        dto.setSerieFinanceiro(serieFin);
        dto.setSerieOcupacao(serieOcup);

        // ======================
        // AÇÕES DO DIA (mantém HOJE)
        // ======================
        dto.setCheckinsPendentesHoje(reservas.findPendentesParaHoje(hoje).size());
        dto.setCheckoutsPendentesHoje(hospedagens.countCheckoutsPendentesParaData(hoje));

        // ======================
        // TAXA OCUPAÇÃO (FILTRADA): média da ocupação no período
        // ======================
        dto.setTaxaOcupacaoPercentual(calcularTaxaMediaPeriodo(serieOcup, quartosAtivos));

        log.info("[DASHBOARD] periodo={} inicio={} fim={} mov90d=[{}..{}]",
                periodo.name(), inicio, fim, movInicio, hoje);

        return dto;
    }

    private double calcularTaxaMediaPeriodo(List<SerieOcupacaoDiaDTO> serie, long quartosAtivos) {
        if (quartosAtivos <= 0) return 0.0;
        if (serie == null || serie.isEmpty()) return 0.0;

        double somaHospedagens = serie.stream()
                .mapToLong(SerieOcupacaoDiaDTO::getQtdHospedagens)
                .sum();

        double dias = serie.size();
        double mediaHospedagens = somaHospedagens / dias;

        double taxa = (mediaHospedagens / (double) quartosAtivos) * 100.0;
        if (taxa < 0) return 0.0;
        if (taxa > 100) return 100.0;
        return taxa;
    }

    private List<MovimentacaoResumoDTO> buscarMovimentacoesPeriodo(LocalDate inicio, LocalDate fim) {
        return lancamentos
                .findByDataBetweenAndExcluidoEmIsNullOrderByDataHoraDesc(inicio, fim)
                .stream()
                .map(l -> {
                    MovimentacaoResumoDTO dto = new MovimentacaoResumoDTO();
                    dto.setId(l.getId());
                    dto.setDataHora(l.getDataHora());
                    dto.setOrigem(l.getOrigem().name());
                    dto.setTipo(l.getTipo().name());
                    dto.setValor(l.getValor());
                    dto.setDescricao(l.getDescricao());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<SerieFinanceiraDiaDTO> montarSerieFinanceiro(LocalDate inicio, LocalDate fim) {

        List<Object[]> rows = lancamentos.totalPorDiaEntre(inicio, fim);
        Map<LocalDate, SerieFinanceiraDiaDTO> map = new HashMap<>();

        for (Object[] row : rows) {
            LocalDate data = (LocalDate) row[0];
            TipoLancamento tipo = (TipoLancamento) row[1];
            Double total = (Double) row[2];

            SerieFinanceiraDiaDTO dto = map.get(data);
            if (dto == null) {
                dto = novoFinanceiro(data);
                map.put(data, dto);
            }

            double v = (total != null ? total : 0.0);
            if (tipo == TipoLancamento.ENTRADA) {
                dto.setTotalEntradas(dto.getTotalEntradas() + v);
            } else {
                dto.setTotalSaidas(dto.getTotalSaidas() + v);
            }
        }

        List<SerieFinanceiraDiaDTO> lista = new ArrayList<>();
        LocalDate dia = inicio;
        while (!dia.isAfter(fim)) {
            lista.add(map.getOrDefault(dia, novoFinanceiro(dia)));
            dia = dia.plusDays(1);
        }

        return lista;
    }

    private SerieFinanceiraDiaDTO novoFinanceiro(LocalDate dia) {
        SerieFinanceiraDiaDTO dto = new SerieFinanceiraDiaDTO();
        dto.setData(dia);
        dto.setTotalEntradas(0.0);
        dto.setTotalSaidas(0.0);
        return dto;
    }

    private List<SerieOcupacaoDiaDTO> montarSerieOcupacao(LocalDate inicio, LocalDate fim) {

        List<Object[]> rows = hospedagens.ocupacaoPorDiaEntre(inicio, fim);
        Map<LocalDate, SerieOcupacaoDiaDTO> map = new HashMap<>();

        for (Object[] row : rows) {
            LocalDate data = (LocalDate) row[0];
            Long qtdHosp = (Long) row[1];
            Long qtdRes = (Long) row[2];

            SerieOcupacaoDiaDTO dto = new SerieOcupacaoDiaDTO();
            dto.setData(data);
            dto.setQtdHospedagens(qtdHosp != null ? qtdHosp : 0L);
            dto.setQtdReservas(qtdRes != null ? qtdRes : 0L);

            map.put(data, dto);
        }

        List<SerieOcupacaoDiaDTO> lista = new ArrayList<>();
        LocalDate dia = inicio;
        while (!dia.isAfter(fim)) {
            lista.add(map.getOrDefault(dia, vazioOcupacao(dia)));
            dia = dia.plusDays(1);
        }

        return lista;
    }

    private SerieOcupacaoDiaDTO vazioOcupacao(LocalDate dia) {
        SerieOcupacaoDiaDTO dto = new SerieOcupacaoDiaDTO();
        dto.setData(dia);
        dto.setQtdHospedagens(0L);
        dto.setQtdReservas(0L);
        return dto;
    }
}
