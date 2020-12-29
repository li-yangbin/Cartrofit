package com.liyangbin.cartrofit.call;

public interface Interceptor {

    Object process(InvokeSession session, Object transact);

    class InvokeSession {
        ChainNode current;
        final Call call;
        boolean cancelled;

        public final Object invoke(Object transact) {
            if (cancelled) {
                throw new RuntimeException("Illegal invoke after cancel");
            }
            onInterceptPass(current.interceptor, transact);
            current = current.previous;
            if (current != null) {
                return current.doProcess(this, transact);
            } else {
                onInterceptComplete(transact);
                return call.mapInvoke(transact);
            }
        }

        InvokeSession(Call call) {
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

    Object doProcess(Interceptor.InvokeSession session, Object transact) {
        return interceptor.process(session, transact);
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

    Object doProcess(Interceptor.InvokeSession session, Object transact) {
        if (top != null) {
            session.current = top;
            session.onInterceptStart(transact);
            return top.doProcess(session, transact);
        }
        return session.call.mapInvoke(transact);
    }
}