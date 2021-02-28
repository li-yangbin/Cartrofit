package com.liyangbin.cartrofit.carproperty;

public class CarPropertyException extends Exception {
    public int propertyId;
    public int area;

    public CarPropertyException(int propertyId, int area) {
        super("property error " + CarPropertyContext.prop2Str(propertyId, area));
        this.propertyId = propertyId;
        this.area = area;
    }
}
