package com.liyangbin.cartrofit.broadcast;

import com.liyangbin.cartrofit.annotation.MethodCategory;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(METHOD)
@Retention(RUNTIME)
@MethodCategory(MethodCategory.CATEGORY_TRACK)
public @interface Receive {
    /**
     * {@link android.content.IntentFilter#addAction(String)}
     */
    String action();

    /**
     * {@link android.content.IntentFilter#setPriority(int)}
     */
    int priority() default 0;

    /**
     * {@link android.content.IntentFilter#addCategory(String)}
     */
    String[] category() default {};

    /**
     * {@link android.content.IntentFilter#addDataScheme(String)}
     */
    String[] dataScheme() default {};

    /**
     * {@link android.content.IntentFilter#addDataType(String)}
     */
    String[] dataMimeType() default {};

    String broadcastPermission() default "";
}
