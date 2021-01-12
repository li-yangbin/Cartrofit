package com.liyangbin.cartrofit.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface ParameterCategory {
    @ParameterCategoryDef int value();

    int ESSENTIAL = 0;
    int ATTRIBUTE = 1;
    int EXTRA = 2;
}
