package com.propertly.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Property {
    public String id;
    public String agencyId;
    public String address;
    public String provincia;
    public String barrio;
    public String moneda; // "ARS" or "USD"
    public BigDecimal precio;
    public LocalDate mesInicio;
    public int ajusteMeses;
    public String indiceAjuste; // "ICL" or "IPC" - only for ARS
    public String tenantName;
    public String notes;
    public LocalDateTime createdAt;

    // Calculated fields
    public LocalDate nextAdjustmentDate;
    public int daysUntilAdjustment;
    public boolean adjustmentDue;

    public Property() {}
}
