package com.zim4ik.fraud;

import com.zim4ik.fraud.entity.BlockedEmail;
import com.zim4ik.fraud.model.FraudReason;
import com.zim4ik.fraud.repository.BlockedEmailRepository;
import com.zim4ik.fraud.repository.FraudCheckHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class FraudCheckIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FraudCheckHistoryRepository fraudCheckHistoryRepository;

    @Autowired
    private BlockedEmailRepository blockedEmailRepository;

    @BeforeEach
    void setUp() {
        fraudCheckHistoryRepository.deleteAll();
        blockedEmailRepository.deleteAll();
    }

    @Test
    void fraudCheck_passesCleanCustomerAndStoresHistory() throws Exception {
        check(42, "yan@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFraudster").value(false))
                .andExpect(jsonPath("$.reason").doesNotExist());

        assertThat(fraudCheckHistoryRepository.findAll())
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getCustomerId()).isEqualTo(42);
                    assertThat(history.getIsFraudster()).isFalse();
                    assertThat(history.getReason()).isNull();
                });
    }

    @Test
    void fraudCheck_rejectsDisposableDomainFromMigration() throws Exception {
        check(43, "someone@mailinator.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFraudster").value(true))
                .andExpect(jsonPath("$.reason").value("DISPOSABLE_EMAIL_DOMAIN"));

        assertThat(fraudCheckHistoryRepository.findAll())
                .singleElement()
                .extracting(history -> history.getReason())
                .isEqualTo(FraudReason.DISPOSABLE_EMAIL_DOMAIN);
    }

    @Test
    void fraudCheck_rejectsBlockedEmail() throws Exception {
        blockedEmailRepository.save(new BlockedEmail("bad@example.com"));

        check(44, "Bad@Example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFraudster").value(true))
                .andExpect(jsonPath("$.reason").value("BLOCKED_EMAIL"));
    }

    @Test
    void fraudCheck_returns400_whenEmailIsInvalid() throws Exception {
        check(45, "not-an-email").andExpect(status().isBadRequest());

        assertThat(fraudCheckHistoryRepository.count()).isZero();
    }

    private ResultActions check(int customerId, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/fraud-check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":" + customerId + ",\"email\":\"" + email + "\"}"));
    }
}
