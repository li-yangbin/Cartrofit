package com.liyangbin.carretrofit.annotation;

import com.liyangbin.carretrofit.StickyType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface Combine {
    int[] elements();
    int combinator();
    StickyType sticky() default StickyType.ON;
    int restoreSet() default 0;
}
