package com.liyangbin.cartrofit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface Interceptor {
    Object process(Command command, Object parameter);

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

    Object doProcess(Command command, Object parameter) {
        return top != null ? top.doProcess(command, parameter) : command.invoke(parameter);
    }

    private static class ChainNode implements Command {
        private final Interceptor interceptor;
        private ChainNode previous;
        private Command command;

        ChainNode(Interceptor interceptor) {
            this.interceptor = interceptor;
        }

        Object doProcess(Command command, Object parameter) {
            this.command = command;
            return interceptor.process(this, parameter);
        }

        @Override
        public Object invoke(Object parameter) {
            return previous != null ? previous.doProcess(this.command, parameter)
                    : this.command.invoke(parameter);
        }

        @Override
        public int getId() {
            return command.getId();
        }

        @Override
        public int getPropertyId() {
            return previous.getPropertyId();
        }

        @Override
        public int getArea() {
            return previous.getArea();
        }

        @Override
        public CommandType getType() {
            return previous.getType();
        }

        @Override
        public String[] getCategory() {
            return previous.getCategory();
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