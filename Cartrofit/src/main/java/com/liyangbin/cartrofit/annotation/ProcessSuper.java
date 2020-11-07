package com.liyangbin.cartrofit.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target(TYPE)
@Retention(SOURCE)
public @interface ProcessSuper {
    Class<?>[] implementClass();

    String className() default "";
    Class<?> superClass() default Object.class;
    Class<?>[] superConstructor() default {};
}
