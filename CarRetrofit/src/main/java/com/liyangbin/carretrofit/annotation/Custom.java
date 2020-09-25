package com.liyangbin.carretrofit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface Custom {
    String ALL = "ALL";

    int interceptBy() default 0;
    int convertBy() default 0;
    String[] category() default {ALL};
}
