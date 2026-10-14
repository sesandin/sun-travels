package com.suntravels.dto;

import com.suntravels.entity.HotelStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class HotelRequest {

    public HotelRequest(){

    }

    @NotBlank(message = "Hotel name is required")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "Address is required")
    @Size(max = 255)
    private String address;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "Country is required")
    @Size(max = 100)
    private String country;

    @Size(max = 20)
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 100)
    private String email;

    @NotNull(message = "Star rating is required")
    @Min(value = 1, message = "Star rating must be at least 1")
    @Max(value = 5, message = "Star rating cannot exceed 5")
    private Byte starRating;

    @NotNull(message = "Hotel status is required")
    private HotelStatus status;

    public String getName()
    {
        return name;
    }

    public void setName( String name )
    {
        this.name = name;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress( String address )
    {
        this.address = address;
    }

    public String getCity()
    {
        return city;
    }

    public void setCity( String city )
    {
        this.city = city;
    }

    public String getCountry()
    {
        return country;
    }

    public void setCountry( String country )
    {
        this.country = country;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone( String phone )
    {
        this.phone = phone;
    }

    public String getEmail()
    {
        return email;
    }

    public void setEmail( String email )
    {
        this.email = email;
    }

    public Byte getStarRating() {
        return starRating;
    }

    public void setStarRating(Byte starRating) {
        this.starRating = starRating;
    }

    public HotelStatus getStatus()
    {
        return status;
    }

    public void setStatus( HotelStatus status )
    {
        this.status = status;
    }
}
