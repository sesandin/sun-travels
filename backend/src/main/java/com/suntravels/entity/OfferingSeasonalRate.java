package com.suntravels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table( name = "offering_seasonal_rate" )
public class OfferingSeasonalRate
{
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "seasonal_rate_id" )
    private Integer seasonalRateId;
    @ManyToOne( fetch = FetchType.LAZY, optional = false )
    @JoinColumn( name = "offering_id", nullable = false )
    private ContractRoomOffering offering;
    @Column( name = "valid_from", nullable = false )
    private LocalDate validFrom;
    @Column( name = "valid_to", nullable = false )
    private LocalDate validTo;
    @Column( name = "rate_per_person_per_night", nullable = false, precision = 10, scale = 2 )
    private BigDecimal ratePerPersonPerNight;

    // Returns the seasonal rate id.
    public Integer getSeasonalRateId()
    {
        return seasonalRateId;
    }

    // Returns the offering.
    public ContractRoomOffering getOffering()
    {
        return offering;
    }

    // Updates the offering.
    public void setOffering( ContractRoomOffering value )
    {
        offering = value;
    }

    // Returns the valid from.
    public LocalDate getValidFrom()
    {
        return validFrom;
    }

    // Updates the valid from.
    public void setValidFrom( LocalDate value )
    {
        validFrom = value;
    }

    // Returns the valid to.
    public LocalDate getValidTo()
    {
        return validTo;
    }

    // Updates the valid to.
    public void setValidTo( LocalDate value )
    {
        validTo = value;
    }

    // Returns the rate per person per night.
    public BigDecimal getRatePerPersonPerNight()
    {
        return ratePerPersonPerNight;
    }

    // Updates the rate per person per night.
    public void setRatePerPersonPerNight( BigDecimal value )
    {
        ratePerPersonPerNight = value;
    }
}
