package com.liyangbin.cartrofit.carproperty;

import com.liyangbin.cartrofit.annotation.MethodCategory;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({METHOD, FIELD})
@Retention(RUNTIME)
@MethodCategory(MethodCategory.CATEGORY_GET)
public @interface Get {
    int propId();

    int area() default PropertyScope.DEFAULT_AREA_ID;
}
