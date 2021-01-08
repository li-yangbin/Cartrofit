package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Union;

public interface Interceptor {

    Object process(InvokeSession session, Union parameter);

    class InvokeSession {
        ChainNode current;
        final Call call;
        boolean cancelled;

        public final Object invoke(Union parameter) {
            if (cancelled) {
                throw new RuntimeException("Illegal invoke after cancel");
            }
            onInterceptPass(current.interceptor, parameter);
            if (current.previous != null) {
                current = current.previous;
                return current.doProcess(this, parameter);
            } else {
                try {
                    onInterceptComplete(parameter);
                    return call.mapInvoke(parameter);
                } finally {
                    parameter.recycle();
                }
            }
        }

        public InvokeSession(Call call) {
            this.call = call;
        }

        public final void cancel() {
            if (!cancelled) {
                cancelled = true;
                onInterceptCancel();
            }
        }

        public boolean isReceive() {
            return false;
        }

        public final Call getCall() {
            return this.call;
        }

        public void onInterceptStart(Union parameter) {
        }

        public void onInterceptPass(Interceptor interceptor, Union parameter) {
        }

        public void onInterceptCancel() {
        }

        public void onInterceptComplete(Union transact) {
        }
    }
}

class ChainNode {
    final Interceptor interceptor;
    ChainNode previous;

    ChainNode(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    Object doProcess(Interceptor.InvokeSession session, Union parameter) {
        return interceptor.process(session, parameter);
    }
}

class InterceptorChain {
    private ChainNode top;

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

    Object doProcess(Interceptor.InvokeSession session, Union parameter) {
        session.current = top;
        session.onInterceptStart(parameter);
        return top.doProcess(session, parameter);
    }
}