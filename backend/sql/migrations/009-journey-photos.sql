CREATE TABLE journey_photo (
    item_id bigint PRIMARY KEY REFERENCES journey_place(id) ON DELETE CASCADE,
    image bytea NOT NULL CHECK (octet_length(image) <= 8388608)
);
