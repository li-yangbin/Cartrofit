package com.liyangbin.cartrofit.carproperty;

import com.liyangbin.cartrofit.annotation.MethodCategory;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(METHOD)
@Retention(RUNTIME)
@MethodCategory(MethodCategory.CATEGORY_GET)
public @interface Sensor {
    int type();
}
