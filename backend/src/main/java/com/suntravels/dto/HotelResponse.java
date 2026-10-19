package com.suntravels.dto;

import com.suntravels.entity.HotelStatus;

public class HotelResponse {

    // Creates an empty object for fields to be populated after construction.
    public HotelResponse() {
        // Required by persistence or JSON binding; fields are populated after construction.
    }

    private Integer hotelId;

    private String name;

    private String address;

    private String city;

    private String country;

    private String phone;

    private String email;

    private Byte starRating;

    private HotelStatus status;

    public Integer getHotelId() {
        return hotelId;
    }

    public void setHotelId(Integer hotelId) {
        this.hotelId = hotelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Byte getStarRating() {
        return starRating;
    }

    public void setStarRating(Byte starRating) {
        this.starRating = starRating;
    }

    public HotelStatus getStatus() {
        return status;
    }

    public void setStatus(HotelStatus status) {
        this.status = status;
    }
}