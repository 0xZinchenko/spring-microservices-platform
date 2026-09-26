package com.zim4ik.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CustomerRegistrationResponse(@Schema(example = "1") Integer customerId) {
}
