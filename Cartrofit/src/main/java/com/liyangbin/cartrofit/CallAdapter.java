package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Category;
import com.liyangbin.cartrofit.funtion.Consumer;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;
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

    private Cartrofit mCartrofit;
    private final ArrayList<CallSolution<?>> mCallSolutionList = new ArrayList<>();

    public final class CallSolutionBuilder {
        public <A extends Annotation> CallSolution<A> create(Class<A> annotationClass) {
            return new CallSolution<>(annotationClass);
        }
    }

    public abstract class CallProvider<A extends Annotation> {

        public abstract Call provide(int category, A annotation, Cartrofit.Key key);

        public final Call inflateByIdIfThrow(Cartrofit.Key key, int id, int category) {
            return mCartrofit.getOrCreateCallById(key.record, id, category, true);
        }

        public final Call inflateById(Cartrofit.Key key, int id, int category) {
            return mCartrofit.getOrCreateCallById(key.record, id, category, false);
        }

        public final Call reInflate(Cartrofit.Key key, int category) {
            return key.record.createAdapterCall(key, category);
        }

        public final <T> void inflateCallback(Class<?> callbackClass,
                                              int category, Consumer<Call> resultReceiver) {
            ArrayList<Cartrofit.Key> childKeys = getChildKey(callbackClass);
            for (int i = 0; i < childKeys.size(); i++) {
                Cartrofit.Key childKey = childKeys.get(i);
                Call call = mCartrofit.getOrCreateCall(childKey.record, childKey, category);
                if (call != null) {
                    resultReceiver.accept(call);
                }
            }
        }
    }

    public ArrayList<Cartrofit.Key> getChildKey(Class<?> callbackClass) {
        return mCartrofit.getApi(callbackClass).getChildKey();
    }

    public final class CallSolution<A extends Annotation> {
        IntPredicate predictor;
        Class<A> candidateClass;
        int expectedCategory;
        CallProvider<A> provider;
        BiConsumer<A, Cartrofit.Key> keyGrammarChecker;
        List<Class<? extends Annotation>[]> withInAnnotationCandidates;
        List<Class<? extends Annotation>> withAnnotationCandidates;
        HashMap<Class<? extends Annotation>, Class<?>> withAnnotationTypeMap;
        boolean keepLookingIfNull;

        CallSolution(Class<A> candidateClass) {
            this.candidateClass = candidateClass;
            Category category = candidateClass.getDeclaredAnnotation(Category.class);
            if (category != null) {
                int categoryFlag = category.value();
                if (categoryFlag == Category.CATEGORY_DEFAULT) {
                    keepLookingIfNull = true;
                    predictor = flag -> true;
                } else {
                    predictor = flag -> (flag & categoryFlag) != 0;
                }
            } else {
                throw new CartrofitGrammarException("Must declare Category attribute on annotation:"
                        + candidateClass);
            }
        }

        @SafeVarargs
        public final CallSolution<A> checkParameterIncluded(Class<? extends Annotation>... included) {
            return checkParameterIncluded(null, included);
        }

        @SafeVarargs
        public final CallSolution<A> checkParameterIncluded(Class<?> fixedType, Class<? extends Annotation>... included) {
            if (included == null || included.length == 0) {
                return this;
            }
            if (fixedType != null) {
                if (withAnnotationTypeMap == null) {
                    withAnnotationTypeMap = new HashMap<>();
                }
                for (Class<? extends Annotation> annotationClass : included) {
                    withAnnotationTypeMap.put(annotationClass, fixedType);
                }
            }
            if (withInAnnotationCandidates == null) {
                withInAnnotationCandidates = new ArrayList<>();
            }
            withInAnnotationCandidates.add(included);
            return this;
        }

        public CallSolution<A> checkParameter(BiConsumer<A, Cartrofit.Key> keyConsumer) {
            keyGrammarChecker = keyConsumer;
            return this;
        }

        public CallSolution<A> checkParameter(Class<? extends Annotation> clazz) {
            return checkParameter(clazz, null);
        }

        public CallSolution<A> checkParameter(Class<? extends Annotation> clazz, Class<?> fixedType) {
            if (fixedType != null) {
                if (withAnnotationTypeMap == null) {
                    withAnnotationTypeMap = new HashMap<>();
                }
                withAnnotationTypeMap.put(clazz, fixedType);
            }
            if (withAnnotationCandidates == null) {
                withAnnotationCandidates = new ArrayList<>();
            }
            withAnnotationCandidates.add(clazz);
            return this;
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

        boolean hasCategory(int category) {
            return (expectedCategory & category) != 0;
        }

        Class<? extends Annotation> getGrammarContext() {
            return candidateClass;
        }

        void checkParameterGrammar(Annotation annotation, Cartrofit.Key key) {
            if (withAnnotationCandidates != null) {
                for (int i = 0; i < withAnnotationCandidates.size(); i++) {
                    Class<? extends Annotation> annotationClass = withAnnotationCandidates.get(i);
                    if (!checkParameterDeclared(key, annotationClass)) {
                        throw new CartrofitGrammarException("Can not find parameter with annotation:"
                                + annotationClass + " on:" + key);
                    }
                }
            }
            if (withInAnnotationCandidates != null) {
                for (int i = 0; i < withInAnnotationCandidates.size(); i++) {
                    for (Class<? extends Annotation> annotationClass : withInAnnotationCandidates.get(i)) {
                        if (checkParameterDeclared(key, annotationClass)) {
                            break;
                        }
                        throw new CartrofitGrammarException("Can not find parameter with annotation:"
                                + Arrays.toString(withInAnnotationCandidates.get(i)) + " on:" + key);
                    }
                }
            }
            if (keyGrammarChecker != null) {
                keyGrammarChecker.accept((A) annotation, key);
            }
        }

        private boolean checkParameterDeclared(Cartrofit.Key key, Class<? extends Annotation> annotationClass) {
            for (int i = 0; i < key.getParameterCount(); i++) {
                Cartrofit.Parameter parameter = key.getParameterAt(i);
                if (parameter.isAnnotationPresent(annotationClass)) {
                    Class<?> expectedType = withAnnotationTypeMap != null ?
                            withAnnotationTypeMap.get(annotationClass) : null;
                    if (expectedType == null
                            || Cartrofit.classEquals(parameter.getType(), expectedType)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void getAnnotationCandidates(int category, ArrayList<CallSolution<?>> grammarRule) {
            if (predictor.test(category)) {
                grammarRule.add(this);
            }
        }
    }

    void init(Cartrofit cartrofit) {
        mCartrofit = cartrofit;
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

    void collectGrammarRules(int category, ArrayList<CallSolution<?>> grammarRule) {
        for (int i = 0; i < mCallSolutionList.size(); i++) {
            mCallSolutionList.get(i).getAnnotationCandidates(category, grammarRule);
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