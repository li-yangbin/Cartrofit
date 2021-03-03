package com.liyangbin.cartrofit.broadcast;

import android.content.Intent;

import com.liyangbin.cartrofit.annotation.MethodCategory;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(METHOD)
@Retention(RUNTIME)
@MethodCategory(MethodCategory.CATEGORY_SET)
public @interface Send {
    /**
     * {@link android.content.Intent#setAction(String)}
     */
    String action();

    /**
     * {@link android.content.Intent#setPackage(String)}
     */
    String targetPackage() default "";

    /**
     * {@link android.content.Intent#setClassName(String, String)}
     */
    String targetClass() default "";

    /**
     * {@link android.content.Context#sendOrderedBroadcast(Intent, String)}
     */
    boolean ordered() default false;

    /**
     * {@link androidx.localbroadcastmanager.content.LocalBroadcastManager#sendBroadcastSync(Intent)}
     */
    boolean synced() default false;

    String receiverPermission() default "";
}
