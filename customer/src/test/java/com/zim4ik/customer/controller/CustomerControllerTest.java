package com.zim4ik.customer.controller;

import com.zim4ik.clients.fraud.FraudClient;
import com.zim4ik.clients.notification.NotificationClient;
import com.zim4ik.customer.dto.CustomerRegistrationRequest;
import com.zim4ik.customer.entity.Customer;
import com.zim4ik.customer.exception.CustomerAlreadyExistsException;
import com.zim4ik.customer.exception.CustomerFraudException;
import com.zim4ik.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final String URL = "/api/v1/customers";

    private static final String VALID_BODY = """
            {"firstName":"Yan","lastName":"Zinchenko","email":"yan@example.com"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private FraudClient fraudClient;

    @MockitoBean
    private NotificationClient notificationClient;

    @Test
    void register_returns201WithCustomerId() throws Exception {
        when(customerService.registerCustomer(any(CustomerRegistrationRequest.class)))
                .thenReturn(Customer.builder().id(7).build());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(7));
    }

    @Test
    void register_returns400WithFieldErrors_whenRequestIsInvalid() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"","lastName":"Zinchenko","email":"not-an-email"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.lastName").doesNotExist());

        verify(customerService, never()).registerCustomer(any());
    }

    @Test
    void register_returns403_whenCustomerIsFraudster() throws Exception {
        when(customerService.registerCustomer(any(CustomerRegistrationRequest.class)))
                .thenThrow(new CustomerFraudException(7));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void register_returns503_whenFraudServiceIsUnavailable() throws Exception {
        when(customerService.registerCustomer(any(CustomerRegistrationRequest.class)))
                .thenThrow(new NoFallbackAvailableException("No fallback available.", new RuntimeException()));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void register_returns409_whenEmailAlreadyExists() throws Exception {
        when(customerService.registerCustomer(any(CustomerRegistrationRequest.class)))
                .thenThrow(new CustomerAlreadyExistsException("yan@example.com"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
