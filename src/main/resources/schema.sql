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

CREATE TABLE IF NOT EXISTS cobros (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID REFERENCES properties(id) ON DELETE CASCADE,
    mes DATE NOT NULL,
    monto_base DECIMAL(15,2) NOT NULL,
    monto_total DECIMAL(15,2) NOT NULL,
    pagado BOOLEAN NOT NULL DEFAULT false,
    fecha_pago DATE,
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(property_id, mes)
);

CREATE TABLE IF NOT EXISTS cobro_extras (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cobro_id UUID REFERENCES cobros(id) ON DELETE CASCADE,
    descripcion VARCHAR(255) NOT NULL,
    monto DECIMAL(15,2) NOT NULL
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

-- Migration: add tenant_token if not exists
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='properties' AND column_name='tenant_token'
  ) THEN
    ALTER TABLE properties ADD COLUMN tenant_token UUID DEFAULT gen_random_uuid() NOT NULL;
  END IF;
END$$;

-- Migration: add duracion_meses if not exists
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='properties' AND column_name='duracion_meses'
  ) THEN
    ALTER TABLE properties ADD COLUMN duracion_meses INTEGER;
  END IF;
END$$;

-- Migration: add adjustment override columns for preserving historical calculations
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='properties' AND column_name='precio_base_override'
  ) THEN
    ALTER TABLE properties ADD COLUMN precio_base_override DECIMAL(15,2);
    ALTER TABLE properties ADD COLUMN mes_base_override DATE;
    ALTER TABLE properties ADD COLUMN historial_snapshot JSONB;
  END IF;
END$$;

-- Migration: add tenant contact/fiscal fields
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='properties' AND column_name='tenant_email'
  ) THEN
    ALTER TABLE properties ADD COLUMN tenant_email VARCHAR(255);
    ALTER TABLE properties ADD COLUMN tenant_factura BOOLEAN DEFAULT false;
    ALTER TABLE properties ADD COLUMN tenant_persona_juridica BOOLEAN DEFAULT false;
    ALTER TABLE properties ADD COLUMN tenant_documento VARCHAR(20);
  END IF;
END$$;

-- Migration: add unidad_funcional
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='properties' AND column_name='unidad_funcional'
  ) THEN
    ALTER TABLE properties ADD COLUMN unidad_funcional VARCHAR(50);
  END IF;
END$$;
