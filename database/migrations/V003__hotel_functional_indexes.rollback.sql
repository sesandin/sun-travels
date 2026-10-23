-- Manual V003 rollback. Restore V001/V002 columns before rolling application code back.
-- Stop writers and back up first. No hotel text is changed.
-- If CALL fails, inspect the schema and drop the helper procedure before retrying.
DELIMITER //
CREATE PROCEDURE suntravels_hotel_expressions_v003_rollback()
BEGIN
    IF DATABASE() IS NULL OR VERSION() NOT LIKE '8.0.%' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the intended MySQL 8.0 database explicitly';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hotel'
        AND column_name IN ('identity_name','identity_country','identity_city','identity_address'))
      OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='hotel'
        AND index_name='uq_hotel_identity' AND non_unique=0 AND expression IS NOT NULL) <> 4
 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected complete V003 schema; inspect before rollback';
    END IF;
    ALTER TABLE hotel
    DROP INDEX uq_hotel_identity,
    ADD COLUMN identity_name VARCHAR(150) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin
        GENERATED ALWAYS AS (lower(regexp_replace(coalesce(name,''),'^[[:space:]]+|[[:space:]]+$',''))) VIRTUAL,
    ADD COLUMN identity_country VARCHAR(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin
        GENERATED ALWAYS AS (lower(regexp_replace(coalesce(country,''),'^[[:space:]]+|[[:space:]]+$',''))) VIRTUAL,
    ADD COLUMN identity_city VARCHAR(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin
        GENERATED ALWAYS AS (lower(regexp_replace(coalesce(city,''),'^[[:space:]]+|[[:space:]]+$',''))) VIRTUAL,
    ADD COLUMN identity_address VARCHAR(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin
        GENERATED ALWAYS AS (lower(regexp_replace(coalesce(address,''),'^[[:space:]]+|[[:space:]]+$',''))) VIRTUAL,
    ADD UNIQUE INDEX uq_hotel_identity(identity_name,identity_country,identity_city,identity_address);
END//
DELIMITER ;
CALL suntravels_hotel_expressions_v003_rollback();
DROP PROCEDURE suntravels_hotel_expressions_v003_rollback;
ANALYZE TABLE hotel;
