-- Migration script to populate missing createdDate and lastModifiedDate fields
-- for existing orders in the database
-- Run this script against your ProdQ database

-- Update created_date for orders that don't have it
-- Uses the 'date' field (order date) as the fallback value
UPDATE _order
SET created_date = date
WHERE created_date IS NULL
  AND date IS NOT NULL;

-- Update last_modified_date for orders that don't have it
-- Uses the 'date' field (order date) as the fallback value
UPDATE _order
SET last_modified_date = date
WHERE last_modified_date IS NULL
  AND date IS NOT NULL;

-- Verify the changes
SELECT
    id,
    name,
    date AS order_date,
    created_date,
    last_modified_date,
    CASE
        WHEN created_date IS NULL THEN 'MISSING created_date'
        WHEN last_modified_date IS NULL THEN 'MISSING last_modified_date'
        ELSE 'OK'
    END AS status
FROM _order
ORDER BY id;
