package com.liyangbin.cartrofit.annotation;

import com.liyangbin.cartrofit.CarType;
import com.liyangbin.cartrofit.StickyType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface Track {
    int id();

    int area() default Scope.DEFAULT_AREA_ID;

    int restoreSet() default 0;

    CarType type() default CarType.VALUE;

    StickyType sticky() default StickyType.NO_SET;
}
