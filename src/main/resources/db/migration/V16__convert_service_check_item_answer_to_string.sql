-- Add the new string-based answer column
ALTER TABLE service_check_item ADD COLUMN answer VARCHAR(20);

-- Backfill from the old boolean column.
-- Anything with a NULL answer_yn on a YES_NO_NA item is treated as "Not Applicable",
-- since your app previously used NULL to represent N/A for that answer type.
UPDATE service_check_item
SET answer = CASE
                 WHEN answer_yn IS TRUE THEN 'Yes'
                 WHEN answer_yn IS FALSE THEN 'No'
                 WHEN answer_yn IS NULL AND answer_type = 'YES_NO_NA' THEN 'Not Applicable'
                 ELSE NULL
    END;

-- Drop the old boolean column now that data has been migrated
ALTER TABLE service_check_item DROP COLUMN answer_yn;