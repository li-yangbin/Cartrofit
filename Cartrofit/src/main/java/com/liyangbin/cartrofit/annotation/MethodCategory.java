package com.liyangbin.cartrofit.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface MethodCategory {
    @MethodCategoryDef int value();
    Class<?> inputType() default Object.class;
    Class<?> outputType() default Object.class;

    int CATEGORY_SET = 1;
    int CATEGORY_GET = 1 << 1;
    int CATEGORY_TRACK = 1 << 2;

    int CATEGORY_DEFAULT = 0xf0000000 | CATEGORY_SET | CATEGORY_GET | CATEGORY_TRACK;
}
