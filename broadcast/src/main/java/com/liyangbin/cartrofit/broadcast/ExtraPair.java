package com.liyangbin.cartrofit.broadcast;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(PARAMETER)
@Retention(RUNTIME)
public @interface ExtraPair {
    int defNumber() default 0;
    boolean defBool() default false;
}
