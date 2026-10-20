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

@Entity
@Table( name = "contract_room_offering" )
public class ContractRoomOffering
{
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "offering_id" )
    private Integer offeringId;
    @Column( name = "rate_per_person_per_night", nullable = false, precision = 10, scale = 2 )
    private BigDecimal ratePerPersonPerNight;
    @Column( name = "contracted_room_count", nullable = false )
    private Integer contractedRoomCount;
    @Column( name = "max_adults", nullable = false )
    private Integer maxAdults;
    @JdbcTypeCode( SqlTypes.VARCHAR )
    @Enumerated( EnumType.STRING )
    @Column( name = "status", nullable = false, length = 20 )
    private OfferingStatus status;
    @ManyToOne( fetch = FetchType.LAZY, optional = false )
    @JoinColumn( name = "contract_id", nullable = false )
    private Contract contract;
    @ManyToOne( fetch = FetchType.LAZY, optional = false )
    @JoinColumn( name = "room_type_id", nullable = false )
    private RoomType roomType;

    // Returns the offering id.
    public Integer getOfferingId()
    {
        return offeringId;
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

    // Returns the contracted room count.
    public Integer getContractedRoomCount()
    {
        return contractedRoomCount;
    }

    // Updates the contracted room count.
    public void setContractedRoomCount( Integer value )
    {
        contractedRoomCount = value;
    }

    // Returns the max adults.
    public Integer getMaxAdults()
    {
        return maxAdults;
    }

    // Updates the max adults.
    public void setMaxAdults( Integer value )
    {
        maxAdults = value;
    }

    // Returns the status.
    public OfferingStatus getStatus()
    {
        return status;
    }

    // Updates the status.
    public void setStatus( OfferingStatus value )
    {
        status = value;
    }

    // Returns the contract.
    public Contract getContract()
    {
        return contract;
    }

    // Updates the contract.
    public void setContract( Contract value )
    {
        contract = value;
    }

    // Returns the room type.
    public RoomType getRoomType()
    {
        return roomType;
    }

    // Updates the room type.
    public void setRoomType( RoomType value )
    {
        roomType = value;
    }
}
