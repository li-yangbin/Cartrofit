package com.liyangbin.cartrofit.carproperty;

import com.liyangbin.cartrofit.annotation.ApiCategory;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@ApiCategory
@Target(TYPE)
@Retention(RUNTIME)
@Inherited
public @interface Scope {
    int GLOBAL_AREA_ID = 0;
    int DEFAULT_AREA_ID = 0xFFFFFFFF;

    String value();

    int area() default DEFAULT_AREA_ID;
}
