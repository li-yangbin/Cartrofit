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

    InterceptorChain copyAndAttach(InterceptorChain newNode) {
        InterceptorChain copy = new InterceptorChain(this.parent, this.interceptor);
        InterceptorChain copyParent = copy.parent;
        while (copyParent != null) {
            copyParent = new InterceptorChain(copyParent.parent, copyParent.interceptor);
            copyParent = copyParent.parent;
        }
        newNode.parent = copy;
        return newNode;
    }

    Object doProcess(Command command, Object parameter) throws Throwable {
        boolean commandChecked = true;
        if (interceptor instanceof CommandPredictor) {
            commandChecked = ((CommandPredictor) interceptor).checkCommand(command);
        }
        if (commandChecked) {
            this.command = command;
            return interceptor.process(this, parameter);
        } else {
            return parent != null ? parent.doProcess(this.command, parameter)
                    : this.command.invoke(parameter);
        }
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
    public int hashCode() {
        return command.hashCode();
    }
}