package com.liyangbin.cartrofit.annotation;

import com.liyangbin.cartrofit.StickyType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface CarApi {
    int GLOBAL_AREA_ID = 0;
    int DEFAULT_AREA_ID = 0xFFFFFFFF;

    String scope() default "";

    int area() default DEFAULT_AREA_ID;

    StickyType defaultSticky() default StickyType.NO_SET;
}
