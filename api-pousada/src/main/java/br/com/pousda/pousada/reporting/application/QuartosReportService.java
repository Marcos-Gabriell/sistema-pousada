package br.com.pousda.pousada.reporting.application;

import br.com.pousda.pousada.reporting.application.validation.ReportValidator;
import br.com.pousda.pousada.reporting.domain.contracts.quartos.QuartoLinhaDTO;
import br.com.pousda.pousada.reporting.domain.contracts.quartos.QuartosFilter;
import br.com.pousda.pousada.reporting.domain.contracts.quartos.QuartosResumoDTO;
import br.com.pousda.pousada.reporting.domain.contracts.quartos.QuartosReportDTO;
import br.com.pousda.pousada.reporting.infrastructure.readrepo.quartos.QuartosReadDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static br.com.pousda.pousada.reporting.application.Timezones.BAHIA;

@Service
@RequiredArgsConstructor
public class QuartosReportService {

    private final QuartosReadDao repo;

    public QuartosReportDTO gerar(QuartosFilter f, String geradoPor) {
        ReportValidator.validar(f);

        var rows = repo.listarTodos();
        List<QuartoLinhaDTO> linhas = new ArrayList<>();

        for (var r : rows) {
            var linha = new QuartoLinhaDTO();
            linha.setNumero((String) r[0]);
            linha.setTipo((String) r[1]);
            linha.setValorDiaria(r[2] != null ? ((Number) r[2]).doubleValue() : 0.0);
            linha.setStatus((String) r[3]);

            boolean passaStatus =
                    f.getStatus() == null ||
                            f.getStatus().equalsIgnoreCase("TODOS") ||
                            f.getStatus().equalsIgnoreCase(linha.getStatus());

            boolean passaTipo =
                    f.getTipo() == null ||
                            f.getTipo().equalsIgnoreCase("TODOS") ||
                            f.getTipo().equalsIgnoreCase(linha.getTipo());

            if (!passaStatus || !passaTipo) continue;

            linhas.add(linha);
        }

        // Resumo
        Double ocupacao = repo.ocupacaoMedia(f.getDataInicio(), f.getDataFim());
        String maisOcupado = repo.quartoMaisOcupado(f.getDataInicio(), f.getDataFim());

        var resumo = new QuartosResumoDTO();
        resumo.setTotalQuartos(linhas.size());
        resumo.setOcupacaoMedia(ocupacao != null ? ocupacao : 0.0);
        resumo.setMaisOcupado(maisOcupado != null ? maisOcupado : "-");

        var dto = new QuartosReportDTO();
        dto.setDataInicio(f.getDataInicio());
        dto.setDataFim(f.getDataFim());
        dto.setGeradoEm(LocalDateTime.now(BAHIA));
        dto.setGeradoPor(geradoPor);
        dto.setResumo(resumo);
        dto.setLinhas(linhas);

        return dto;
    }
}
