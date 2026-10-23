package com.suntravels.validation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Local ISO catalogue. Names remain stored in the existing country column for compatibility. */
public final class CountryCatalog
{
    private static final Map<String,String> NAMES = load();

    private CountryCatalog() {}

    private static Map<String,String> load()
    {
        Map<String,String> names = new HashMap<>();
        try( var reader = new BufferedReader( new InputStreamReader( Objects.requireNonNull(
                CountryCatalog.class.getResourceAsStream( "/location/countries.tsv" ) ), StandardCharsets.UTF_8 ) ) )
        {
            reader.lines().forEach( line -> {
                String[] fields = line.split( "\t", -1 );
                for( String alias : fields )
                    if( !alias.isBlank() ) names.put( key( alias ), fields[1] );
            } );
        }
        catch( Exception ex ) { throw new IllegalStateException( "Cannot load country catalogue", ex ); }
        return Map.copyOf( names );
    }

    private static String key( String value ) { return value.trim().toUpperCase( Locale.ROOT ); }

    public static boolean contains( String value ) { return value != null && NAMES.containsKey( key( value ) ); }

    public static String canonicalName( String value )
    {
        if( !contains( value ) ) throw new IllegalArgumentException( "Select a valid ISO country" );
        return NAMES.get( key( value ) );
    }

    /** Filters may refer to historical stored names; registration still requires an ISO country. */
    public static String filterName( String value )
    {
        if( value == null ) throw new IllegalArgumentException( "Country filter must not be null" );
        return contains( value ) ? canonicalName( value ) : value.trim();
    }
}
