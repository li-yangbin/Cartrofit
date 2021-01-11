package com.liyangbin.cartrofit.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(METHOD)
@Retention(RUNTIME)
@MethodCategory(MethodCategory.CATEGORY_SET | MethodCategory.CATEGORY_GET | MethodCategory.CATEGORY_TRACK)
public @interface Combine {
    int[] elements();
}
