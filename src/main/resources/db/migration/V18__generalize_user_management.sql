-- Staff user types: ADMIN and OFFICE (CEO, CXOs, office staff) sign in with the ADMIN role,
-- ENGINEER signs in with the ENGINEER role. CUSTOMER logins have no user type.
ALTER TABLE app_user ADD COLUMN user_type VARCHAR(20);

UPDATE app_user SET user_type = role WHERE role IN ('ADMIN', 'ENGINEER');

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_user_type
        CHECK (user_type IS NULL OR user_type IN ('ADMIN', 'OFFICE', 'ENGINEER'));

-- The engineer profile becomes the common staff profile for every user type.
ALTER TABLE engineer_profile RENAME TO user_profile;
ALTER TABLE user_profile RENAME CONSTRAINT fk_engineer_profile_user TO fk_user_profile_user;
ALTER INDEX uk_engineer_profile_employee_number RENAME TO uk_user_profile_employee_number;
