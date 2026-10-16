package com.suntravels.exception;

import com.suntravels.dto.ReservationResponse.Quote;

public class ReservationException extends RuntimeException {
 private final int status;
 private final String code;
 private final Quote quotation;
 public ReservationException(int status,String code,String message) { this(status,code,message,null); }
 public ReservationException(int status,String code,String message,Quote quotation) {
  super(message);this.status=status;this.code=code;this.quotation=quotation;
 }
 public int getStatus() { return status; }
 public String getCode() { return code; }
 public Quote getQuotation() { return quotation; }
 public static ReservationException unavailable() { return new ReservationException(409,"UNAVAILABLE","Selected accommodation is no longer eligible or available. Search again."); }
}
