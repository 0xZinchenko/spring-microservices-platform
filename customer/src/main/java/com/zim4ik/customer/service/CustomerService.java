package com.zim4ik.customer.service;

import com.zim4ik.clients.fraud.FraudCheckResponse;
import com.zim4ik.clients.fraud.FraudClient;
import com.zim4ik.customer.dto.CustomerRegistrationRequest;
import com.zim4ik.customer.entity.Customer;
import com.zim4ik.customer.event.CustomerRegisteredEvent;
import com.zim4ik.customer.exception.CustomerAlreadyExistsException;
import com.zim4ik.customer.exception.CustomerFraudException;
import com.zim4ik.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final FraudClient fraudClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Customer registerCustomer(CustomerRegistrationRequest request) {
        String email = normalizeEmail(request.email());
        if (customerRepository.existsByEmail(email)) {
            throw new CustomerAlreadyExistsException(email);
        }

        Customer customer = Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(email)
                .build();

        try {
            customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw new CustomerAlreadyExistsException(email);
        }
        log.info("✅ Saved customer with id: {}", customer.getId());

        FraudCheckResponse fraudResponse = fraudClient.isFraudster(customer.getId());
        if (Boolean.TRUE.equals(fraudResponse.isFraudster())) {
            throw new CustomerFraudException(customer.getId());
        }

        eventPublisher.publishEvent(new CustomerRegisteredEvent(
                customer.getId(),
                customer.getEmail(),
                customer.getFirstName()
        ));

        return customer;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
