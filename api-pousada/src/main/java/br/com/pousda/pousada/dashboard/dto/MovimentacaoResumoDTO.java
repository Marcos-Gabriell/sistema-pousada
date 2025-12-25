package br.com.pousda.pousada.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MovimentacaoResumoDTO {

    private Long id;
    private LocalDateTime dataHora;
    private String origem;   // HOSPEDAGEM, RESERVA, MANUAL...
    private String tipo;     // ENTRADA ou SAIDA
    private Double valor;
    private String descricao;
}
