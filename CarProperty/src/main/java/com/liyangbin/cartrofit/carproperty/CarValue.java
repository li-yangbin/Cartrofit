package com.liyangbin.cartrofit.carproperty;

public @interface CarValue {
    String EMPTY_VALUE = "car_retrofit_empty_value";

    int Int() default Integer.MAX_VALUE;
    int[] IntArray() default {};

    boolean Boolean() default false;
    boolean[] BooleanArray() default {};

    long Long() default Long.MAX_VALUE;
    long[] LongArray() default {};

    byte Byte() default Byte.MAX_VALUE;
    byte[] ByteArray() default {};

    float Float() default Float.MAX_VALUE;
    float[] FloatArray() default {};

    String string() default "";
    String[] stringArray() default {};
}
