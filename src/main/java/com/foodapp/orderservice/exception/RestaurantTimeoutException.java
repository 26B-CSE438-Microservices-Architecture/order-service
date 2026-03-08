package com.foodapp.orderservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class RestaurantTimeoutException extends RuntimeException {
    public RestaurantTimeoutException(String message) {
        super(message);
    }
}
