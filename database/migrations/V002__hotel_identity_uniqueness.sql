-- Manual MySQL 8 migration. Apply BEFORE deploying the duplicate-check query.
-- Preserves all hotel rows and text. NULL and blank location parts share an empty key.
-- DDL commits implicitly: back up first and schedule metadata locks/index construction.
-- If preflight reports conflicts, resolve them separately; this script never edits records.
-- A failed CALL can leave the helper procedure. Inspect partial state and drop that procedure before retrying.
DELIMITER //
CREATE PROCEDURE suntravels_hotel_identity_v002()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the intended database explicitly';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='hotel' AND character_set_name='utf8mb3'
        AND ((column_name='name' AND character_maximum_length=150)
          OR (column_name IN ('city','country') AND character_maximum_length=100)
          OR (column_name='address' AND character_maximum_length=255)))<>4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected hotel schema: review identity key sizes and charset';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='hotel' AND column_name IN ('identity_name','identity_country','identity_city','identity_address')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Identity migration already or partially applied: inspect before retrying';
    END IF;
    IF EXISTS(SELECT 1 FROM hotel GROUP BY
        lower(regexp_replace(coalesce(name,''),'^[[:space:]]+|[[:space:]]+$','')) COLLATE utf8mb3_bin,
        lower(regexp_replace(coalesce(country,''),'^[[:space:]]+|[[:space:]]+$','')) COLLATE utf8mb3_bin,
        lower(regexp_replace(coalesce(city,''),'^[[:space:]]+|[[:space:]]+$','')) COLLATE utf8mb3_bin,
        lower(regexp_replace(coalesce(address,''),'^[[:space:]]+|[[:space:]]+$','')) COLLATE utf8mb3_bin
        HAVING COUNT(*)>1) THEN
        SELECT GROUP_CONCAT(hotel_id ORDER BY hotel_id) AS conflicting_hotel_ids FROM hotel GROUP BY
            lower(regexp_replace(coalesce(name,''),'^[[:space:]]+|[[:space:]]+$','')) COLLATE utf8mb3_bin,
            lower(regexp_replace(coalesce(country,''),'^[[:space:]]+|[[:space:]]+$','')) COLLATE utf8mb3_bin,
            lower(regexp_replace(coalesce(city,''),'^[[:space:]]+|[[:space:]]+$','')) COLLATE utf8mb3_bin,
            lower(regexp_replace(coalesce(address,''),'^[[:space:]]+|[[:space:]]+$','')) COLLATE utf8mb3_bin
            HAVING COUNT(*)>1;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting hotels exist; no records changed';
    END IF;
    -- Binary collation after LOWER preserves accents; only case/outer whitespace are ignored.
    -- Composite key is 1815 bytes in utf8mb3, within InnoDB's 3072-byte limit.
    ALTER TABLE hotel
        ADD COLUMN identity_name VARCHAR(150) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin
            GENERATED ALWAYS AS (lower(regexp_replace(coalesce(name,''),'^[[:space:]]+|[[:space:]]+$',''))) VIRTUAL,
        ADD COLUMN identity_country VARCHAR(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin
            GENERATED ALWAYS AS (lower(regexp_replace(coalesce(country,''),'^[[:space:]]+|[[:space:]]+$',''))) VIRTUAL,
        ADD COLUMN identity_city VARCHAR(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin
            GENERATED ALWAYS AS (lower(regexp_replace(coalesce(city,''),'^[[:space:]]+|[[:space:]]+$',''))) VIRTUAL,
        ADD COLUMN identity_address VARCHAR(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin
            GENERATED ALWAYS AS (lower(regexp_replace(coalesce(address,''),'^[[:space:]]+|[[:space:]]+$',''))) VIRTUAL,
        ALGORITHM=INPLACE, LOCK=NONE;
    -- A concurrent conflicting insert makes this fail rather than leaving an unprotected unique identity.
    -- On failure inspect partial columns, roll back, and drop the helper procedure before retrying.
    ALTER TABLE hotel
        ADD UNIQUE INDEX uq_hotel_identity(identity_name,identity_country,identity_city,identity_address),
        ALGORITHM=INPLACE, LOCK=NONE;
END//
DELIMITER ;
CALL suntravels_hotel_identity_v002();
DROP PROCEDURE suntravels_hotel_identity_v002;
