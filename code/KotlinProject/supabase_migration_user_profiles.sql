-- Run this in your Supabase SQL Editor.
--
-- IMPORTANT: Your users table must have at least: id, username, password_hash, full_name, created_at
-- For new sign-ups we only insert username, password_hash, full_name.
-- If your existing users table has extra required (NOT NULL) columns like age, sex, occupation, etc.,
-- either make them nullable or add default values. Example:
--   ALTER TABLE users ALTER COLUMN age DROP NOT NULL;

-- Create user_profiles table (separate from auth)
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    -- Step 1: Personal info
    date_of_birth DATE,
    gender TEXT,
    race_ethnicity TEXT,
    -- Step 2: Lifestyle & diet
    physical_activity TEXT,
    smoking TEXT,
    drinking TEXT,
    calories_per_day INT,
    protein_g DOUBLE PRECISION,
    carbs_g DOUBLE PRECISION,
    fat_g DOUBLE PRECISION,
    caffeine_g DOUBLE PRECISION,
    fiber_g DOUBLE PRECISION,
    hypertension TEXT,
    diabetes TEXT,
    hyperlipidemia TEXT,
    -- Step 3: Weekly symptom log
    pain_level INT,
    stiffness_level INT,
    fatigue_level INT,
    physical_difficulty_level INT,
    -- Step 4: Physical activity (last 7 days)
    vigorous_days INT,
    vigorous_hours DOUBLE PRECISION,
    moderate_days INT,
    moderate_hours DOUBLE PRECISION,
    walking_days INT,
    walking_hours DOUBLE PRECISION,
    sitting_hours_per_weekday DOUBLE PRECISION,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Enable RLS if needed (optional)
-- ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
