package com.suntravels.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Target( ElementType.FIELD )
@Retention( RetentionPolicy.RUNTIME )
@Constraint( validatedBy = CountryValue.Validator.class )
public @interface CountryValue
{
    String message() default "Select a valid ISO country";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CountryValue,String>
    {
        public boolean isValid( String value, ConstraintValidatorContext context )
        {
            // Required-field validation remains responsible for missing values.
            return value == null || value.isBlank() || CountryCatalog.contains( value );
        }
    }
}
