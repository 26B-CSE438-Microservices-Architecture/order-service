package com.foodapp.orderservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class OrderNotBelongToUserException extends RuntimeException {
    public OrderNotBelongToUserException(String message) {
        super(message);
    }
}
