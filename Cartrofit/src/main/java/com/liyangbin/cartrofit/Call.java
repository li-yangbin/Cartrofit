package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Token;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Call implements Cloneable {
    private Key key;
    private int category;
    InterceptorChain interceptorChain;
    private ParameterContext parameterContext;

    private CallGroup<?> parentCall;
    private AbsContext context;
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

    void setKey(Key key, AbsContext context) {
        this.key = key;
        this.context = context;
    }

    public Key getKey() {
        return key;
    }

    public AbsContext getContext() {
        return context;
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
