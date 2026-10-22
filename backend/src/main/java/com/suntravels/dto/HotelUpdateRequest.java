package com.suntravels.dto;
import com.suntravels.validation.CountryValue;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class HotelUpdateRequest
{

    @NotBlank
    @Size( max = 150, message = "Name must be at most 150 characters" )
    private String name;

    @NotBlank
    @Size( max = 255, message = "Address must be at most 255 characters" )
    private String address;

    @NotBlank
    @Size( max = 100, message = "City must be at most 100 characters" )
    private String city;

    @NotBlank
    @Size( max = 100, message = "Country must be at most 100 characters" )
    @CountryValue
    private String country;

    @Size( max = 20, message = "Phone must be at most 20 characters" )
    private String phone;

    @Email
    @Size( max = 100, message = "Email must be at most 100 characters" )
    private String email;

    @NotNull
    @Min( 1 )
    @Max( 5 )
    private Byte starRating;

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
}
