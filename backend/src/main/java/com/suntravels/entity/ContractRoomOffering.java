package com.suntravels.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contract_room_offering")
public class ContractRoomOffering {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offering_id")
    private Integer offeringId;

    public Integer getOfferingId() {
        return offeringId;
    }

    @Column(name = "rate_per_person_per_night", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerPersonPerNight;

    public BigDecimal getRatePerPersonPerNight() {
        return ratePerPersonPerNight;
    }

    public void setRatePerPersonPerNight(BigDecimal value) {
        ratePerPersonPerNight = value;
    }

    @Column(name = "contracted_room_count", nullable = false)
    private Integer contractedRoomCount;

    public Integer getContractedRoomCount() {
        return contractedRoomCount;
    }

    public void setContractedRoomCount(Integer value) {
        contractedRoomCount = value;
    }

    @Column(name = "max_adults", nullable = false)
    private Integer maxAdults;

    public Integer getMaxAdults() {
        return maxAdults;
    }

    public void setMaxAdults(Integer value) {
        maxAdults = value;
    }

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OfferingStatus status;

    public OfferingStatus getStatus() {
        return status;
    }

    public void setStatus(OfferingStatus value) {
        status = value;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract value) {
        contract = value;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType value) {
        roomType = value;
    }
}
