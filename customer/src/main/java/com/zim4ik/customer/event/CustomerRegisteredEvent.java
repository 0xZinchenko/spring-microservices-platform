package com.zim4ik.customer.event;

public record CustomerRegisteredEvent(
        Integer customerId,
        String email,
        String firstName
) {
}
