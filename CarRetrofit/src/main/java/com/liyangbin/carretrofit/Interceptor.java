package com.liyangbin.carretrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Interceptor {
    Object process(Command command, Object parameter) throws Throwable;

    default Interceptor after(Interceptor interceptor) {
        return (command, parameter) -> new InterceptorChain(
                new InterceptorChain(null, Interceptor.this), interceptor)
                .doProcess(command, parameter);
    }
}

class InterceptorChain implements Command {
    private InterceptorChain parent;
    private Interceptor interceptor;
    private Command command;

    InterceptorChain(InterceptorChain parent, Interceptor interceptor) {
        this.parent = parent;
        this.interceptor = interceptor;
    }

    InterceptorChain copy() {
        InterceptorChain copy = new InterceptorChain(this.parent, this.interceptor);
        InterceptorChain copyParent = copy.parent;
        while (copyParent != null) {
            copyParent = new InterceptorChain(copyParent.parent, copyParent.interceptor);
            copyParent = copyParent.parent;
        }
        return copy;
    }

    InterceptorChain attach(InterceptorChain node) {
        if (node != null) {
            InterceptorChain loopNode = node;
            while (loopNode.parent != null) {
                loopNode = loopNode.parent;
            }
            loopNode.parent = this;
            return node;
        } else {
            return this;
        }
    }

    void addToBottom(Interceptor interceptor) {
        InterceptorChain loopNode = this;
        while (loopNode.parent != null) {
            loopNode = loopNode.parent;
        }
        loopNode.parent = new InterceptorChain(null, interceptor);
    }

    Object doProcess(Command command, Object parameter) throws Throwable {
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
    public void setPropertyId(int propertyId) {
        command.setPropertyId(propertyId);
    }

    @Override
    public int getPropertyId() {
        return command.getPropertyId();
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

    @Override
    public String toString() {
        return command.toString();
    }

    @Override
    public int getId() {
        return command.getId();
    }

    @Override
    public void addInterceptorToTop(Interceptor interceptor) {
        command.addInterceptorToTop(interceptor);
    }

    @Override
    public void addInterceptorToBottom(Interceptor interceptor) {
        command.addInterceptorToBottom(interceptor);
    }

    @Override
    public void setConverter(Converter<?, ?> converter) {
        command.setConverter(converter);
    }

    @Override
    public int hashCode() {
        return command.hashCode();
    }
}