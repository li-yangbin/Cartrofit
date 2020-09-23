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
public @interface CarApi {
    int GLOBAL_AREA_ID = 0;
    int DEFAULT_AREA_ID = 0xFFFFFFFF;
    String DUMMY_SCOPE = "dummy_scope_id";

    String scope() default DUMMY_SCOPE;

    int area() default DEFAULT_AREA_ID;

    Class<?>[] dependency() default {};

    StickyType defaultSticky() default StickyType.NO_SET;
}
