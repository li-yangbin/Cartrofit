package com.liyangbin.cartrofit.annotation;

import com.liyangbin.cartrofit.StickyType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(METHOD)
@Retention(RUNTIME)
public @interface Combine {
    int[] elements();
    StickyType sticky() default StickyType.ON;
    int restoreSet() default 0;
}
