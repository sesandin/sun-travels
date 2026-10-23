package com.suntravels.service;

import com.suntravels.validation.CountryCatalog;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class CountryCatalogTest
{
    @Test
    void includesEveryIsoCountryAndAcceptsNamesAndCodes()
    {
        for( String code : Locale.getISOCountries() ) assertTrue( CountryCatalog.contains( code ), code );
        assertEquals( "Sri Lanka", CountryCatalog.canonicalName( " lk " ) );
        assertEquals( "Sri Lanka", CountryCatalog.canonicalName( "Sri Lanka" ) );
        assertEquals( "United States", CountryCatalog.canonicalName( "United States of America" ) );
        assertFalse( CountryCatalog.contains( "ZZ" ) );
        assertFalse( CountryCatalog.contains( null ) );
        assertEquals( "Historical country", CountryCatalog.filterName( " Historical country " ) );
        assertEquals( "Sri Lanka", CountryCatalog.filterName( "LK" ) );
        assertThrows( IllegalArgumentException.class, () -> CountryCatalog.filterName( null ) );
    }
}
