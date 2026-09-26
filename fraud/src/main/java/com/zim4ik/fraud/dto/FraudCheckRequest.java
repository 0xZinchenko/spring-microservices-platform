package com.zim4ik.fraud.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FraudCheckRequest(
        @Schema(example = "1") @NotNull Integer customerId,
        @Schema(example = "yan@example.com") @NotBlank @Email String email
) {
}
