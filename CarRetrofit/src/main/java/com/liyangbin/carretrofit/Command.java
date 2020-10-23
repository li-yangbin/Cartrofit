package com.liyangbin.carretrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Command {

    int getId();

    void setPropertyId(int propertyId);

    int getPropertyId();

    void setArea(int area);

    int getArea();

    DataSource getSource();

    Object invoke(Object parameter) throws Throwable;

    CommandType type();

    String getCategory();

    boolean fromApply();

    boolean fromInject();

    Method getMethod();

    Field getField();

    String getName();
}