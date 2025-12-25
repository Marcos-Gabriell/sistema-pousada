package br.com.pousda.pousada.quartos.dtos;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class QuartoDTO {
    private String numero;
    private boolean ocupado;
}
