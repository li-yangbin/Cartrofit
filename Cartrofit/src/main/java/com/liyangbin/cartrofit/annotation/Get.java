package com.liyangbin.cartrofit.annotation;

import com.liyangbin.cartrofit.CarType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface Get {
    int id();

    CarType type() default CarType.VALUE;

    int area() default Scope.DEFAULT_AREA_ID;
}
