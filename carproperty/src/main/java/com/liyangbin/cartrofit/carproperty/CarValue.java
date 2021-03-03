package com.liyangbin.cartrofit.carproperty;

public @interface CarValue {
    String EMPTY_VALUE = "car_retrofit_empty_value";

    int Int() default Integer.MAX_VALUE;

    boolean Boolean() default false;

    long Long() default Long.MAX_VALUE;

    byte Byte() default Byte.MAX_VALUE;
    byte[] ByteArray() default {};

    float Float() default Float.MAX_VALUE;

    String string() default "";
}
