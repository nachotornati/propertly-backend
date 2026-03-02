package com.propertly.model;

import java.math.BigDecimal;

public class AjusteInfo {
    public BigDecimal coeficiente;
    public BigDecimal nuevoPrecio;
    public double valorDesde;
    public String fechaDesde;
    public double valorHasta;
    public String fechaHasta;
    public boolean estimado;
    public String disclaimer;

    public AjusteInfo() {}

    public AjusteInfo(BigDecimal coeficiente, BigDecimal nuevoPrecio,
                      double valorDesde, String fechaDesde,
                      double valorHasta, String fechaHasta,
                      boolean estimado, String disclaimer) {
        this.coeficiente = coeficiente;
        this.nuevoPrecio = nuevoPrecio;
        this.valorDesde = valorDesde;
        this.fechaDesde = fechaDesde;
        this.valorHasta = valorHasta;
        this.fechaHasta = fechaHasta;
        this.estimado = estimado;
        this.disclaimer = disclaimer;
    }
}
