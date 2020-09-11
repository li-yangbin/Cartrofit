package com.android.carretrofit;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface Set {
    int key();

    int area() default CarApi.DEFAULT_AREA_ID;

    Value value() default @Value(string = CarRetrofit.EMPTY_VALUE);
}