package br.com.pousda.pousada.reporting.domain.contracts.reservas;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ReservaLinhaDTO {
    private String hospede;
    private String quarto;          // pode estar vazio se não atribuído
    private String codigo;
    private LocalDate criadaEm;
    private LocalDate checkinPrevisto;
    private String status;          // PENDENTE | CONFIRMADA | CANCELADA
}
