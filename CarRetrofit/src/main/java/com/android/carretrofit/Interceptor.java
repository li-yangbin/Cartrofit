package com.android.carretrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Interceptor {
    Object process(Command command, Object[] parameters) throws Throwable;
}

class InterceptorChain extends Command {
    InterceptorChain parent;
    Interceptor interceptor;
    Command command;

    InterceptorChain(InterceptorChain parent, Interceptor interceptor) {
        this.parent = parent;
        this.interceptor = interceptor;
    }

    Object doProcess(Command command, Object[] parameters) throws Throwable {
        this.command = command;
        return interceptor.process(this, parameters);
    }

    @Override
    public Object invoke(Object[] parameters) throws Throwable {
        return parent != null ? parent.doProcess(this.command, parameters)
                : this.command.invoke(parameters);
    }

    @Override
    public CommandType type() {
        return command.type();
    }

    @Override
    public boolean fromApply() {
        return command.fromApply();
    }

    @Override
    public boolean fromInject() {
        return command.fromInject();
    }

    @Override
    public Method getMethod() {
        return command.getMethod();
    }

    @Override
    public Field getField() {
        return command.getField();
    }

    @Override
    public String getName() {
        return command.getName();
    }

    @Override
    public int getKey() {
        return command.getKey();
    }

    @Override
    public int getArea() {
        return command.getArea();
    }
}