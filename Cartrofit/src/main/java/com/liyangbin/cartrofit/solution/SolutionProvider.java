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
import java.util.function.IntPredicate;

@SuppressWarnings("unchecked")
public final class SolutionProvider {

    private static final Function<Object[], Object> sDummyInputConverter = value -> value != null && value.length > 0 ? value[0] : null;
    private static final Function<Object, Object[]> sDummyOutputConverter = o -> new Object[]{o};

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

    public <INPUT> Function<Object[], INPUT> findInputConverter(FixedTypeCall<INPUT, ?> call) {
        ConvertSolution<INPUT, ?, ?> builder = (ConvertSolution<INPUT, ?, ?>) mConverterInputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkIn(call.getBindingParameter());
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
            CallSolution<?> solution = mCallSolutionList.get(i);
            Call call = solution.createCall(context, category, key);
            if (call != null) {
                return call;
            }
        }
        return null;
    }

    public interface CallProvider<A extends Annotation, T extends Call> {
        T provide(CartrofitContext context, int category, A annotation, Key key);
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

        private Call createCall(CartrofitContext context, int category, Key key) {
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
