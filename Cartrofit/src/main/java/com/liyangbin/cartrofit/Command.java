package com.liyangbin.cartrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Command {

    int getId();

    int getPropertyId();

    int getArea();

    Object invoke(Object parameter);

    CommandType getType();

    String[] getCategory();

    Method getMethod();

    Field getField();

    String getName();

    Class<?> getOutputType();

    Class<?> getInputType();
}