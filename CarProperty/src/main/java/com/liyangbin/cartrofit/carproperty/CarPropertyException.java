package com.liyangbin.cartrofit.carproperty;

public class CarPropertyException extends RuntimeException {
    public int propertyId;
    public int area;

    CarPropertyException(int propertyId, int area) {
        super("property error " + DefaultCarContext.prop2Str(propertyId, area));
        this.propertyId = propertyId;
        this.area = area;
    }
}
