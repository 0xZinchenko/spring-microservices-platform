package com.zim4ik.customer.controller;


import com.zim4ik.customer.dto.CustomerRegistrationRequest;
import com.zim4ik.customer.dto.CustomerRegistrationResponse;
import com.zim4ik.customer.entity.Customer;
import com.zim4ik.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("api/v1/customers")
@Tag(name = "Customers", description = "Customer registration")
public record CustomerController(CustomerService customerService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register a customer",
            description = "Saves the customer, runs a fraud check and sends a welcome notification asynchronously.")
    @ApiResponse(responseCode = "201", description = "Customer registered")
    @ApiResponse(responseCode = "400", description = "Request validation failed",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Customer did not pass the fraud check",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Customer with this email already exists",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Fraud check is temporarily unavailable",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public CustomerRegistrationResponse registerCustomer(
            @Valid @RequestBody CustomerRegistrationRequest customerRegistrationRequest) {
        log.info("new customer registration {}", customerRegistrationRequest);
        Customer customer = customerService.registerCustomer(customerRegistrationRequest);
        return new CustomerRegistrationResponse(customer.getId());
    }
}
