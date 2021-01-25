package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.MethodCategory;
import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
    private final HashMap<Class<?>, ConverterBuilder<?, ?, ?>> mConverterInputMap = new HashMap<>();
    private final HashMap<Class<?>, ConverterBuilder<?, ?, ?>> mConverterOutputMap = new HashMap<>();
    private final Converter<Union, Object> mDummyInputConverter = value -> value.getCount() > 0 ? value.get(0) : null;
    private final Converter<Object, Union> mDummyOutputConverter = Union::of;

    public final class CallSolutionBuilder {

        private CallSolutionBuilder() {
        }

        public <A extends Annotation> CallSolution<A> create(Class<A> annotationClass) {
            return new CallSolution<>(annotationClass);
        }
    }

    public final Call createChildCall(Cartrofit.Key key, int category) {
        return mCartrofit.getOrCreateCall(key.record, key, category, false);
    }

    public final Call getOrCreateCallById(Cartrofit.Key key, int id, int category, boolean fromDelegate) {
        return mCartrofit.getOrCreateCallById(key, key.record, id, category, fromDelegate);
    }

    public final <T> void inflateCallback(Cartrofit.Key key, Class<?> callbackClass,
                                          int category, Consumer<Call> resultReceiver) {
        ArrayList<Cartrofit.Key> childKeys = getChildKey(key, callbackClass);
        for (int i = 0; i < childKeys.size(); i++) {
            Cartrofit.Key childKey = childKeys.get(i);
            Call call = mCartrofit.getOrCreateCall(childKey.record, childKey, category, false);
            if (call != null) {
                resultReceiver.accept(call);
            }
        }
    }

    public interface CallProvider<A extends Annotation, T extends Call> {
        T provide(int category, A annotation, Cartrofit.Key key);
    }

    public final <INPUT> Converter<Union, INPUT> findInputConverter(FixedTypeCall<INPUT, ?> call) {
        ConverterBuilder<INPUT, ?, ?> builder = (ConverterBuilder<INPUT, ?, ?>) mConverterInputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkIn(call.getParameterContext()
                    .extractParameterFromCall(call));
        } else {
            return (Converter<Union, INPUT>) mDummyInputConverter;
        }
    }

    public final <OUTPUT> Converter<OUTPUT, Union> findCallbackOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        ConverterBuilder<?, OUTPUT, ?> builder = (ConverterBuilder<?, OUTPUT, ?>) mConverterOutputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkOutCallback(call.getParameterContext()
                    .extractParameterFromCall(call));
        } else {
            return (Converter<OUTPUT, Union>) mDummyOutputConverter;
        }
    }

    public FlowConverter<?> findFlowConverter(Call call) {
        if (call.getKey().isCallbackEntry) {
            return null;
        }
        return Cartrofit.findFlowConverter(call.getKey().getReturnType());
    }

    public final <OUTPUT> Converter<OUTPUT, ?> findReturnOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        ConverterBuilder<?, OUTPUT, ?> builder = (ConverterBuilder<?, OUTPUT, ?>) mConverterOutputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkOutReturn(call.getKey());
        } else {
            return null;
        }
    }

    public ArrayList<Cartrofit.Key> getChildKey(Cartrofit.Key parent, Class<?> callbackClass) {
        return mCartrofit.getApi(callbackClass).getChildKey(parent);
    }

    public final class CallSolution<A extends Annotation> {
        IntPredicate predictor;
        Class<A> candidateClass;
        int expectedCategory;
        CallProvider<A, ?> provider;
        BiConsumer<A, Cartrofit.Key> keyGrammarChecker;
        List<Class<? extends Annotation>[]> withInAnnotationCandidates;
        List<Class<? extends Annotation>> withAnnotationCandidates;
        HashMap<Class<? extends Annotation>, Class<?>> withAnnotationTypeMap;
        boolean keepLookingIfNull;

        CallSolution(Class<A> candidateClass) {
            this.candidateClass = candidateClass;
            MethodCategory category = candidateClass.getDeclaredAnnotation(MethodCategory.class);
            if (category != null) {
                expectedCategory = category.value();
                if (expectedCategory == MethodCategory.CATEGORY_DEFAULT) {
                    keepLookingIfNull = true;
                    predictor = flag -> true;
                } else {
                    predictor = flag -> (flag & expectedCategory) != 0;
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

        public <INPUT, OUTPUT, T extends FixedTypeCall<INPUT, OUTPUT>>
                ConverterBuilder<INPUT, OUTPUT, A> buildParameter(Class<T> callType) {
            if (mConverterInputMap.containsKey(callType) || mConverterOutputMap.containsKey(callType)) {
                throw new CartrofitGrammarException("There is a parameter solution exists already");
            }
            return new ConverterBuilder<>(callType, this);
        }

        void commitInputConverter(Class<?> callType, ConverterBuilder<?, ?, ?> builder) {
            mConverterInputMap.put(callType, builder);
        }

        void commitOutputConverter(Class<?> callType, ConverterBuilder<?, ?, ?> builder) {
            mConverterOutputMap.put(callType, builder);
        }

        public <T extends Call> void provide(CallProvider<A, T> provider) {
            this.provider = provider;
            mCallSolutionList.add(this);
        }

        private Call createCall(int category, Cartrofit.Key key) {
            if (predictor.test(category)) {
                A annotation = key.getAnnotation(candidateClass);
                if (annotation != null) {
                    Call call = provider.provide(category, annotation, key);
                    if (call != null) {
                        call.addCategory(expectedCategory);
                    }
                    return call;
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

    public Executor getSubscribeExecutor(String tag) {
        return null;
    }

    public Executor getConsumeExecutor(String tag) {
        return null;
    }

    void init(Cartrofit cartrofit) {
        mCartrofit = cartrofit;
        onProvideCallSolution(new CallSolutionBuilder());
    }

    public abstract Object extractScope(Class<?> scopeClass);

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
}
