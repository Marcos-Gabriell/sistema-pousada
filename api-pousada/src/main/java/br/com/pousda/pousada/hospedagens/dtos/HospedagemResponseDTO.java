package br.com.pousda.pousada.hospedagens.dtos;

import br.com.pousda.pousada.hospedagens.domain.enuns.TipoHospedagem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospedagemResponseDTO {

    private Long id;
    private TipoHospedagem tipo;
    private String nome;
    private String cpf;

    private String email;

    private LocalDate dataEntrada;
    private LocalDate dataSaida;
    private Integer numeroDiarias;
    private Double valorDiaria;
    private Double valorTotal;
    private String formaPagamento;
    private String observacoes;
    private String numeroQuarto;
    private Boolean ocupado;
    private String status;
    private String codigoHospedagem;

    // quem criou (login/email antigo)
    private String criadoPor;

    private LocalDateTime criadoEm;

    // ✅ NOVOS CAMPOS EXPOSTOS NO FRONT / PDF
    private String criadoPorNome;
    private Long criadoPorId;
}
