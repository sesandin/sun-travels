package com.suntravels.service;

import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.QuotationSearchRepository.BookedRoom;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sweep clipped reservation-item intervals. Each item is exactly ONE room.
 */
@Component
public class StayAvailability
{
    /**
     * Returns the minimum free room count across the entire exclusive-checkout stay.
     * Each reservation item consumes one room irrespective of its adult count. Clipping
     * item intervals and sweeping their boundaries avoids work proportional to all
     * historical booked nights.
     *
     * @throws QuotationSearchException if persisted bookings or inventory are inconsistent
     */
    public int available(
            int offeringId, int contracted, List<BookedRoom> bookings, LocalDate in,
            LocalDate out )
    {
        int nights = Math.toIntExact( ChronoUnit.DAYS.between( in, out ) );
        int[] events = new int[nights + 1];
        Set<Integer> seen = new HashSet<>();
        if( contracted <= 0 )
        {
            throw QuotationSearchException.inconsistent();
        }
        for( BookedRoom booking : bookings )
        {
            if( booking.offeringId() != offeringId || !seen.add( booking.itemId() ) || booking.adults() <= 0 ||
                        booking.checkIn() == null || booking.checkOut() == null ||
                        !booking.checkOut().isAfter( booking.checkIn() ) )
            {
                throw QuotationSearchException.inconsistent();
            }
            LocalDate start = booking.checkIn().isAfter( in ) ? booking.checkIn() : in;
            LocalDate end = booking.checkOut().isBefore( out ) ? booking.checkOut() : out;
            if( start.isBefore( end ) )
            {
                events[( int ) ChronoUnit.DAYS.between( in, start )]++;
                events[( int ) ChronoUnit.DAYS.between( in, end )]--;
            }
        }
        int occupied = 0;
        int peak = 0;
        for( int i = 0; i < nights; i++ )
        {
            occupied += events[i];
            peak = Math.max( peak, occupied );
        }
        if( peak > contracted )
        {
            throw QuotationSearchException.inconsistent();
        }
        return contracted - peak;
    }
}
