package com.foodapp.orderservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class RestaurantRejectedException extends RuntimeException {
    public RestaurantRejectedException(String message) {
        super(message);
    }
}
