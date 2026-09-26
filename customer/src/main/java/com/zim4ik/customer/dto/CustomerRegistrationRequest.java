package com.zim4ik.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRegistrationRequest(
        @Schema(example = "Yan") @NotBlank @Size(max = 100) String firstName,
        @Schema(example = "Zinchenko") @NotBlank @Size(max = 100) String lastName,
        @Schema(example = "yan@example.com", description = "Unique, compared case-insensitively")
        @NotBlank @Email @Size(max = 255) String email
) {
}
