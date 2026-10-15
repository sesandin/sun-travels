package com.suntravels.service;
import com.suntravels.dto.QuotationSearchResponse.NightlyPrice;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.QuotationSearchRepository.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
/** Pure pricing shared with a future LOCKED confirmation flow; never writes snapshots. */
@Component
public class NightlyQuotationPricing {
 public record Tariff(LocalDate date, BigDecimal rate, Integer seasonId) {}
 public List<Tariff> tariffs(Candidate offering,List<Season> seasons,LocalDate in,LocalDate out) {
  List<Season> sorted=new ArrayList<>(seasons); sorted.sort(Comparator.comparing(Season::from,Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingInt(Season::id));
  LocalDate previousEnd=null;
  for(Season s:sorted) {
   if(s.offeringId()!=offering.offeringId() || s.from()==null || s.to()==null || s.to().isBefore(s.from())
     || s.from().isBefore(offering.validFrom()) || s.to().isAfter(offering.validTo()) || !validRate(s.rate())
     || previousEnd!=null && !s.from().isAfter(previousEnd)) throw QuotationSearchException.inconsistent();
   previousEnd=s.to();
  }
  List<Tariff> result=new ArrayList<>(); int index=0;
  for(LocalDate date=in;date.isBefore(out);date=date.plusDays(1)) {
   while(index<sorted.size() && sorted.get(index).to().isBefore(date)) index++;
   Season s=index<sorted.size() && !sorted.get(index).from().isAfter(date)?sorted.get(index):null;
   result.add(new Tariff(date,s==null?offering.baseRate():s.rate(),s==null?null:s.id()));
  }
  return List.copyOf(result);
 }
 public boolean validRate(BigDecimal rate) { return rate!=null && rate.signum()>0 && rate.scale()<=2 && rate.compareTo(new BigDecimal("99999999.99"))<=0; }
 public BigDecimal nightly(Tariff tariff,int adults,BigDecimal markup) {
  return tariff.rate().multiply(BigDecimal.valueOf(adults)).multiply(BigDecimal.ONE.add(markup.movePointLeft(2))).setScale(2,RoundingMode.HALF_UP);
 }
 public BigDecimal total(List<Tariff> tariffs,int adults,BigDecimal markup) {
  return tariffs.stream().map(t->nightly(t,adults,markup)).reduce(new BigDecimal("0.00"),BigDecimal::add);
 }
 public List<NightlyPrice> breakdown(List<Tariff> tariffs,int adults,BigDecimal markup) {
  return tariffs.stream().map(t->new NightlyPrice(t.date(),t.seasonId()==null?"BASE":"SEASONAL",t.seasonId(),t.rate(),markup,nightly(t,adults,markup))).toList();
 }
}
