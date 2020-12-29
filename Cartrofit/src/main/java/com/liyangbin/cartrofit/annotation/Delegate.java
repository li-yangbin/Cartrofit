package com.liyangbin.cartrofit.annotation;

import com.liyangbin.cartrofit.CarType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface Delegate {
    int value();
    int _return() default 0;// used in CarCallback
    StickyType sticky() default StickyType.NO_SET;
    int restoreId() default 0;
    CarType type() default CarType.NO_SET;
}
