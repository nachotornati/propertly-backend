package com.propertly.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AjusteRecord {
    public LocalDate fecha;
    public BigDecimal precioAntes;
    public BigDecimal precioAhora;
    public BigDecimal coeficiente;

    public AjusteRecord() {}

    public AjusteRecord(LocalDate fecha, BigDecimal precioAntes, BigDecimal precioAhora, BigDecimal coeficiente) {
        this.fecha = fecha;
        this.precioAntes = precioAntes;
        this.precioAhora = precioAhora;
        this.coeficiente = coeficiente;
    }
}
