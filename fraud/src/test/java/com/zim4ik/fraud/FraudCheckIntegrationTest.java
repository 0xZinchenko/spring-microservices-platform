package com.zim4ik.fraud;

import com.zim4ik.fraud.repository.FraudCheckHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @BeforeEach
    void setUp() {
        fraudCheckHistoryRepository.deleteAll();
    }

    @Test
    void fraudCheck_returnsResultAndStoresHistory() throws Exception {
        mockMvc.perform(get("/api/v1/fraud-check/{customerId}", 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFraudster").value(false));

        assertThat(fraudCheckHistoryRepository.findAll())
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getCustomerId()).isEqualTo(42);
                    assertThat(history.getIsFraudster()).isFalse();
                    assertThat(history.getCreatedAt()).isNotNull();
                });
    }
}
