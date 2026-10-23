-- Apply manually before starting the updated backend. Existing rows remain NULL.
ALTER TABLE contract_room_offering
    ADD COLUMN room_description VARCHAR(500) NULL;
