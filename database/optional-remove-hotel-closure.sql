-- Optional manual migration. Never run automatically.
-- Application code and required tests no longer depend on this table.
-- Before execution, back up its data and inspect the target database for
-- dependent views, triggers, routines, events, and foreign keys; these live
-- database objects could not be verified from this repository.
DROP TABLE IF EXISTS hotel_closure;
