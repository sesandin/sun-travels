package com.suntravels.dto;

import com.suntravels.entity.HotelStatus;
import com.suntravels.validation.CountryValue;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class HotelRequest
{

    @NotBlank( message = "Hotel name is required" )
    @Size( max = 150, message = "Name must be at most 150 characters" )
    private String name;
    @NotBlank( message = "Address is required" )
    @Size( max = 255, message = "Address must be at most 255 characters" )
    private String address;
    @NotBlank( message = "City is required" )
    @Size( max = 100, message = "City must be at most 100 characters" )
    private String city;
    @NotBlank( message = "Country is required" )
    @CountryValue
    @Size( max = 100, message = "Country must be at most 100 characters" )
    private String country;
    @Size( max = 20, message = "Phone must be at most 20 characters" )
    private String phone;
    @Email( message = "Invalid email format" )
    @Size( max = 100, message = "Email must be at most 100 characters" )
    private String email;
    @NotNull( message = "Star rating is required" )
    @Min( value = 1, message = "Star rating must be at least 1" )
    @Max( value = 5, message = "Star rating cannot exceed 5" )
    private Byte starRating;
    @NotNull( message = "Hotel status is required" )
    private HotelStatus status;

    // Creates an empty object for fields to be populated after construction.
    public HotelRequest()
    {
        // Required by persistence or JSON binding; fields are populated after construction.
    }

    // Returns the name.
    public String getName()
    {
        return name;
    }

    // Updates the name.
    public void setName( String name )
    {
        this.name = name;
    }

    // Returns the address.
    public String getAddress()
    {
        return address;
    }

    // Updates the address.
    public void setAddress( String address )
    {
        this.address = address;
    }

    // Returns the city.
    public String getCity()
    {
        return city;
    }

    // Updates the city.
    public void setCity( String city )
    {
        this.city = city;
    }

    // Returns the country.
    public String getCountry()
    {
        return country;
    }

    // Updates the country.
    public void setCountry( String country )
    {
        this.country = country;
    }

    // Returns the phone.
    public String getPhone()
    {
        return phone;
    }

    // Updates the phone.
    public void setPhone( String phone )
    {
        this.phone = phone;
    }

    // Returns the email.
    public String getEmail()
    {
        return email;
    }

    // Updates the email.
    public void setEmail( String email )
    {
        this.email = email;
    }

    // Returns the star rating.
    public Byte getStarRating()
    {
        return starRating;
    }

    // Updates the star rating.
    public void setStarRating( Byte starRating )
    {
        this.starRating = starRating;
    }

    // Returns the status.
    public HotelStatus getStatus()
    {
        return status;
    }

    // Updates the status.
    public void setStatus( HotelStatus status )
    {
        this.status = status;
    }
}
