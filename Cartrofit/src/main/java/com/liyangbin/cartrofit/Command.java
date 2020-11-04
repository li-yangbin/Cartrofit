package com.liyangbin.cartrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Command {

    int getId();

    int getPropertyId();

    int getArea();

    DataSource getSource();

    Object invoke(Object parameter);

    CommandType type();

    String[] getCategory();

    boolean fromApply();

    boolean fromInject();

    Method getMethod();

    Field getField();

    String getName();

    Class<?> getOutputType();

    Class<?> getInputType();
}