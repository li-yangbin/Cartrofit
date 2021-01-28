package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.MethodCategory;
import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.IntPredicate;

@SuppressWarnings("unchecked")
public final class SolutionProvider {

    private static final Converter<Union, Object> sDummyInputConverter = value -> value.getCount() > 0 ? value.get(0) : null;
    private static final Converter<Object, Union> sDummyOutputConverter = Union::of;

    private final ArrayList<CallSolution<?>> mCallSolutionList = new ArrayList<>();
    private final HashMap<Class<?>, ConvertSolution<?, ?, ?>> mConverterInputMap = new HashMap<>();
    private final HashMap<Class<?>, ConvertSolution<?, ?, ?>> mConverterOutputMap = new HashMap<>();

    public <A extends Annotation> CallSolution<A> create(Class<A> annotationClass) {
        return new CallSolution<>(annotationClass);
    }

    boolean isEmpty() {
        return mCallSolutionList.size() == 0;
    }

    void commitInputConverter(Class<?> callType, ConvertSolution<?, ?, ?> builder) {
        mConverterInputMap.put(callType, builder);
    }

    void commitOutputConverter(Class<?> callType, ConvertSolution<?, ?, ?> builder) {
        mConverterOutputMap.put(callType, builder);
    }

    <INPUT> Converter<Union, INPUT> findInputConverter(FixedTypeCall<INPUT, ?> call) {
        ConvertSolution<INPUT, ?, ?> builder = (ConvertSolution<INPUT, ?, ?>) mConverterInputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkIn(call.getBindingParameter());
        } else {
            return (Converter<Union, INPUT>) sDummyInputConverter;
        }
    }

    <OUTPUT> Converter<OUTPUT, ?> findReturnOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        ConvertSolution<?, OUTPUT, ?> builder = (ConvertSolution<?, OUTPUT, ?>) mConverterOutputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkOutReturn(call.getKey());
        } else {
            return null;
        }
    }

    <OUTPUT> Converter<OUTPUT, Union> findCallbackOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        ConvertSolution<?, OUTPUT, ?> builder = (ConvertSolution<?, OUTPUT, ?>) mConverterOutputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkOutCallback(call.getBindingParameter());
        } else {
            return (Converter<OUTPUT, Union>) sDummyOutputConverter;
        }
    }

    Call createCall(Context context, Key key, int category) {
        for (int i = mCallSolutionList.size() - 1; i >= 0; i--) {
            CallSolution<?> solution = mCallSolutionList.get(i);
            Call call = solution.createCall(context, category, key);
            if (call != null) {
                return call;
            }
        }
        return null;
    }

    public interface CallProvider<A extends Annotation, T extends Call> {
        T provide(Context context, int category, A annotation, Key key);
    }

    public SolutionProvider merge(SolutionProvider solutionProvider) {
        if (solutionProvider == null || solutionProvider.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return solutionProvider;
        }

        final SolutionProvider newProvider = new SolutionProvider();

        newProvider.mCallSolutionList.addAll(mCallSolutionList);
        newProvider.mCallSolutionList.addAll(solutionProvider.mCallSolutionList);

        newProvider.mConverterInputMap.putAll(mConverterInputMap);
        newProvider.mConverterInputMap.putAll(solutionProvider.mConverterInputMap);

        newProvider.mConverterOutputMap.putAll(mConverterOutputMap);
        newProvider.mConverterOutputMap.putAll(solutionProvider.mConverterOutputMap);

        return newProvider;
    }

    public class CallSolution<A extends Annotation> {

        IntPredicate predictor;
        Class<A> candidateClass;
        int expectedCategory;
        CallProvider<A, ?> provider;

        CallSolution(Class<A> candidateClass) {
            this.candidateClass = candidateClass;
            MethodCategory category = candidateClass.getDeclaredAnnotation(MethodCategory.class);
            if (category != null) {
                expectedCategory = category.value();
                if (expectedCategory == MethodCategory.CATEGORY_DEFAULT) {
//                    keepLookingIfNull = true;
                    predictor = flag -> true;
                } else {
                    predictor = flag -> (flag & expectedCategory) != 0;
                }
            } else {
                throw new CartrofitGrammarException("Must declare Category attribute on annotation:"
                        + candidateClass);
            }
        }

        public <T extends Call> void provide(CallProvider<A, T> provider) {
            this.provider = provider;
            mCallSolutionList.add(this);
        }

        public <INPUT, OUTPUT, T extends FixedTypeCall<INPUT, OUTPUT>> ConvertSolution<INPUT, OUTPUT, A>
                provideAndBuildParameter(Class<T> callType, CallProvider<A, T> provider) {
            this.provider = provider;
            mCallSolutionList.add(this);
            if (mConverterInputMap.containsKey(callType) || mConverterOutputMap.containsKey(callType)) {
                throw new CartrofitGrammarException("There is a parameter solution exists already");
            }
            return new ConvertSolution<>(callType, SolutionProvider.this);
        }

        private Call createCall(Context context, int category, Key key) {
            if (predictor.test(category)) {
                A annotation = key.getAnnotation(candidateClass);
                if (annotation != null) {
                    Call call = provider.provide(context, category, annotation, key);
                    call.addCategory(expectedCategory);
                    ConvertSolution<?, ?, ?> convertSolution = mConverterInputMap.get(call.getClass());
                    if (convertSolution != null) {
                        convertSolution.checkInputParameterGrammarIfNeeded(key,
                                call.getBindingParameter());
                    }
                    return call;
                }
            }
            return null;
        }
    }
}
