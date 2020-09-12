package com.liyangbin.carretrofit;

public @interface CarValue {
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
