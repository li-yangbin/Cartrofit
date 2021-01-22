package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Token;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Call implements Cloneable {
    private Cartrofit.Key key;
    private int category;
    InterceptorChain interceptorChain;
    private ParameterContext parameterContext;

    private CallGroup<?> parentCall;
    private CallAdapter callAdapter;
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

    protected ParameterContext onCreateParameterContext() {
        return new ParameterContext(getKey());
    }

    void setKey(Cartrofit.Key key, CallAdapter adapter) {
        this.key = key;
        this.callAdapter = adapter;
    }

    public Cartrofit.Key getKey() {
        return key;
    }

    public CallAdapter getAdapter() {
        return callAdapter;
    }

    void setTokenList(Token token) {
        tokenList = Arrays.asList(token.value());
    }

    public void addToken(String token) {
        if (tokenList == null) {
            tokenList = new ArrayList<>();
        }
        tokenList.add(token);
    }

    public boolean hasToken(String token) {
        return tokenList != null && tokenList.contains(token);
    }

    public void onInit() {
    }

    void addCategory(int category) {
        this.category |= category;
    }

    public final Object invoke(Union parameter) {
        if (interceptorChain != null) {
            return interceptorChain.doProcess(onCreateInvokeSession(), parameter);
        } else {
            try {
                return mapInvoke(parameter);
            } finally {
                parameter.recycle();
            }
        }
    }

    public abstract Object mapInvoke(Union parameter);

    void attachParent(CallGroup<?> parent) {
        parentCall = parent;
    }

    public CallGroup<?> getParent() {
        return parentCall;
    }

    protected Interceptor.InvokeSession onCreateInvokeSession() {
        return new Interceptor.InvokeSession(this);
    }

    public Call copyByHost(Call host) {
        try {
            Call call = (Call) clone();
            host.disableInOutConvert();
            return call;
        } catch (CloneNotSupportedException error) {
            throw new RuntimeException(error);
        }
    }

    private void disableInOutConvert() {
    }

    public final Method getMethod() {
        return key.method;
    }

    public void addInterceptor(Interceptor interceptor, boolean toBottom) {
        if (interceptorChain != null) {
            if (toBottom) {
                interceptorChain.addInterceptorToBottom(interceptor);
            } else {
                interceptorChain.addInterceptor(interceptor);
            }
        } else {
            interceptorChain = new InterceptorChain(interceptor);
        }
    }

    public boolean hasCategory(int category) {
        return (this.category & category) != 0;
    }

    public final int getId() {
        return key.getId();
    }
}
