package com.liyangbin.cartrofit;

public enum CarType {
    VALUE, // CarPropertyValue.getValue()
    AVAILABILITY, // CarPropertyValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE
    ALL, // CarPropertyValue
    CONFIG, // CarPropertyConfig
    NONE
}
