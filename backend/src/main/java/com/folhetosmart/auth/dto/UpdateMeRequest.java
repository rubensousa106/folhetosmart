package com.folhetosmart.auth.dto;

import jakarta.validation.constraints.Size;

/** PUT /api/v1/users/me — atualiza a localização (folheto regional Aldi). */
public record UpdateMeRequest(
        @Size(max = 50, message = "Distrito demasiado longo")
        String district,

        @Size(max = 100, message = "Cidade demasiado longa")
        String city
) {
}
