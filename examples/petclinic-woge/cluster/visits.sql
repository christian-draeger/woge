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
