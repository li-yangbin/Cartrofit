package com.liyangbin.carretrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Interceptor {
    Object process(ICommand ICommand, Object parameter) throws Throwable;

    default Interceptor after(Interceptor interceptor) {
        return (command, parameter) -> new InterceptorChain(
                new InterceptorChain(null, Interceptor.this), interceptor)
                .doProcess(command, parameter);
    }
}

class InterceptorChain implements ICommand {
    private InterceptorChain parent;
    private Interceptor interceptor;
    private ICommand command;

    InterceptorChain(InterceptorChain parent, Interceptor interceptor) {
        this.parent = parent;
        this.interceptor = interceptor;
    }

    Object doProcess(ICommand command, Object parameter) throws Throwable {
        this.command = command;
        return interceptor.process(this, parameter);
    }

    @Override
    public Object invoke(Object parameter) throws Throwable {
        return parent != null ? parent.doProcess(this.command, parameter)
                : this.command.invoke(parameter);
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
    public String getToken() {
        return command.getToken();
    }

    @Override
    public void setKey(int key) {
        command.setKey(key);
    }

    @Override
    public int getKey() {
        return command.getKey();
    }

    @Override
    public void setArea(int area) {
        command.setArea(area);
    }

    @Override
    public int getArea() {
        return command.getArea();
    }

    @Override
    public void setSource(DataSource source) {
        command.setSource(source);
    }

    @Override
    public DataSource getSource() {
        return command.getSource();
    }
}