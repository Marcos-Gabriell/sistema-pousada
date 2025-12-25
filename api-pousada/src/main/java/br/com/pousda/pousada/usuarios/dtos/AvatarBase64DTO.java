package br.com.pousda.pousada.usuarios.dtos;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AvatarBase64DTO {
    @NotBlank
    private String dataUrl; // ex: "data:image/png;base64,AAAA..."
}