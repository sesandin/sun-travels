package com.suntravels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hotel")
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hotel_id")
    private Integer hotelId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "star_rating")
    private Byte starRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HotelStatus status;

    // Creates an empty object for fields to be populated after construction.
    public Hotel() {
        // Required by JPA.
    }

    // Returns the hotel id.
    public Integer getHotelId() {
        return hotelId;
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
