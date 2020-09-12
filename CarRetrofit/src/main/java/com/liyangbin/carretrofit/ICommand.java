package com.liyangbin.carretrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface ICommand {

    void setKey(int key);

    int getKey();

    void setArea(int area);

    int getArea();

    void setSource(DataSource source);

    DataSource getSource();

    Object invoke(Object parameter) throws Throwable;

    CommandType type();

    boolean fromApply();

    boolean fromInject();

    Method getMethod();

    Field getField();

    String getName();

    String getToken();

    enum CommandType {
        SET,
        GET,
        TRACK,
        APPLY,
        INJECT
    }
}