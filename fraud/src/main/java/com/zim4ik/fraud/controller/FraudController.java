package com.zim4ik.fraud.controller;


import com.zim4ik.fraud.dto.FraudCheckRequest;
import com.zim4ik.fraud.dto.FraudCheckResponse;
import com.zim4ik.fraud.service.FraudCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/fraud-check")
@Tag(name = "Fraud check", description = "Internal API called by the customer service")
@AllArgsConstructor
@Slf4j
public class FraudController {

    private final FraudCheckService fraudCheckService;

    @PostMapping
    @Operation(summary = "Check whether a customer is a fraudster",
            description = "Rules: the email is on the blocklist, or its domain is a disposable email provider.")
    public FraudCheckResponse check(@Valid @RequestBody FraudCheckRequest request) {
        FraudCheckResponse response = fraudCheckService.check(request);
        log.info("fraud check for customer {}: fraudster={}, reason={}",
                request.customerId(), response.isFraudster(), response.reason());
        return response;
    }
}
