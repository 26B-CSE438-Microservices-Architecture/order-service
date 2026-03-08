package com.foodapp.orderservice.dto.response;

import com.foodapp.orderservice.domain.valueobject.Address;

public record AddressResponse(String street, String district, String city, String postalCode, Double lat, Double lng) {
    public static AddressResponse from(Address a) {
        if (a == null) return null;
        return new AddressResponse(a.getStreet(), a.getDistrict(), a.getCity(), a.getPostalCode(), a.getLat(), a.getLng());
    }
}
