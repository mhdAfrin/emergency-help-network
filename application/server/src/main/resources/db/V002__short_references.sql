CREATE TABLE reference_sequence(singleton INTEGER PRIMARY KEY CHECK(singleton=1), next_value INTEGER NOT NULL CHECK(next_value BETWEEN 10000 AND 100000));
INSERT INTO reference_sequence VALUES(1,10000);
