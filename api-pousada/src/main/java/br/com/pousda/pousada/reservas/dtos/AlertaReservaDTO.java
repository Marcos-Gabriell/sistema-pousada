package br.com.pousda.pousada.reservas.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class AlertaReservaDTO {

    private Long reservaId;
    private String codigoReserva;
    private String numeroQuarto;
    private String nomeHospede;
    private String tipo;
    private String mensagem;
    private String dataReferencia;
}
