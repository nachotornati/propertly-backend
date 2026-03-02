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
