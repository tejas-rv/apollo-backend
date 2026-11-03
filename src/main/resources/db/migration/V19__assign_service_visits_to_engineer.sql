-- Scheduled AMC visits are assigned to an engineer account by ID. engineer_name stays as a
-- display snapshot (and as the legacy match key for rows that could not be linked).
ALTER TABLE amc_service_history ADD COLUMN assigned_engineer_id BIGINT;

ALTER TABLE amc_service_history
    ADD CONSTRAINT fk_amc_service_history_engineer
        FOREIGN KEY (assigned_engineer_id)
        REFERENCES app_user (id)
        ON DELETE SET NULL;

CREATE INDEX idx_amc_service_history_assigned_engineer
    ON amc_service_history (assigned_engineer_id, status);

-- Link existing visits whose engineer name identifies exactly one engineer
-- (by username, profile full name or employee number). Ambiguous names stay unlinked.
WITH candidates AS (
    SELECT u.id AS user_id, LOWER(TRIM(u.username)) AS match_key
    FROM app_user u
    WHERE u.user_type = 'ENGINEER'
    UNION
    SELECT p.user_id, LOWER(TRIM(p.full_name))
    FROM user_profile p
    JOIN app_user u ON u.id = p.user_id
    WHERE u.user_type = 'ENGINEER' AND TRIM(p.full_name) <> ''
    UNION
    SELECT p.user_id, LOWER(TRIM(p.employee_number))
    FROM user_profile p
    JOIN app_user u ON u.id = p.user_id
    WHERE u.user_type = 'ENGINEER' AND p.employee_number IS NOT NULL AND TRIM(p.employee_number) <> ''
),
unique_keys AS (
    SELECT match_key, MIN(user_id) AS user_id
    FROM candidates
    GROUP BY match_key
    HAVING COUNT(DISTINCT user_id) = 1
)
UPDATE amc_service_history h
SET assigned_engineer_id = k.user_id
FROM unique_keys k
WHERE h.assigned_engineer_id IS NULL
  AND LOWER(TRIM(h.engineer_name)) = k.match_key;
