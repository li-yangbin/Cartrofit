package com.liyangbin.carretrofit.annotation;

import com.liyangbin.carretrofit.StickyType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface Combine {
    String[] elements();
    String combinator();
    String token() default "";
    StickyType sticky() default StickyType.NO_SET;
}
