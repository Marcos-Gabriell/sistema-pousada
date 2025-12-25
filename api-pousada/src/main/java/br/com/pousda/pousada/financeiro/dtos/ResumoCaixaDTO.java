package br.com.pousda.pousada.financeiro.dtos;
import lombok.Data;

@Data
public class ResumoCaixaDTO {
    private Double totalEmCaixa;
    private Double partePrefeitura;
    private Double demaisClientes;
}
