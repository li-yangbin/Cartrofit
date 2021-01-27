package com.liyangbin.cartrofit;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public interface Parameter {
    boolean isAnnotationPresent(Class<? extends Annotation> clazz);
    <A extends Annotation> A getAnnotation(Class<A> clazz);
    Annotation[] getAnnotations();
    boolean hasNoAnnotation();
    Class<?> getType();
    Type getGenericType();
    int getDeclaredIndex();
    Key getDeclaredKey();

    @RequiresApi(Build.VERSION_CODES.O)
    String getName();
}
