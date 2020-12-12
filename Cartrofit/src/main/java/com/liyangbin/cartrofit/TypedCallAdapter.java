package com.liyangbin.cartrofit;

import java.lang.annotation.Annotation;

@SuppressWarnings("unchecked")
public abstract class TypedCallAdapter<SCOPE extends Annotation, CALL extends CallAdapter.Call> extends CallAdapter {

    @Override
    public final Annotation extractScope(Class<?> scopeClass, ConverterFactory factory) {
        return extractTypedScope(scopeClass, factory);
    }

    public abstract SCOPE extractTypedScope(Class<?> scopeClass, ConverterFactory factory);

    @Override
    public final Call onCreateCall(Annotation scope, Cartrofit.Key key, int category) {
        return onCreateTypedCall((SCOPE) scope, key, category);
    }

    public abstract CALL onCreateTypedCall(SCOPE scope, Cartrofit.Key key, int category);
}
