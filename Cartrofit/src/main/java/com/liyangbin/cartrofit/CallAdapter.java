package com.liyangbin.cartrofit;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;

public abstract class CallAdapter<SCOPE, CALL extends CallAdapter<SCOPE, CALL>.Call> {

    public static final int CATEGORY_SET = 1;
    public static final int CATEGORY_GET = 1 << 1;
    public static final int CATEGORY_TRACK = 1 << 2;
    public static final int CATEGORY_TRACK_EVENT = CATEGORY_TRACK | (1 << 3);

    public abstract SCOPE getScopeInfo(Class<?> scopeClass);

    public abstract CALL onCreateCall(SCOPE scope, Cartrofit.Key key, int category);

    public abstract Object invoke(CALL call, Object arg);

    public abstract boolean isInterested(SCOPE scope);

    public abstract boolean hasCategory(CALL call, int category);

    public abstract Class<?> extractValueType(CALL arg);

    interface ConvertAdapter {
        int getConverterCount();
    }

    public final static class ConvertSolution {
        HashMap<String, Class<?>> supportType = new HashMap<>();
        HashMap<String, Class<? extends Annotation>> supportAnnotation = new HashMap<>();
        int flag;
        Class<?> protocolType;
        Converter<?, ?> converter;
    }

    public static abstract class Constraint<T> {
        Class<T> type;

        abstract T get();
    }

    public static abstract class AnnotatedConstraint<A extends Annotation, T> extends Constraint<T> {
        Class<A> annotationType;

        abstract A getAnnotation();
    }

    public static class Builder<E> {
        public final <FROM> ConverterBuilder<FROM> convert(Class<FROM> clazz) {
            return new ConverterBuilder<>(clazz);
        }

        public final <FROM1, FROM2> ConverterBuilder2<FROM1, FROM2> combine(Class<FROM1> clazz1, Class<FROM2> clazz2) {
            return new ConverterBuilder2<>(clazz1, clazz2);
        }

        public final <FROM1, FROM2, FROM3> ConverterBuilder3<FROM1, FROM2, FROM3>
        combine(Class<FROM1> clazz1, Class<FROM2> clazz2, Class<FROM3> clazz3) {
            return new ConverterBuilder3<>(clazz1, clazz2, clazz3);
        }

        public final <FROM1, FROM2, FROM3, FROM4> ConverterBuilder4<FROM1, FROM2, FROM3, FROM4>
        combine(Class<FROM1> clazz1, Class<FROM2> clazz2, Class<FROM3> clazz3, Class<FROM4> clazz4) {
            return new ConverterBuilder4<>(clazz1, clazz2, clazz3, clazz4);
        }

        public final <FROM1, FROM2, FROM3, FROM4, FROM5> ConverterBuilder5<FROM1, FROM2, FROM3, FROM4, FROM5>
        combine(Class<FROM1> clazz1, Class<FROM2> clazz2, Class<FROM3> clazz3, Class<FROM4> clazz4, Class<FROM5> clazz5) {
            return new ConverterBuilder5<>(clazz1, clazz2, clazz3, clazz4, clazz5);
        }
    }

    public static class TypeBuilder<T> extends Builder<Class<T>> {
    }

    public static class AnnotatedBuilder<A extends Annotation, T> extends Builder<AnnotatedConstraint<A, T>> {
    }

    @SuppressWarnings("unchecked")
    public abstract class Call {
        private final Annotation annotation;

        public Call(Annotation annotation) {
            this.annotation = annotation;
        }

        public <A extends Annotation> A getAnnotation() {
            return (A) annotation;
        }

        final Object invoke(Object arg) {
            return CallAdapter.this.invoke((CALL) this, arg);
        }

        final boolean isTrackable() {
            return CallAdapter.this.hasCategory((CALL) this, CATEGORY_TRACK);
        }

        void getConvertSolutionList(ApiBuilder builder) {
        }

        final boolean isEvent() {
            return CallAdapter.this.hasCategory((CALL) this, CATEGORY_TRACK_EVENT);
        }
    }
}
