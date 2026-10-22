-- MANUAL migration for the inspected MySQL 8.0.46 schema. Not applied to development.
-- Deploy this BEFORE application SQL referencing search_city/search_country.
-- No UPDATE/DELETE, uniqueness constraint, or country-name conversion is performed.
-- DDL commits implicitly. Back up first; inspect the preflight output and metadata-lock load.
-- If interrupted after the first ALTER, use the reviewed rollback before retrying.
DELIMITER //
CREATE PROCEDURE suntravels_location_search_v001()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the intended database explicitly';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='hotel' AND column_name IN ('name','city','country','status')
        AND collation_name='utf8mb3_general_ci') <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected hotel collation: review semantics before migrating';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='hotel' AND column_name IN ('city','country')
        AND data_type='varchar' AND character_maximum_length=100) <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected location widths: review generated column definitions';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='hotel' AND column_name IN ('search_city','search_country'))
        OR EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='hotel' AND index_name IN ('ix_hotel_status_name','ix_hotel_status_location')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Migration already or partially applied; inspect before retrying';
    END IF;
    -- Inspect padded/missing data without changing it. Duplicate destinations are expected;
    -- these non-unique indexes preserve them and impose no new data constraints.
    SELECT COUNT(*) AS hotels,
        SUM(city IS NOT NULL AND BINARY city<>BINARY TRIM(city)) AS padded_cities,
        SUM(country IS NOT NULL AND BINARY country<>BINARY TRIM(country)) AS padded_countries,
        SUM(country IS NULL OR TRIM(country)='') AS missing_countries FROM hotel;
    ALTER TABLE hotel
        ADD COLUMN search_city VARCHAR(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci
            GENERATED ALWAYS AS (TRIM(city)) VIRTUAL,
        ADD COLUMN search_country VARCHAR(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci
            GENERATED ALWAYS AS (NULLIF(TRIM(country),'')) VIRTUAL,
        ALGORITHM=INPLACE, LOCK=NONE;
    ALTER TABLE hotel
        ADD INDEX ix_hotel_status_name(status,name,hotel_id),
        ADD INDEX ix_hotel_status_location(status,search_city,search_country),
        ALGORITHM=INPLACE, LOCK=NONE;
END//
DELIMITER ;
CALL suntravels_location_search_v001();
DROP PROCEDURE suntravels_location_search_v001;
ANALYZE TABLE hotel;
