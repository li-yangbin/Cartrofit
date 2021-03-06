package com.liyangbin.cartrofit;

import java.lang.annotation.Annotation;

public class DelegateCall extends Call {

    private Call delegate;

    DelegateCall(Call delegate) {
        this.delegate = delegate;
    }

    @Override
    public Annotation getAnnotation() {
        return delegate.getAnnotation();
    }

    @Override
    boolean hasCategory(int category) {
        return delegate.hasCategory(category);
    }

    @Override
    public boolean hasToken(String token) {
        return super.hasToken(token) || delegate.hasToken(token);
    }

    @Override
    public Object invoke(Object[] parameter) throws Throwable {
        return delegate.exceptionalInvoke(parameter);
    }

    @Override
    boolean handleFlowCallbackException(Throwable suspect, Object callback) {
        return super.handleFlowCallbackException(suspect, callback)
                || delegate.handleFlowCallbackException(suspect, callback);
    }
}
