package br.com.pousda.pousada.hospedagens.dtos;

import br.com.pousda.pousada.hospedagens.domain.enuns.TipoHospedagem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospedagemDTO {

    private String nome;
    private String cpf;

    private String email;

    private Integer numeroDiarias;
    private Double valorDiaria;
    private String formaPagamento;
    private String observacoes;
    private String numeroQuarto;
    private TipoHospedagem tipo;
}
