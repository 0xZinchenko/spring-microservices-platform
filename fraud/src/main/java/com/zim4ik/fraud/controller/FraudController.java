package com.zim4ik.fraud.controller;


import com.zim4ik.fraud.dto.FraudCheckResponse;
import com.zim4ik.fraud.service.FraudCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @GetMapping(path = "{customerId}")
    @Operation(summary = "Check whether a customer is a fraudster")
    public FraudCheckResponse isFraudster(@PathVariable("customerId")  Integer customerID) {
        boolean isFraudulentCustomer = fraudCheckService.isFraudulentCustomer(customerID);
        log.info("fraud check request for customer {}", customerID);
        return new FraudCheckResponse(isFraudulentCustomer);
    }
}
