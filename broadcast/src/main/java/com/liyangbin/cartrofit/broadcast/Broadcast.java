package com.liyangbin.cartrofit.broadcast;

import com.liyangbin.cartrofit.annotation.Context;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@Context
public @interface Broadcast {
    boolean isLocal() default false;
}
