package com.zim4ik.customer;

public class CustomerFraudException extends RuntimeException {

    public CustomerFraudException(Integer customerId) {
        super("Customer " + customerId + " did not pass the fraud check");
    }
}
