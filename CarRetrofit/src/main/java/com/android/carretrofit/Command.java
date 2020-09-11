package com.android.carretrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public abstract class Command {
    public abstract Object invoke(Object[] args) throws Throwable;

    public abstract CommandType type();

    public abstract boolean fromApply();

    public abstract boolean fromInject();

    public abstract Method getMethod();

    public abstract Field getField();

    public abstract String getName();

    public abstract int getKey();

    public abstract int getArea();

    public enum CommandType {
        SET,
        GET,
        TRACK,
        APPLY,
        INJECT
    }
}