package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Token;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public abstract class Call {
    private Key key;
    private int category;
    private ParameterContext parameterContext;

    private CallGroup<?> parentCall;
    private CartrofitContext context;
    private List<String> tokenList;

    void dispatchInit(ParameterContext parameterContext) {
        if (key.field != null) {
            key.field.setAccessible(true);
        }
        this.parameterContext = parameterContext;

        onInit();
    }

    public ParameterContext getParameterContext() {
        if (getParent() != null) {
            return getParent().getParameterContext();
        }
        if (parameterContext == null) {
            parameterContext = onCreateParameterContext();
        }
        return parameterContext;
    }

    public ParameterGroup getBindingParameter() {
        return getParameterContext().extractParameterFromCall(this);
    }

    protected ParameterContext onCreateParameterContext() {
        return new ParameterContext(getKey());
    }

    void setKey(Key key, CartrofitContext context) {
        this.key = key;
        this.context = context;
    }

    public Key getKey() {
        return key;
    }

    public CartrofitContext getContext() {
        return context;
    }

    void setTokenList(Token token) {
        tokenList = Arrays.asList(token.value());
    }

    public boolean hasToken(String token) {
        return tokenList != null && tokenList.contains(token);
    }

    public void onInit() {
    }

    public void addCategory(int category) {
        this.category |= category;
    }

    public abstract Object invoke(Object[] parameter);

    void attachParent(CallGroup<?> parent) {
        parentCall = parent;
    }

    public CallGroup<?> getParent() {
        return parentCall;
    }

    public final Method getMethod() {
        return key.method;
    }

    public boolean hasCategory(int category) {
        return (this.category & category) != 0;
    }

    public final int getId() {
        return key.getId();
    }
}
