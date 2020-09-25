package com.liyangbin.carretrofit.annotation;

import com.liyangbin.carretrofit.StickyType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface Delegate {
    int target();
    StickyType sticky() default StickyType.NO_SET;
    String[] category() default {};
}
