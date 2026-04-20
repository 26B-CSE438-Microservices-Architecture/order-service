-- Fix order_items to match OrderItem entity (Money embedded + specialInstructions)

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS unit_price_amount    NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS unit_price_currency  VARCHAR(10),
    ADD COLUMN IF NOT EXISTS total_price_amount   NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS total_price_currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS special_instructions TEXT;

UPDATE order_items
SET unit_price_amount   = unit_price,
    unit_price_currency = currency
WHERE unit_price_amount IS NULL;

ALTER TABLE order_items
    DROP COLUMN IF EXISTS unit_price,
    DROP COLUMN IF EXISTS currency;

ALTER TABLE order_items
    ALTER COLUMN unit_price_amount   SET NOT NULL,
    ALTER COLUMN unit_price_currency SET NOT NULL;
