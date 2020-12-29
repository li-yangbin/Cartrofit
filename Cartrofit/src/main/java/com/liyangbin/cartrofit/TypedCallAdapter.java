package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.call.Call;

@SuppressWarnings("unchecked")
public abstract class TypedCallAdapter<SCOPE, CALL extends Call> extends CallAdapter {

    @Override
    public final Object extractScope(Class<?> scopeClass, ConverterFactory factory) {
        return extractTypedScope(scopeClass, factory);
    }

    public abstract SCOPE extractTypedScope(Class<?> scopeClass, ConverterFactory factory);

    @Override
    public final Call onCreateCall(Object scopeObj, Cartrofit.Key key, int category) {
        return onCreateTypedCall((SCOPE) scopeObj, key, category);
    }

    public abstract CALL onCreateTypedCall(SCOPE scope, Cartrofit.Key key, int category);
}
