package com.android.carretrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Command {

    void setKey(int key);

    int getKey();

    void setArea(int area);

    int getArea();

    void setSource(DataSource source);

    DataSource getSource();

    Object invoke(Object[] args) throws Throwable;

    CommandType type();

    boolean fromApply();

    boolean fromInject();

    Method getMethod();

    Field getField();

    String getName();

    enum CommandType {
        SET,
        GET,
        TRACK,
        APPLY,
        INJECT
    }
}