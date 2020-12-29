package com.liyangbin.cartrofit.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
public @interface Scope {
    int GLOBAL_AREA_ID = 0;
    int DEFAULT_AREA_ID = 0xFFFFFFFF;

    String value();

    int area() default DEFAULT_AREA_ID;
}
