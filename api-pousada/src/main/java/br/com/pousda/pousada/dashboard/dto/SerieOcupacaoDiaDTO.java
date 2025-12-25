package br.com.pousda.pousada.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SerieOcupacaoDiaDTO {
    private LocalDate data;
    private long qtdHospedagens;
    private long qtdReservas;
}
