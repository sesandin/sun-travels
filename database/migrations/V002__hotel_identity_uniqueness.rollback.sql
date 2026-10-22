-- Roll application duplicate-query code back first; inspect partial state before running.
-- If index creation failed after columns were added, omit the first statement.
ALTER TABLE hotel DROP INDEX uq_hotel_identity, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE hotel DROP COLUMN identity_name, DROP COLUMN identity_country,
    DROP COLUMN identity_city, DROP COLUMN identity_address,
    ALGORITHM=INPLACE, LOCK=NONE;
