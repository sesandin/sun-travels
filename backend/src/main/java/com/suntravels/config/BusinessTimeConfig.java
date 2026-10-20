package com.suntravels.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class BusinessTimeConfig
{
    // Provides the business clock using the Asia/Colombo time zone.
    @Bean
    public Clock businessClock()
    {
        return Clock.system( ZoneId.of( "Asia/Colombo" ) );
    }
}
