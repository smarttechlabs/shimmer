package org.openmhealth.schema.pojos.base;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class LengthUnitValue {

    @JsonProperty(value = "value", required = true)
    private BigDecimal value;

    @JsonProperty(value = "unit", required = true)
    private LengthUnit unit;

    public enum LengthUnit {fm, pm, nm, micg, mm, cm, m, km, in, ft, yd, mi}

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public LengthUnit getUnit() {
        return unit;
    }

    public void setUnit(LengthUnit unit) {
        this.unit = unit;
    }
}