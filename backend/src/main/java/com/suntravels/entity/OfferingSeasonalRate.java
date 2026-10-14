package com.suntravels.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="offering_seasonal_rate")
public class OfferingSeasonalRate {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="seasonal_rate_id") private Integer seasonalRateId;
 public Integer getSeasonalRateId() { return seasonalRateId; }
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="offering_id",nullable=false)
 private ContractRoomOffering offering;
 public ContractRoomOffering getOffering() { return offering; }
 public void setOffering(ContractRoomOffering value) { offering=value; }
 @Column(name="valid_from",nullable=false)
 private LocalDate validFrom;
 public LocalDate getValidFrom() { return validFrom; }
 public void setValidFrom(LocalDate value) { validFrom=value; }
 @Column(name="valid_to",nullable=false)
 private LocalDate validTo;
 public LocalDate getValidTo() { return validTo; }
 public void setValidTo(LocalDate value) { validTo=value; }
 @Column(name="rate_per_person_per_night",nullable=false, precision=10, scale=2)
 private BigDecimal ratePerPersonPerNight;
 public BigDecimal getRatePerPersonPerNight() { return ratePerPersonPerNight; }
 public void setRatePerPersonPerNight(BigDecimal value) { ratePerPersonPerNight=value; }
}
