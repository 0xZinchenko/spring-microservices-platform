package com.zim4ik.clients.fraud;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "fraud")
public interface FraudClient {

    @PostMapping(path = "api/v1/fraud-check")
    FraudCheckResponse check(@RequestBody FraudCheckRequest request);
}
