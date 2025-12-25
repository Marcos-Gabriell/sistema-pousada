package br.com.pousda.pousada.financeiro.dtos;

import br.com.pousda.pousada.financeiro.domain.enuns.OrigemLancamento;
import br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class LancamentoResponseDTO {
    Long id;
    String codigo;

    TipoLancamento tipo;
    OrigemLancamento origem;
    Long referenciaId;

    LocalDate data;
    LocalDateTime dataHora;

    Double valor;
    String formaPagamento;
    String descricao;

    Long   criadoPorId;
    String criadoPorNome; // "NOME | ID"

    // auditoria de edição
    Integer edicoes;
    LocalDateTime editadoEm;
    Long editadoPorId;
    String editadoPorNome;

    // cancelamento
    boolean cancelado;
    LocalDateTime canceladoEm;
    Long   canceladoPorId;
    String canceladoPorNome;
    String canceladoPorPerfil; // apenas para notificação
    String canceladoMotivo;
}
