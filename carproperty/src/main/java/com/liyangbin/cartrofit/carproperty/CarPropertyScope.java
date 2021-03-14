package com.liyangbin.cartrofit.carproperty;

import com.liyangbin.cartrofit.annotation.Context;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@Context
public @interface CarPropertyScope {
    int DEFAULT_AREA_ID = 0;

    String value();
}
