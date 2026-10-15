package com.suntravels.service;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.QuotationSearchRepository.BookedRoom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
/** Sweep clipped reservation-item intervals. Each item is exactly ONE room. */
@Component
public class StayAvailability {
 public int available(int offeringId,int contracted,List<BookedRoom> bookings,LocalDate in,LocalDate out) {
  int nights=Math.toIntExact(ChronoUnit.DAYS.between(in,out)); int[] events=new int[nights+1]; Set<Integer> seen=new HashSet<>();
  if(contracted<=0) throw QuotationSearchException.inconsistent();
  for(BookedRoom b:bookings) {
   if(b.offeringId()!=offeringId || !seen.add(b.itemId()) || b.adults()<=0 || b.checkIn()==null || b.checkOut()==null || !b.checkOut().isAfter(b.checkIn())) throw QuotationSearchException.inconsistent();
   LocalDate start=b.checkIn().isAfter(in)?b.checkIn():in, end=b.checkOut().isBefore(out)?b.checkOut():out;
   if(start.isBefore(end)) { events[(int)ChronoUnit.DAYS.between(in,start)]++; events[(int)ChronoUnit.DAYS.between(in,end)]--; }
  }
  int occupied=0,peak=0;
  for(int i=0;i<nights;i++) { occupied+=events[i]; peak=Math.max(peak,occupied); }
  if(peak>contracted) throw QuotationSearchException.inconsistent();
  return contracted-peak;
 }
}
