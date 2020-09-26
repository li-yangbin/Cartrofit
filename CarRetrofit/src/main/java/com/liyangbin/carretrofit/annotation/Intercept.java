package com.liyangbin.carretrofit.annotation;

import com.liyangbin.carretrofit.CommandType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface Intercept {
    CommandType[] concernType() default {};
    String[] category() default {};
    int priority() default 0;
    boolean all() default false;
}
