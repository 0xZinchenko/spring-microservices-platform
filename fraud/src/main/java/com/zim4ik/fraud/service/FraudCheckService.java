package com.zim4ik.fraud.service;

import com.zim4ik.fraud.dto.FraudCheckRequest;
import com.zim4ik.fraud.dto.FraudCheckResponse;
import com.zim4ik.fraud.entity.FraudCheckHistory;
import com.zim4ik.fraud.model.FraudReason;
import com.zim4ik.fraud.repository.BlockedEmailDomainRepository;
import com.zim4ik.fraud.repository.BlockedEmailRepository;
import com.zim4ik.fraud.repository.FraudCheckHistoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@AllArgsConstructor
public class FraudCheckService {

    private final FraudCheckHistoryRepository fraudCheckHistoryRepository;
    private final BlockedEmailRepository blockedEmailRepository;
    private final BlockedEmailDomainRepository blockedEmailDomainRepository;

    public FraudCheckResponse check(FraudCheckRequest request) {
        Optional<FraudReason> reason = findReason(request.email().trim().toLowerCase(Locale.ROOT));

        fraudCheckHistoryRepository.save(
                FraudCheckHistory.builder()
                        .customerId(request.customerId())
                        .isFraudster(reason.isPresent())
                        .reason(reason.orElse(null))
                        .createdAt(LocalDateTime.now())
                        .build()
        );
        return new FraudCheckResponse(reason.isPresent(), reason.orElse(null));
    }

    private Optional<FraudReason> findReason(String email) {
        if (blockedEmailRepository.existsById(email)) {
            return Optional.of(FraudReason.BLOCKED_EMAIL);
        }
        String domain = email.substring(email.lastIndexOf('@') + 1);
        if (blockedEmailDomainRepository.existsById(domain)) {
            return Optional.of(FraudReason.DISPOSABLE_EMAIL_DOMAIN);
        }
        return Optional.empty();
    }
}
