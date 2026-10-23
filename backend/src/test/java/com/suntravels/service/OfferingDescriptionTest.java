package com.suntravels.service;

import com.suntravels.dto.OfferingRequest;
import com.suntravels.entity.OfferingStatus;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class OfferingDescriptionTest
{
    @Test
    void optionalDescriptionEnforcesBoundary()
    {
        try( var factory = Validation.buildDefaultValidatorFactory() )
        {
            var validator = factory.getValidator();
            for( String description : new String[]{ null, "", " ", "a".repeat( 500 ) } )
            {
                assertTrue( validator.validate( request( description ) ).isEmpty() );
            }
            assertFalse( validator.validate( request( "a".repeat( 501 ) ) ).isEmpty() );
        }
    }

    private OfferingRequest request( String description )
    {
        return new OfferingRequest( 1, BigDecimal.TEN, 2, 2, OfferingStatus.ENABLED, description );
    }
}
