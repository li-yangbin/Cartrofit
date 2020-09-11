package com.android.carretrofit;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Documented
@Target(TYPE)
@Retention(SOURCE)
public @interface ProcessSuper {
    Class<?>[] implementClass();

    String className() default "";
    Class<?> superClass() default Object.class;
    Class<?>[] superConstructor() default {};
}
