package com.propertly.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class Property {
    public String id;
    public String agencyId;
    public String address;
    public String provincia;
    public String barrio;
    public String moneda; // "ARS" or "USD"
    public BigDecimal precio;       // initial/base price stored in DB
    public LocalDate mesInicio;
    public int ajusteMeses;
    public Integer duracionMeses; // total contract duration in months (optional)
    public String tenantToken;   // public share token
    public String indiceAjuste; // "ICL" or "IPC" - only for ARS
    public String tenantName;
    public String tenantPhone;
    public String tenantEmail;
    public Boolean tenantFactura;
    public Boolean tenantPersonaJuridica;
    public String tenantDocumento; // CUIT if juridica, DNI if fisica
    public String unidadFuncional; // optional unit identifier, e.g. "Piso 3A", "UF 42"
    public String notes;
    public LocalDateTime createdAt;

    // Calculated fields
    public BigDecimal precioActual; // accumulated price after all past adjustments
    public LocalDate nextAdjustmentDate;
    public int daysUntilAdjustment;
    public boolean adjustmentDue;
    public AjusteInfo ajusteInfo;
    public List<AjusteRecord> historialAjustes;

    // Internal override fields — stored in DB to preserve history when settings change, not sent to frontend
    @com.fasterxml.jackson.annotation.JsonIgnore public BigDecimal precioBaseOverride;
    @com.fasterxml.jackson.annotation.JsonIgnore public LocalDate mesBaseOverride;
    @com.fasterxml.jackson.annotation.JsonIgnore public String historialSnapshotJson;

    // Input-only fields for "contract already started" creation — not stored directly
    public BigDecimal precioActualInput;
    public String proximoMesAjusteInput; // "YYYY-MM"

    public Property() {}
}
