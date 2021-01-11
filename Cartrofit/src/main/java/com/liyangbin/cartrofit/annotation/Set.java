package com.liyangbin.cartrofit.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({METHOD, FIELD})
@Retention(RUNTIME)
@MethodCategory(MethodCategory.CATEGORY_SET)
public @interface Set {
    int id();

    int area() default Scope.DEFAULT_AREA_ID;

    CarValue value() default @CarValue(string = CarValue.EMPTY_VALUE);
}