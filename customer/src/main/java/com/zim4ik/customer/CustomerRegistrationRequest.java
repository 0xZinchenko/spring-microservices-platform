package com.zim4ik.customer;

public record CustomerRegistrationRequest(
        String firstName,
        String lastName,
        String email
) {
}
