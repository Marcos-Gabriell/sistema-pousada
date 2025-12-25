// src/main/java/br/com/pousda/pousada/reservas/dtos/ReservaResponseDTO.java
package br.com.pousda.pousada.reservas.dtos;

import br.com.pousda.pousada.reservas.domain.TipoCliente;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ReservaResponseDTO {

    private Long id;
    private String codigo;
    private String nome;
    private String telefone;
    private String cpf;
    private String email;
    private TipoCliente tipoCliente;
    private String numeroQuarto;
    private LocalDate dataEntrada;
    private LocalDate dataSaida;
    private Integer numeroDiarias;
    private String status;
    private String observacoes;
    private String observacoesCheckin;
    private LocalDate dataReserva;
    private Double valorDiaria;
    private Double valorTotal;
    private String formaPagamento;
    private String motivoCancelamento;

    // auditoria
    private Long autorId;
    private String autorNome;

    private Long confirmadorId;
    private String confirmadorNome;
    private LocalDateTime confirmadoEm;

    private Long canceladorId;
    private String canceladorNome;
    private LocalDateTime canceladoEm;
}
