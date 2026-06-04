package com.zim4ik.customer;

import com.zim4ik.clients.fraud.FraudClient;
import com.zim4ik.clients.fraud.FraudCheckResponse;
import com.zim4ik.clients.notification.NotificationClient;
import com.zim4ik.clients.notification.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final FraudClient fraudClient;
    private final NotificationClient notificationClient;

    public void registerCustomer(CustomerRegistrationRequest request) {
        Customer customer = Customer.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .build();

        customerRepository.saveAndFlush(customer);
        log.info("Saved customer with id: {}", customer.getId());


        FraudCheckResponse fraudResponse = fraudClient.isFraudster(customer.getId());
        if (fraudResponse.isFraudster()) {
            throw new IllegalStateException("Customer is fraudulent");
        }


        notificationClient.sendNotification(
                new NotificationRequest(
                        customer.getId(),
                        customer.getEmail(),
                        "Welcome, " + customer.getFirstName() + "!"
                )
        );
        log.info("Sent notification for customer {}", customer.getId());
    }
}