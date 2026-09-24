package com.zim4ik.customer;

public record CustomerRegisteredEvent(
        Integer customerId,
        String email,
        String firstName
) {
}
