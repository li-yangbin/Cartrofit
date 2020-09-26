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
public @interface Convert {
    CommandType[] concernType() default {};
    String[] category() default {};
    boolean all() default false;
}
