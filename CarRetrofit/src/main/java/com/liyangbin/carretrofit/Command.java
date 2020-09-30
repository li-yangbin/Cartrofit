package com.liyangbin.carretrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Command {

    int getId();

    void setPropertyId(int propertyId);

    void addInterceptorToTop(Interceptor interceptor);

    void addInterceptorToBottom(Interceptor interceptor);

    void setConverter(Converter<?, ?> converter);

    int getPropertyId();

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
}