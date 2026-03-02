CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS agencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    dias_antes_recordatorio INTEGER NOT NULL DEFAULT 30,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS properties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id UUID REFERENCES agencies(id) ON DELETE CASCADE,
    address VARCHAR(500),
    provincia VARCHAR(255),
    barrio VARCHAR(255) NOT NULL,
    moneda VARCHAR(10) NOT NULL,
    precio DECIMAL(15,2) NOT NULL,
    mes_inicio DATE NOT NULL,
    ajuste_meses INTEGER NOT NULL DEFAULT 3,
    indice_ajuste VARCHAR(10),
    tenant_name VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Migration: add provincia if not exists
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='properties' AND column_name='provincia'
  ) THEN
    ALTER TABLE properties ADD COLUMN provincia VARCHAR(255);
  END IF;
END$$;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    agency_id UUID REFERENCES agencies(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sessions (
    token VARCHAR(64) PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    agency_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

-- Migration: add tenant_phone if not exists
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='properties' AND column_name='tenant_phone'
  ) THEN
    ALTER TABLE properties ADD COLUMN tenant_phone VARCHAR(50);
  END IF;
END$$;
