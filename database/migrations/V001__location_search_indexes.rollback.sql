-- Roll application SQL back FIRST. Run only after inspecting which V001 steps succeeded.
-- For a partially applied migration, omit DROP INDEX clauses for indexes not present.
ALTER TABLE hotel
    DROP INDEX ix_hotel_status_name,
    DROP INDEX ix_hotel_status_location,
    ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE hotel
    DROP COLUMN search_city,
    DROP COLUMN search_country,
    ALGORITHM=INPLACE, LOCK=NONE;
