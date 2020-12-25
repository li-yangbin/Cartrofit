package com.liyangbin.cartrofit;

public interface Interceptor {

    Object process(InvokeSession session, Object parameter);

    class InvokeSession {
        ChainNode current;
        final CallAdapter.Call call;
        boolean cancelled;

        InvokeSession(CallAdapter.Call call) {
            this.call = call;
        }

        public final Object invoke(Object parameter) {
            if (cancelled) {
                throw new RuntimeException("Illegal invoke after cancel");
            }
            onInterceptPass(current.interceptor, parameter);
            current = current.previous;
            if (current != null) {
                return current.doProcess(this, parameter);
            } else {
                onInterceptComplete(parameter);
                return call.mapInvoke(parameter);
            }
        }

        public final void cancel() {
            if (!cancelled) {
                cancelled = true;
                onInterceptCancel();
            }
        }

        public CallAdapter.Call getCall() {
            return this.call;
        }

        public void onInterceptStart(Object transact) {
        }

        public void onInterceptPass(Interceptor interceptor, Object transact) {
        }

        public void onInterceptCancel() {
        }

        public void onInterceptComplete(Object transact) {
        }
    }
}

class ChainNode {
    final Interceptor interceptor;
    ChainNode previous;

    ChainNode(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    Object doProcess(Interceptor.InvokeSession session, Object parameter) {
        return interceptor.process(session, parameter);
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

    Object doProcess(Interceptor.InvokeSession session, Object parameter) {
        if (top != null) {
            session.current = top;
            session.onInterceptStart(parameter);
            return top.doProcess(session, parameter);
        }
        return session.call.mapInvoke(parameter);
    }
}