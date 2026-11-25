CREATE TABLE clinic_live_owner_access (
    username TEXT NOT NULL,
    owner_id INT NOT NULL REFERENCES owners (id) ON DELETE CASCADE,
    PRIMARY KEY (username, owner_id)
);
INSERT INTO clinic_live_owner_access (username, owner_id) VALUES ('reference', 1);

CREATE FUNCTION notify_petclinic_visit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- Only a hint. Each node reads current, authorized rows from PostgreSQL on demand.
    PERFORM pg_notify('woge_petclinic_visits', '');
    RETURN NEW;
END;
$$;

-- The upstream unidirectional JPA collection can insert a visit before associating its pet.
CREATE TRIGGER notify_petclinic_visit
    AFTER INSERT OR UPDATE OF pet_id ON visits
    FOR EACH ROW WHEN (NEW.pet_id IS NOT NULL)
    EXECUTE FUNCTION notify_petclinic_visit();

-- The companion form schedules one follow-up slot per owner.
CREATE TABLE clinic_follow_up (
    owner_id INT PRIMARY KEY REFERENCES owners (id) ON DELETE CASCADE,
    follow_up_on DATE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0)
);
INSERT INTO clinic_follow_up (owner_id) VALUES (1);

CREATE TABLE clinic_follow_up_receipt (
    owner_id INT NOT NULL REFERENCES clinic_follow_up (owner_id) ON DELETE CASCADE,
    username TEXT NOT NULL,
    request_id UUID NOT NULL,
    expected_revision BIGINT NOT NULL,
    follow_up_on DATE NOT NULL,
    PRIMARY KEY (owner_id, username, request_id)
);
