package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.call.Call;
import com.liyangbin.cartrofit.funtion.Consumer;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
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

    private Cartrofit.CallInflater mCallInflater;
    private final ArrayList<CallSolution<?>> mCallSolutionList = new ArrayList<>();

    public final class CallSolutionBuilder {
        public <A extends Annotation> CallSolution<A> create(Class<A> annotationClass) {
            return new CallSolution<>(annotationClass);
        }
    }

    public abstract class CallProvider<A extends Annotation> implements Cartrofit.CallInflater {

        public abstract Call provide(int category, A annotation, Cartrofit.Key key);

        @Override
        public Call inflateByIdIfThrow(Cartrofit.Key key, int id, int category) {
            return mCallInflater.inflateByIdIfThrow(key, id, category);
        }

        @Override
        public Call inflateById(Cartrofit.Key key, int id, int category) {
            return mCallInflater.inflateById(key, id, category);
        }

        @Override
        public Call inflate(Cartrofit.Key key, int category) {
            return mCallInflater.inflate(key, category);
        }

        @Override
        public void inflateCallback(Class<?> callbackClass, int flag, Consumer<Call> resultReceiver) {
            mCallInflater.inflateCallback(callbackClass, flag, resultReceiver);
        }
    }

    public final class CallSolution<A extends Annotation> {
        IntPredicate predictor;
        Class<A> candidateClass;
        CallProvider<A> provider;
        boolean keepLookingIfNull;

        CallSolution(Class<A> candidateClass) {
            this.candidateClass = candidateClass;
        }

        public CallSolution<A> takeIf(IntPredicate intPredicate) {
            predictor = intPredicate;
            return this;
        }

        public CallSolution<A> takeIfAny() {
            keepLookingIfNull = true;
            return takeIf(category -> true);
        }

        public CallSolution<A> takeIfEqual(int expect) {
            return takeIf(category -> category == expect);
        }

        public CallSolution<A> takeIfContains(int expect) {
            return takeIf(category -> (category & expect) != 0);
        }

        public void provide(CallProvider<A> provider) {
            this.provider = provider;
            mCallSolutionList.add(this);
        }

        private Call createCall(int category, Cartrofit.Key key) {
            if (predictor.test(category)) {
                A annotation = key.getAnnotation(candidateClass);
                if (annotation != null) {
                    return provider.provide(category, annotation, key);
                }
            }
            return null;
        }

        void getAnnotationCandidates(int category, ArrayList<Class<? extends Annotation>> outCandidates) {
            if (predictor.test(category)) {
                outCandidates.add(candidateClass);
            }
        }
    }

    public void init(Cartrofit.CallInflater callInflater) {
        mCallInflater = callInflater;
        onProvideCallSolution(new CallSolutionBuilder());
    }

    public abstract Object extractScope(Class<?> scopeClass, ConverterFactory scopeConverterSolutionFactory);

    public abstract void onProvideCallSolution(CallSolutionBuilder builder);

    Call createCall(Cartrofit.Key key, int category) {
        for (int i = 0; i < mCallSolutionList.size(); i++) {
            CallSolution<?> solution = mCallSolutionList.get(i);
            Call call = solution.createCall(category, key);
            if (call != null || solution.keepLookingIfNull) {
                return call;
            }
        }
        return null;
    }

    void getAnnotationCandidates(int category, ArrayList<Class<? extends Annotation>> outCandidates) {
        for (int i = 0; i < mCallSolutionList.size(); i++) {
            mCallSolutionList.get(i).getAnnotationCandidates(category, outCandidates);
        }
    }

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
