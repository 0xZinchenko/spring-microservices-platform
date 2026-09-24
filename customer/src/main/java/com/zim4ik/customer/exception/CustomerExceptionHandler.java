package com.zim4ik.customer.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class CustomerExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> String.valueOf(fieldError.getDefaultMessage()),
                        (first, second) -> first));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(CustomerFraudException.class)
    public ProblemDetail handleFraud(CustomerFraudException e) {
        log.warn(e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(NoFallbackAvailableException.class)
    public ProblemDetail handleFraudUnavailable(NoFallbackAvailableException e) {
        log.error("Fraud service call failed", e.getCause());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Fraud check is temporarily unavailable, try again later");
    }
}
