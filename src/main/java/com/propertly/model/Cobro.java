package com.propertly.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class Cobro {
    public String id;
    public String propertyId;
    public LocalDate mes;
    public BigDecimal montoBase;
    public BigDecimal montoTotal;
    public boolean pagado;
    public boolean vencido;
    public LocalDate fechaPago;
    public String notes;
    public LocalDateTime createdAt;
    public List<CobroExtra> extras;

    public Cobro() {}
}
