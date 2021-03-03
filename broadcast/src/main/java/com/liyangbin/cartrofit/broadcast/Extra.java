package com.liyangbin.cartrofit.broadcast;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface Extra {
    String key();
    int defNumber() default -1;
    boolean defBool() default false;
}
