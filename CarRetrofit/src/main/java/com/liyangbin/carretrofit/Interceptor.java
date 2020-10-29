package com.liyangbin.carretrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Interceptor {
    Object process(Command command, Object parameter) throws Throwable;

    default boolean checkCommand(Command command) {
        return true;
    }
}

class InterceptorChain {
    private ChainNode top;

    InterceptorChain() {
    }

    InterceptorChain(Interceptor firstInterceptor) {
        addInterceptor(firstInterceptor);
    }

    void addInterceptor(Interceptor interceptor) {
        ChainNode newTop = new ChainNode(interceptor);
        newTop.previous = top;
        top = newTop;
    }

    void addInterceptorToBottom(Interceptor interceptor) {
        addNodeToBottom(new ChainNode(interceptor));
    }

    void addInterceptorChainToBottom(InterceptorChain chain) {
        addNodeToBottom(chain.top);
    }

    private void addNodeToBottom(ChainNode node) {
        if (top != null) {
            ChainNode loopNode = top;
            while (loopNode.previous != null) {
                loopNode = loopNode.previous;
            }
            loopNode.previous = node;
        } else {
            top = node;
        }
    }

    Object doProcess(Command command, Object parameter) throws Throwable {
        return top != null ? top.doProcess(command, parameter) : command.invoke(parameter);
    }

    private static class ChainNode implements Command {
        private final Interceptor interceptor;
        private ChainNode previous;
        private Command command;

        ChainNode(Interceptor interceptor) {
            this.interceptor = interceptor;
        }

        Object doProcess(Command command, Object parameter) throws Throwable {
            this.command = command;
            return interceptor.process(this, parameter);
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            return previous != null ? previous.doProcess(this.command, parameter)
                    : this.command.invoke(parameter);
        }

        @Override
        public int getId() {
            return command.getId();
        }

        @Override
        public void setPropertyId(int propertyId) {
        }

        @Override
        public int getPropertyId() {
            return previous.getPropertyId();
        }

        @Override
        public void setArea(int area) {
        }

        @Override
        public int getArea() {
            return previous.getArea();
        }

        @Override
        public DataSource getSource() {
            return previous.getSource();
        }

        @Override
        public CommandType type() {
            return previous.type();
        }

        @Override
        public String[] getCategory() {
            return previous.getCategory();
        }

        @Override
        public boolean fromApply() {
            return previous.fromApply();
        }

        @Override
        public boolean fromInject() {
            return previous.fromInject();
        }

        @Override
        public Method getMethod() {
            return previous.getMethod();
        }

        @Override
        public Field getField() {
            return previous.getField();
        }

        @Override
        public String getName() {
            return previous.getName();
        }

        @Override
        public Class<?> getOutputType() {
            return previous.getOutputType();
        }

        @Override
        public Class<?> getInputType() {
            return previous.getInputType();
        }
    }
}