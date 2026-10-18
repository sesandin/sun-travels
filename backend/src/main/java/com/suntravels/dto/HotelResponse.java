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

    // Returns the hotel id.
    public Integer getHotelId() {
        return hotelId;
    }

    // Updates the hotel id.
    public void setHotelId(Integer hotelId) {
        this.hotelId = hotelId;
    }

    // Returns the name.
    public String getName() {
        return name;
    }

    // Updates the name.
    public void setName(String name) {
        this.name = name;
    }

    // Returns the address.
    public String getAddress() {
        return address;
    }

    // Updates the address.
    public void setAddress(String address) {
        this.address = address;
    }

    // Returns the city.
    public String getCity() {
        return city;
    }

    // Updates the city.
    public void setCity(String city) {
        this.city = city;
    }

    // Returns the country.
    public String getCountry() {
        return country;
    }

    // Updates the country.
    public void setCountry(String country) {
        this.country = country;
    }

    // Returns the phone.
    public String getPhone() {
        return phone;
    }

    // Updates the phone.
    public void setPhone(String phone) {
        this.phone = phone;
    }

    // Returns the email.
    public String getEmail() {
        return email;
    }

    // Updates the email.
    public void setEmail(String email) {
        this.email = email;
    }

    // Returns the star rating.
    public Byte getStarRating() {
        return starRating;
    }

    // Updates the star rating.
    public void setStarRating(Byte starRating) {
        this.starRating = starRating;
    }

    // Returns the status.
    public HotelStatus getStatus() {
        return status;
    }

    // Updates the status.
    public void setStatus(HotelStatus status) {
        this.status = status;
    }
}