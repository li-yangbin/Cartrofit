package com.liyangbin.carretrofit.annotation;

import com.liyangbin.carretrofit.Command;

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
    int value() default 0;
    Command.CommandType[] concernType() default {};
    String[] category() default {Custom.ALL};
    int priority() default 0;
}
