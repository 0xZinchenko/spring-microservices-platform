package com.zim4ik.customer.service;

import com.zim4ik.clients.fraud.FraudCheckResponse;
import com.zim4ik.clients.fraud.FraudClient;
import com.zim4ik.customer.dto.CustomerRegistrationRequest;
import com.zim4ik.customer.entity.Customer;
import com.zim4ik.customer.event.CustomerRegisteredEvent;
import com.zim4ik.customer.exception.CustomerAlreadyExistsException;
import com.zim4ik.customer.exception.CustomerFraudException;
import com.zim4ik.customer.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final CustomerRegistrationRequest REQUEST =
            new CustomerRegistrationRequest("Yan", "Zinchenko", "yan@example.com");

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private FraudClient fraudClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void registerCustomer_savesCustomerAndPublishesEvent_whenNotFraudster() {
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(1);
            return customer;
        });
        when(fraudClient.isFraudster(1)).thenReturn(new FraudCheckResponse(false));

        Customer customer = customerService.registerCustomer(REQUEST);

        assertThat(customer.getId()).isEqualTo(1);
        assertThat(customer.getFirstName()).isEqualTo("Yan");
        assertThat(customer.getLastName()).isEqualTo("Zinchenko");
        assertThat(customer.getEmail()).isEqualTo("yan@example.com");

        ArgumentCaptor<CustomerRegisteredEvent> event = ArgumentCaptor.forClass(CustomerRegisteredEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new CustomerRegisteredEvent(1, "yan@example.com", "Yan"));
    }

    @Test
    void registerCustomer_throwsAndDoesNotPublishEvent_whenFraudster() {
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(2);
            return customer;
        });
        when(fraudClient.isFraudster(2)).thenReturn(new FraudCheckResponse(true));

        assertThatThrownBy(() -> customerService.registerCustomer(REQUEST))
                .isInstanceOf(CustomerFraudException.class)
                .hasMessageContaining("2");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void registerCustomer_doesNotPublishEvent_whenFraudServiceFails() {
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(3);
            return customer;
        });
        when(fraudClient.isFraudster(3)).thenThrow(new IllegalStateException("fraud is down"));

        assertThatThrownBy(() -> customerService.registerCustomer(REQUEST))
                .isInstanceOf(IllegalStateException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void registerCustomer_normalizesEmail() {
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(4);
            return customer;
        });
        when(fraudClient.isFraudster(4)).thenReturn(new FraudCheckResponse(false));

        Customer customer = customerService.registerCustomer(
                new CustomerRegistrationRequest("Yan", "Zinchenko", "  Yan@Example.COM "));

        assertThat(customer.getEmail()).isEqualTo("yan@example.com");
        verify(customerRepository).existsByEmail("yan@example.com");
    }

    @Test
    void registerCustomer_throwsWithoutCallingFraud_whenEmailAlreadyExists() {
        when(customerRepository.existsByEmail("yan@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.registerCustomer(REQUEST))
                .isInstanceOf(CustomerAlreadyExistsException.class)
                .hasMessageContaining("yan@example.com");

        verify(customerRepository, never()).saveAndFlush(any());
        verifyNoInteractions(fraudClient, eventPublisher);
    }

    @Test
    void registerCustomer_throwsAlreadyExists_whenUniqueConstraintIsViolated() {
        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("uq_customer_email"));

        assertThatThrownBy(() -> customerService.registerCustomer(REQUEST))
                .isInstanceOf(CustomerAlreadyExistsException.class);

        verify(fraudClient, never()).isFraudster(anyInt());
        verifyNoInteractions(eventPublisher);
    }
}
