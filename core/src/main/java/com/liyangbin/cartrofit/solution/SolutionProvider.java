package com.liyangbin.cartrofit.solution;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CartrofitContext;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.FixedTypeCall;
import com.liyangbin.cartrofit.Key;
import com.liyangbin.cartrofit.annotation.MethodCategory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public final class SolutionProvider {

    private static final Function<Object[], Object> sDummyInputConverter = value -> value != null && value.length > 0 ? value[0] : null;
    private static final Function<Object, Object[]> sDummyOutputConverter = o -> new Object[]{o};

    private final ArrayList<CallSolution<?, ?>> mCallSolutionList = new ArrayList<>();
    private final HashMap<Class<?>, ConvertSolution<?, ?, ?>> mConverterInputMap = new HashMap<>();
    private final HashMap<Class<?>, ConvertSolution<?, ?, ?>> mConverterOutputMap = new HashMap<>();

    public <A extends Annotation, T extends Call> CallSolution<A, T> create(Class<A> annotationClass,
                                                                            Class<T> callType) {
        return new CallSolution<>(annotationClass, callType);
    }

    public <A extends Annotation, INPUT, OUTPUT, T extends FixedTypeCall<INPUT, OUTPUT>>
        FixedTypeCallSolution<A, INPUT, OUTPUT, T> createWithFixedType(Class<A> annotationClass,
                                                                       Class<T> callType) {
        return new FixedTypeCallSolution<>(annotationClass, callType);
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

    public <INPUT> Function<Object[], INPUT> findInputConverter(FixedTypeCall<INPUT, ?> call) {
        ConvertSolution<INPUT, ?, Annotation> builder =
                (ConvertSolution<INPUT, ?, Annotation>) mConverterInputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkIn(call.getBindingParameter(), call);
        } else {
            return (Function<Object[], INPUT>) sDummyInputConverter;
        }
    }

    public <OUTPUT> Function<OUTPUT, ?> findReturnOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        ConvertSolution<?, OUTPUT, ?> builder = (ConvertSolution<?, OUTPUT, ?>) mConverterOutputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkOutReturn(call.getKey());
        } else {
            return null;
        }
    }

    public <OUTPUT> Function<OUTPUT, Object[]> findCallbackOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        ConvertSolution<?, OUTPUT, ?> builder = (ConvertSolution<?, OUTPUT, ?>) mConverterOutputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkOutCallback(call.getKey().getImplicitParameterGroup());
        } else {
            return (Function<OUTPUT, Object[]>) sDummyOutputConverter;
        }
    }

    public Call createCall(CartrofitContext context, Key key, int category) {
        for (int i = mCallSolutionList.size() - 1; i >= 0; i--) {
            CallSolution<?, ?> solution = mCallSolutionList.get(i);
            Call call = solution.createCall(context, category, key);
            if (call != null) {
                return call;
            }
        }
        return null;
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

    public class CallSolution<A extends Annotation, T extends Call> {

        Class<A> candidateClass;
        Class<T> callType;
        int expectedCategory;
        CallProvider<A, ?> provider;

        CallSolution(Class<A> candidateClass, Class<T> callType) {
            this.candidateClass = candidateClass;
            this.callType = callType;
            MethodCategory category = candidateClass.getDeclaredAnnotation(MethodCategory.class);
            if (category != null) {
                expectedCategory = category.value();
            } else {
                throw new CartrofitGrammarException("Must declare Category attribute on annotation:"
                        + candidateClass);
            }
        }

        public void provide(CallProvider<A, T> provider) {
            this.provider = provider;
            mCallSolutionList.add(this);
        }

        private Call createCall(CartrofitContext context, int category, Key key) {
            if ((category & expectedCategory) != 0) {
                A annotation = key.getAnnotation(candidateClass);
                if (annotation != null) {
                    Call call;
                    if (provider instanceof CallProvider2) {
                        call = ((CallProvider2) provider).provide(context, category, annotation, key);
                    } else {
                        call = provider.provide(annotation, key);
                    }
                    call.setCategoryAndAnnotation(expectedCategory, annotation);
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

    public class FixedTypeCallSolution<A extends Annotation, INPUT, OUTPUT,
            T extends FixedTypeCall<INPUT, OUTPUT>> extends CallSolution<A, T> {

        FixedTypeCallSolution(Class<A> candidateClass, Class<T> callType) {
            super(candidateClass, callType);
        }

        public ConvertSolution<INPUT, OUTPUT, A> provideAndBuildParameter(CallProvider<A, T> provider) {
            this.provider = provider;
            mCallSolutionList.add(this);
            if (mConverterInputMap.containsKey(callType) || mConverterOutputMap.containsKey(callType)) {
                throw new CartrofitGrammarException("There is a parameter solution exists already");
            }
            return new ConvertSolution<>(callType, SolutionProvider.this);
        }
    }
}
