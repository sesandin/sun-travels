-- Manual V003 upgrade after V001/V002. MySQL 8.0.13+ / InnoDB only.
-- Deploy the expression-based repository queries BEFORE running this script.
-- Stop application writers and take a backup: this ALTER may rebuild/lock the table.
-- No hotel records are updated or deleted. One atomic ALTER replaces the identity index and removes four identity columns.
-- The two search columns and their indexes are retained. Functional indexes use internal hidden columns.
-- If CALL fails, inspect the error/schema and drop the helper procedure before retrying.
DELIMITER //
CREATE PROCEDURE suntravels_hotel_expressions_v003()
BEGIN
    IF DATABASE() IS NULL OR VERSION() NOT LIKE '8.0.%' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the intended MySQL 8.0 database explicitly';
    END IF;
    IF (SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='hotel') <> 'InnoDB' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected InnoDB hotel table';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hotel'
        AND collation_name='utf8mb3_general_ci' AND data_type='varchar'
        AND ((column_name='name' AND character_maximum_length=150)
          OR (column_name IN ('city','country') AND character_maximum_length=100)
          OR (column_name='address' AND character_maximum_length=255))) <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected hotel text types or collation; review before migrating';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hotel'
        AND column_name IN ('search_city','search_country','identity_name','identity_country','identity_city','identity_address')
        AND extra LIKE '%VIRTUAL GENERATED%') <> 6 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected all six V001/V002 virtual columns; already/partially migrated?';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='hotel'
        AND index_name='uq_hotel_identity' AND non_unique=0 AND expression IS NULL
        AND ((seq_in_index=1 AND column_name='identity_name') OR (seq_in_index=2 AND column_name='identity_country')
          OR (seq_in_index=3 AND column_name='identity_city') OR (seq_in_index=4 AND column_name='identity_address'))) <> 4
      OR (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='hotel'
        AND index_name='ix_hotel_status_location' AND expression IS NULL
        AND ((seq_in_index=1 AND column_name='status') OR (seq_in_index=2 AND column_name='search_city')
          OR (seq_in_index=3 AND column_name='search_country'))) <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected V001/V002 indexes; inspect before migrating';
    END IF;
    IF EXISTS(SELECT 1 FROM hotel GROUP BY
        cast(lower(regexp_replace(coalesce(name,''),'^[[:space:]]+|[[:space:]]+$','')) as char(150) character set utf8mb3) collate utf8mb3_bin,
        cast(lower(regexp_replace(coalesce(country,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin,
        cast(lower(regexp_replace(coalesce(city,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin,
        cast(lower(regexp_replace(coalesce(address,''),'^[[:space:]]+|[[:space:]]+$','')) as char(255) character set utf8mb3) collate utf8mb3_bin HAVING COUNT(*)>1) THEN
        SELECT GROUP_CONCAT(hotel_id ORDER BY hotel_id) AS conflicting_hotel_ids FROM hotel GROUP BY
            cast(lower(regexp_replace(coalesce(name,''),'^[[:space:]]+|[[:space:]]+$','')) as char(150) character set utf8mb3) collate utf8mb3_bin,
            cast(lower(regexp_replace(coalesce(country,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin,
            cast(lower(regexp_replace(coalesce(city,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin,
            cast(lower(regexp_replace(coalesce(address,''),'^[[:space:]]+|[[:space:]]+$','')) as char(255) character set utf8mb3) collate utf8mb3_bin HAVING COUNT(*)>1;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting hotel identities; no hotel records changed';
    END IF;
    ALTER TABLE hotel
        DROP INDEX uq_hotel_identity,
        ADD UNIQUE INDEX uq_hotel_identity (
            (cast(lower(regexp_replace(coalesce(name,''),'^[[:space:]]+|[[:space:]]+$','')) as char(150) character set utf8mb3) collate utf8mb3_bin),
            (cast(lower(regexp_replace(coalesce(country,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin),
            (cast(lower(regexp_replace(coalesce(city,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin),
            (cast(lower(regexp_replace(coalesce(address,''),'^[[:space:]]+|[[:space:]]+$','')) as char(255) character set utf8mb3) collate utf8mb3_bin)),
        DROP COLUMN identity_name, DROP COLUMN identity_country,
        DROP COLUMN identity_city, DROP COLUMN identity_address;
END//
DELIMITER ;
CALL suntravels_hotel_expressions_v003();
DROP PROCEDURE suntravels_hotel_expressions_v003;
ANALYZE TABLE hotel;
