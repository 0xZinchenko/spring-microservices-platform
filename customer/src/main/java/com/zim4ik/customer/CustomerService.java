package com.zim4ik.customer;

import com.zim4ik.clients.fraud.FraudCheckResponse;
import com.zim4ik.clients.fraud.FraudClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final FraudClient fraudClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Customer registerCustomer(CustomerRegistrationRequest request) {
        Customer customer = Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .build();

        customerRepository.saveAndFlush(customer);
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
}
