package com.zim4ik.customer.exception;

import com.zim4ik.customer.metrics.RegistrationMetrics;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class CustomerExceptionHandler {

    private final RegistrationMetrics registrationMetrics;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        registrationMetrics.record(RegistrationMetrics.INVALID);
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

    @ExceptionHandler(CustomerAlreadyExistsException.class)
    public ProblemDetail handleAlreadyExists(CustomerAlreadyExistsException e) {
        registrationMetrics.record(RegistrationMetrics.DUPLICATE);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(CustomerFraudException.class)
    public ProblemDetail handleFraud(CustomerFraudException e) {
        registrationMetrics.record(RegistrationMetrics.FRAUD);
        log.warn(e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Registration rejected by the fraud check");
    }

    @ExceptionHandler(NoFallbackAvailableException.class)
    public ProblemDetail handleFraudUnavailable(NoFallbackAvailableException e) {
        registrationMetrics.record(RegistrationMetrics.FRAUD_UNAVAILABLE);
        log.error("Fraud service call failed", e.getCause());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Fraud check is temporarily unavailable, try again later");
    }
}
