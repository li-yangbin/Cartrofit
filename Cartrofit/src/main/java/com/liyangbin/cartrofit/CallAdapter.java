package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.call.Call;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.IntPredicate;

public abstract class CallAdapter {

    public static final int CATEGORY_SET = 1;
    public static final int CATEGORY_GET = 1 << 1;
    public static final int CATEGORY_TRACK = 1 << 2;
    public static final int CATEGORY_TRACK_EVENT = CATEGORY_TRACK | (1 << 3);

//    public static final int CATEGORY_REGISTER = 1 << 4;
//    public static final int CATEGORY_RETURN = 1 << 5;
//    public static final int CATEGORY_INJECT_IN = 1 << 6;
//    public static final int CATEGORY_INJECT_OUT = 1 << 7;
//    public static final int CATEGORY_INJECT = CATEGORY_INJECT_IN | CATEGORY_INJECT_OUT;

    public static final int CATEGORY_DEFAULT = 0xffffffff;

    protected Cartrofit.CallInflater mCallInflater;

    public void setCallInflater(Cartrofit.CallInflater callInflater) {
        mCallInflater = callInflater;
    }

    public final class CallSolutionBuilder {
        CallSolutionBuilder takeIf(IntPredicate intPredicate) {
            return this;
        }

        <A extends Annotation> CallSolutionBuilder provide(Class<A> annotationClass,
                                                           BiFunction<A, Cartrofit.Key, Call> provider) {
            return provide(annotationClass, provider, false);
        }

        <A extends Annotation> CallSolutionBuilder provide(Class<A> annotationClass,
                                                           BiFunction<A, Cartrofit.Key, Call> provider,
                                                           boolean keepSearchingIfProvideNull) {
            return this;
        }
    }

    public abstract Object extractScope(Class<?> scopeClass, ConverterFactory scopeConverterSolutionFactory);

    public abstract Call onCreateCall(Object scopeObj, Cartrofit.Key key, int category);

    public abstract void getAnnotationCandidates(int category, ArrayList<Class<? extends Annotation>> outCandidates);

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

    public interface FieldAccessible {
        void set(Object target, Object value) throws IllegalAccessException;
        Object get(Object target) throws IllegalAccessException;
    }
}
