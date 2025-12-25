package br.com.pousda.pousada.usuarios.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class AvatarViewDTO {
    private String mode;       // PHOTO | COLOR | PRESET
    private String color;      // se COLOR
    private String gender;     // se PRESET
    private String dataUrl;    // se PHOTO (JPEG 256x256)
    private Long version;    // p/ cache-busting se precisar
}