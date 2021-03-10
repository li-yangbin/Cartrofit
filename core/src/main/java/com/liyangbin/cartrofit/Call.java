package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Token;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Call {
    private Key key;
    private Annotation annotation;
    private ArrayList<ExceptionHandler<?>> exceptionHandlers;
    private int category;
    private ParameterContext parameterContext;

    private CallGroup<?> parentCall;
    private CartrofitContext context;
    private List<String> tokenList;

    void dispatchInit(ParameterContext parameterContext) {
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

    void attach(Key key, CartrofitContext context, ArrayList<ExceptionHandler<?>> exceptionHandlers) {
        this.key = key;
        this.context = context;
        this.exceptionHandlers = exceptionHandlers;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public Key getKey() {
        return key;
    }

    public CartrofitContext<?> getContext() {
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

    public void setCategoryAndAnnotation(int category, Annotation annotation) {
        this.category = category;
        this.annotation = annotation;
    }

    final Object exceptionalInvoke(Object[] parameter) throws Throwable {
        try {
            Object interceptedValue = getContext().onInterceptCallInvocation(this, parameter);
            if (interceptedValue != Cartrofit.SKIP) {
                return interceptedValue;
            }
            return invoke(parameter);
        } catch (Throwable suspect) {
            if (suspect instanceof RuntimeException) {
                throw suspect;
            }
            if (exceptionHandlers != null) {
                for (int i = 0; i < exceptionHandlers.size(); i++) {
                    ExceptionHandler<?> handler = exceptionHandlers.get(i);
                    Object handledResult = handler.handleException(suspect, this, parameter);
                    if (handledResult != Cartrofit.SKIP) {
                        return handledResult;
                    }
                }
            }
            throw suspect;
        }
    }

    boolean handleFlowCallbackException(Throwable suspect, Object callback) {
        if (suspect instanceof RuntimeException) {
            return false;
        }
        if (exceptionHandlers != null) {
            for (int i = 0; i < exceptionHandlers.size(); i++) {
                ExceptionHandler<?> handler = exceptionHandlers.get(i);
                if (handler.handleFlowCallbackException(suspect, this, callback)) {
                    return true;
                }
            }
        }
        return false;
    }

    public abstract Object invoke(Object[] parameter) throws Throwable;

    void attachParent(CallGroup<?> parent) {
        parentCall = parent;
    }

    public CallGroup<?> getParent() {
        return parentCall;
    }

    public final Method getMethod() {
        return key.method;
    }

    boolean hasCategory(int category) {
        return (this.category & category) != 0;
    }

    public final int getId() {
        return key.getId();
    }
}
