package com.liyangbin.cartrofit;

import java.lang.annotation.Annotation;

public abstract class CallAdapter {

    public static final int CATEGORY_SET = 1;
    public static final int CATEGORY_GET = 1 << 1;
    public static final int CATEGORY_TRACK = 1 << 2;
    public static final int CATEGORY_TRACK_EVENT = CATEGORY_TRACK | (1 << 3);

    public abstract Annotation extractScope(Class<?> scopeClass, ConverterFactory factory);

    public abstract Call onCreateCall(Annotation scope, Cartrofit.Key key, int category);

    public static <A extends Annotation> A findScopeByClass(Class<A> annotationClazz, Class<?> clazz) {
        A scope = clazz.getDeclaredAnnotation(annotationClazz);
        if (scope != null) {
            return scope;
        }
        Class<?> enclosingClass = clazz.getEnclosingClass();
        while (enclosingClass != null) {
            scope = enclosingClass.getDeclaredAnnotation(annotationClazz);
            if (scope != null) {
                return scope;
            }
            enclosingClass = enclosingClass.getEnclosingClass();
        }
        return null;
    }

    public static abstract class Call {

        protected Cartrofit.Key key;
        protected int category;

        protected Call(Cartrofit.Key key) {
            this.key = key;
        }

        protected final void addCategory(int category) {
            this.category |= category;
        }

        public abstract Object invoke(Object arg);

        public void buildConvertSolution(ConverterFactory factory) {
        }

        public final boolean hasCategory(int category) {
            return (this.category & category) != 0;
        }
    }
}
