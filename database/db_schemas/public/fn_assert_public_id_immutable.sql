create function fn_assert_public_id_immutable() returns trigger
    language plpgsql
as
$$
BEGIN
  IF NEW.public_id IS DISTINCT FROM OLD.public_id THEN
    RAISE EXCEPTION 'public_id is immutable' USING ERRCODE = 'P0001';
  END IF;
  RETURN NEW;
END;
$$;

alter function fn_assert_public_id_immutable() owner to postgres;

