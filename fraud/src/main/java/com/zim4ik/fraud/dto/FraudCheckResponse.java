package com.zim4ik.fraud.dto;

import com.zim4ik.fraud.model.FraudReason;

public record FraudCheckResponse(Boolean isFraudster, FraudReason reason) {
}
