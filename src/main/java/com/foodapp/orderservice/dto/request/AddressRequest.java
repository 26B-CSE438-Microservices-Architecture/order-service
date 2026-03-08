package com.foodapp.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
        @NotBlank String street,
        String district,
        @NotBlank String city,
        String postalCode,
        Double lat,
        Double lng
) {}
