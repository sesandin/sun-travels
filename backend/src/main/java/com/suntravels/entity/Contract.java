package com.suntravels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table( name = "contract" )
public class Contract
{
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "contract_id" )
    private Integer contractId;

    @Column( name = "contract_code", nullable = false, unique = true, length = 45 )
    private String contractCode;

    @Column( name = "valid_from", nullable = false )
    private LocalDate validFrom;

    @Column( name = "valid_to", nullable = false )
    private LocalDate validTo;

    @Column( name = "markup_percentage", nullable = false, precision = 5, scale = 2 )
    private BigDecimal markupPercentage;

    @JdbcTypeCode( SqlTypes.CHAR )
    @Column( name = "currency_code", nullable = false, length = 3, columnDefinition = "char(3)" )
    private String currencyCode;

    @JdbcTypeCode( SqlTypes.VARCHAR )
    @Enumerated( EnumType.STRING )
    @Column( name = "status", nullable = false, length = 20 )
    private ContractStatus status;

    @ManyToOne( fetch = FetchType.LAZY, optional = false )
    @JoinColumn( name = "hotel_id", nullable = false )
    private Hotel hotel;

    // Creates an empty object for fields to be populated after construction.
    public Contract()
    {
        // Required by persistence or JSON binding; fields are populated after construction.
    }

    // Returns the contract id.
    public Integer getContractId()
    {
        return contractId;
    }

    // Returns the contract code.
    public String getContractCode()
    {
        return contractCode;
    }

    // Updates the contract code.
    public void setContractCode( String contractCode )
    {
        this.contractCode = contractCode;
    }

    // Returns the valid from.
    public LocalDate getValidFrom()
    {
        return validFrom;
    }

    // Updates the valid from.
    public void setValidFrom( LocalDate validFrom )
    {
        this.validFrom = validFrom;
    }

    // Returns the valid to.
    public LocalDate getValidTo()
    {
        return validTo;
    }

    // Updates the valid to.
    public void setValidTo( LocalDate validTo )
    {
        this.validTo = validTo;
    }

    // Returns the markup percentage.
    public BigDecimal getMarkupPercentage()
    {
        return markupPercentage;
    }

    // Updates the markup percentage.
    public void setMarkupPercentage( BigDecimal markupPercentage )
    {
        this.markupPercentage = markupPercentage;
    }

    // Returns the currency code.
    public String getCurrencyCode()
    {
        return currencyCode;
    }

    // Updates the currency code.
    public void setCurrencyCode( String currencyCode )
    {
        this.currencyCode = currencyCode;
    }

    // Returns the status.
    public ContractStatus getStatus()
    {
        return status;
    }

    // Updates the status.
    public void setStatus( ContractStatus status )
    {
        this.status = status;
    }

    // Returns the hotel.
    public Hotel getHotel()
    {
        return hotel;
    }

    // Updates the hotel.
    public void setHotel( Hotel hotel )
    {
        this.hotel = hotel;
    }
}
