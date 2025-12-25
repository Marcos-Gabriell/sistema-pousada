package br.com.pousda.pousada.usuarios.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AcessoDTO {
    private String ip;
    private LocalDateTime dataHora;
}
