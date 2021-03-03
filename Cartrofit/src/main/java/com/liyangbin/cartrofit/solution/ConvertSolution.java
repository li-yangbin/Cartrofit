package com.liyangbin.cartrofit.solution;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CartrofitContext;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.FixedTypeCall;
import com.liyangbin.cartrofit.Key;
import com.liyangbin.cartrofit.Parameter;
import com.liyangbin.cartrofit.ParameterGroup;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class ConvertSolution<IN, OUT, A extends Annotation> {

    private BiFunction<A, Key, IN> inputProvider;
    private final ArrayList<AbsParameterSolution> forwardSolutions = new ArrayList<>();
    private final ArrayList<AbsParameterSolution> backwardSolutions = new ArrayList<>();
    private final Class<?> callType;
    private final SolutionProvider caller;
    private final AbsParameterSolution plainOutSolution = takeAny().output((a, old, para) -> {
        para.set(old);
        return old;
    });

    ConvertSolution(Class<? extends FixedTypeCall<IN, OUT>> callType, SolutionProvider caller) {
        this.callType = callType;
        this.caller = caller;
    }

    private void commitSolution(AbsParameterSolution solution) {
        boolean checkTypeIndeterminate = solution.typeIndeterminate;
        if (solution.hasForward()) {
            boolean inserted = false;
            for (int i = 0; i < forwardSolutions.size(); i++) {
                AbsParameterSolution solutionSaved = forwardSolutions.get(i);
                if (checkTypeIndeterminate && solutionSaved.typeIndeterminate) {
                    throw new CartrofitGrammarException("Can only provide one free-form type solution");
                }
                if (solutionSaved.size() > solution.size()) {
                    forwardSolutions.add(i, solution);
                    inserted = true;
                    break;
                }
            }
            if (!inserted) {
                forwardSolutions.add(solution);
            }
        }

        if (solution.hasBackward()) {
            boolean inserted = false;
            for (int i = 0; i < backwardSolutions.size(); i++) {
                AbsParameterSolution solutionSaved = backwardSolutions.get(i);
                if (checkTypeIndeterminate && solutionSaved.typeIndeterminate) {
                    throw new CartrofitGrammarException("Can only provide one free-form type solution");
                }
                if (backwardSolutions.get(i).size() > solution.size()) {
                    backwardSolutions.add(i, solution);
                    inserted = true;
                    break;
                }
            }
            if (!inserted) {
                backwardSolutions.add(solution);
            }
        }
    }

    private <T> void findSolutionDependency(boolean input, ParameterGroup group, SolutionRecordKeeper<T> resultReceiver) {
        final int paraCount = group.getParameterCount();
        if (paraCount == 0) {
            return;
        }
        boolean[] occupy = new boolean[paraCount];
        int occupyExpected = paraCount;
        final ArrayList<AbsParameterSolution> solutions = input ? forwardSolutions : backwardSolutions;
        for (int i = solutions.size() - 1; i >= 0 && occupyExpected != 0; i--) {
            AbsParameterSolution solution = solutions.get(i);

            if (solution.indeterminateMode) {
                for (int j = 0; j < paraCount; j++) {
                    int span = 0;
                    if (!occupy[j] && solution.isInterestedToStart(group.getParameterAt(j))) {
                        span++;
                        for (int k = j + 1; k < paraCount; k++) {
                            if (occupy[k] || !solution.isInterestedWithoutAnnotation(group.getParameterAt(k))) {
                                break;
                            }
                            span++;
                        }
                        resultReceiver.assembleSolution(solution, j, span);
                        occupyExpected -= span;
                        Arrays.fill(occupy, j, j + span, true);
                    }
                }
            } else {
                final int solutionSize = solution.size();
                anchor: for (int j = 0; j <= paraCount - solutionSize; j++) {
                    for (int k = j; k < j + solutionSize; k++) {
                        if (occupy[k]) {
                            continue anchor;
                        }
                    }
                    if (solution.isInterestedToStart(group.getParameterAt(j))) {
                        for (int k = j + 1; k < j + solutionSize; k++) {
                            if (!solution.isInterestedWithoutAnnotation(group.getParameterAt(k))) {
                                continue anchor;
                            }
                        }

                        resultReceiver.assembleSolution(solution, j, solutionSize);
                        occupyExpected -= solutionSize;
                        Arrays.fill(occupy, j, j + solutionSize, true);
                    }
                }
            }
        }

        if (occupyExpected == 1 && !input) {
            for (int i = 0; i < occupy.length; i++) {
                if (!occupy[i] && group.getParameterAt(i).hasNoAnnotation()) {
                    resultReceiver.assembleSolution(plainOutSolution, i, 1);
                    occupyExpected--;
                    occupy[i] = true;
                }
            }
        }

        if (occupyExpected != 0) {
            throw new CartrofitGrammarException("Invalid parameter declaration " + group.getDeclaredKey());
        }
    }

    Function<Object[], IN> checkIn(ParameterGroup group, Call call) {
        InputConverterImpl inputConverter = new InputConverterImpl(group, call);
        findSolutionDependency(true, group, inputConverter);
        return inputConverter;
    }

    Function<OUT, Object[]> checkOutCallback(ParameterGroup group) {
        OutputConverterImpl outputConverter = new OutputConverterImpl(group);
        findSolutionDependency(false, group, outputConverter);
        return outputConverter;
    }

    Function<OUT, Object> checkOutReturn(Key key) {
        Parameter returnParameter = key.getReturnAsParameter();
        if (returnParameter == null) {
            return null;
        }
        for (int i = 0; i < backwardSolutions.size(); i++) {
            AbsParameterSolution solution = backwardSolutions.get(i);
            if (solution.size() == 1 && solution.isInterestedToStart(returnParameter)) {
                return new ReturnConverterImpl(solution, returnParameter);
            }
        }
        return null;
    }

    void checkInputParameterGrammarIfNeeded(Key checkTarget, ParameterGroup group) {
        if (!checkTarget.isCallbackEntry) {
            for (int i = 0; i < forwardSolutions.size(); i++) {
                AbsParameterSolution solution = forwardSolutions.get(i);
                if (solution.necessaryCheck && (solution.inputTogether != null || solution.inputBridge != null)) {
                    for (int j = 0; j < group.getParameterCount(); j++) {
                        if (solution.isInterestedToStart(group.getParameterAt(j))) {
                            return;
                        }
                    }
                    throw new CartrofitGrammarException("Grammar check failed:" + solution
                            + " can not be resolved from:" + checkTarget);
                }
            }
        }
    }

    public ConvertSolution<IN, OUT, A> provideBasic(BiFunction<A, Key, IN> inputProvider) {
        this.inputProvider = inputProvider;
        return ConvertSolution.this;
    }

    public <FROM> ParameterSolution1<Annotation, FROM> take(Class<FROM> clazz) {
        return new ParameterSolution1<>(clazz, null);
    }

    public <PA extends Annotation, FROM> ParameterSolution1<PA, FROM>
            takeWithAnnotation(Class<FROM> clazz, Class<PA> parameterAnnotationType) {
        return new ParameterSolution1<>(clazz, parameterAnnotationType);
    }

    public ParameterSolution1<Annotation, Object> takeAny() {
        return new ParameterSolution1<>(Object.class, null);
    }

    public <PA extends Annotation> ParameterSolution1<PA, Object>
            takeAnyWithAnnotation(Class<PA> parameterAnnotationType) {
        return new ParameterSolution1<>(Object.class, parameterAnnotationType);
    }

    private void commitParameter() {
        if (forwardSolutions.size() > 0) {
            caller.commitInputConverter(callType, this);
        }
        if (backwardSolutions.size() > 0) {
            caller.commitOutputConverter(callType, this);
        }
    }

    abstract class AbsParameterSolution<PA extends Annotation, T> {
        AbsParameterSolution<PA, ?> parent;
        Class<T> fixedType;
        boolean typeIndeterminate;

        Class<PA> fixedAnnotationType;
        Predicate<Parameter> extraCheck;

        boolean markedAsTogetherHead;
        boolean indeterminateMode;
        boolean necessaryCheck;

        AbsAccumulator<PA, ParaVal[], IN> inputBridge;
        AbsAccumulator<PA, ?, IN> inputTogether;

        AbsAccumulator<PA, ParaVal[], OUT> outputBridge;
        AbsAccumulator<PA, ?, OUT> outputTogether;

        AbsParameterSolution(AbsParameterSolution<PA, ?> parent, Class<T> fixedType,
                             Class<PA> annotationType) {
            this.parent = parent;
            this.fixedType = fixedType;
            this.fixedAnnotationType = annotationType;
        }

        abstract int size();

        boolean hasForward() {
            return inputBridge != null || inputTogether != null;
        }

        boolean hasBackward() {
            return outputBridge != null || outputTogether != null;
        }

        boolean isInterestedToStart(Parameter parameter) {
            Class<?> declaredType = parameter.getType();
            declaredType = CartrofitContext.boxTypeOf(declaredType);
            if (fixedType.isAssignableFrom(declaredType)) {
                if (inputBridge != null || outputBridge != null || markedAsTogetherHead) {
                    return (fixedAnnotationType == null
                            || parameter.isAnnotationPresent(fixedAnnotationType))
                            && (extraCheck == null || extraCheck.test(parameter));
                }
            }
            return false;
        }

        boolean isInterestedWithoutAnnotation(Parameter parameter) {
            return fixedType == parameter.getType() && parameter.hasNoAnnotation();
        }

        public final ConvertSolution<IN, OUT, A> build() {
            if (parent == null && fixedAnnotationType == null) {
                typeIndeterminate = fixedType == Object.class;
            }
            commitSolution(this);
            return parent != null ? parent.build() : ConvertSolution.this;
        }

        public final void buildAndCommit() {
            build().commitParameter();
        }
    }

    public class ParameterSolution1<PA extends Annotation, T> extends AbsParameterSolution<PA, T> {

        ParameterSolution1(Class<T> fixedType, Class<PA> paraAnnotationType) {
            super(null, fixedType, paraAnnotationType);
        }

        @Override
        int size() {
            return 1;
        }

        public ParameterSolution1<PA, T> input(Accumulator<PA, T, IN> inputBridge) {
            this.inputBridge = inputBridge;
            return this;
        }

        public ParameterSolution1<PA, T> output(Accumulator<PA, T, OUT> outputBridge) {
            this.outputBridge = outputBridge;
            return this;
        }

        public ParameterSolution1<PA, T> forwardIndeterminate(AccumulatorIndeterminate<PA, T, IN> forward) {
            this.inputBridge = forward;
            this.indeterminateMode = true;
            return this;
        }

        public ParameterSolution1<PA, T> backwardIndeterminate(AccumulatorIndeterminate<PA, T, OUT> backward) {
            this.outputBridge = backward;
            this.indeterminateMode = true;
            return this;
        }

        public <T2> ParameterSolution2<PA, T, T2> and(Class<T2> fromClazz) {
            if (indeterminateMode) {
                throw new CartrofitGrammarException("Can not add other types in indeterminate mode");
            }
            markedAsTogetherHead = true;
            return new ParameterSolution2<>(this, fromClazz);
        }
    }

    public class ParameterSolution2<PA extends Annotation, T1, T2> extends AbsParameterSolution<PA, T2> {

        ParameterSolution2(ParameterSolution1<PA, T1> solutionBase, Class<T2> type) {
            super(solutionBase, type, null);
        }

        @Override
        int size() {
            return 2;
        }

        public ParameterSolution2<PA, T1, T2> inputTogether(Accumulator2<PA, T1, T2, IN> forward) {
            this.inputTogether = forward;
            return this;
        }

        public ParameterSolution2<PA, T1, T2> outputTogether(Accumulator2<PA, T1, T2, OUT> backward) {
            this.outputTogether = backward;
            return this;
        }

        public <T3> ParameterSolution3<PA, T1, T2, T3> and(Class<T3> fromClazz) {
            return new ParameterSolution3<>(this, fromClazz);
        }
    }

    public class ParameterSolution3<PA extends Annotation, T1, T2, T3> extends AbsParameterSolution<PA, T3> {

        ParameterSolution3(ParameterSolution2<PA, T1, T2> solutionBase, Class<T3> type) {
            super(solutionBase, type, null);
        }

        @Override
        int size() {
            return 3;
        }

        public ParameterSolution3<PA, T1, T2, T3> inputTogether(Accumulator3<PA, T1, T2, T3, IN> forward) {
            this.inputTogether = forward;
            return this;
        }

        public ParameterSolution3<PA, T1, T2, T3> outputTogether(Accumulator3<PA, T1, T2, T3, OUT> backward) {
            this.outputTogether = backward;
            return this;
        }

        public <T4> ParameterSolution4<PA, T1, T2, T3, T4> and(Class<T4> fromClazz) {
            return new ParameterSolution4<>(this, fromClazz);
        }
    }

    public class ParameterSolution4<PA extends Annotation, T1, T2, T3, T4> extends AbsParameterSolution<PA, T4> {

        ParameterSolution4(ParameterSolution3<PA, T1, T2, T3> solutionBase, Class<T4> type) {
            super(solutionBase, type, null);
        }

        @Override
        int size() {
            return 4;
        }

        public ParameterSolution4<PA, T1, T2, T3, T4> inputTogether(Accumulator4<PA, T1, T2, T3, T4, IN> forward) {
            this.inputTogether = forward;
            return this;
        }

        public ParameterSolution4<PA, T1, T2, T3, T4> outputTogether(Accumulator4<PA, T1, T2, T3, T4, OUT> backward) {
            this.outputTogether = backward;
            return this;
        }

        public <T5> ParameterSolution5<PA, T1, T2, T3, T4, T5> and(Class<T5> fromClazz) {
            return new ParameterSolution5<>(this, fromClazz);
        }
    }

    public class ParameterSolution5<PA extends Annotation, T1, T2, T3, T4, T5> extends AbsParameterSolution<PA, T5> {

        ParameterSolution5(ParameterSolution4<PA, T1, T2, T3, T4> solutionBase, Class<T5> type) {
            super(solutionBase, type, null);
        }

        @Override
        int size() {
            return 5;
        }

        public ParameterSolution5<PA, T1, T2, T3, T4, T5> inputTogether(Accumulator5<PA, T1, T2, T3, T4, T5, IN> forward) {
            this.inputTogether = forward;
            return this;
        }

        public ParameterSolution5<PA, T1, T2, T3, T4, T5> outputTogether(Accumulator5<PA, T1, T2, T3, T4, T5, OUT> backward) {
            this.outputTogether = backward;
            return this;
        }
    }

    private class SolutionRecord {
        AbsParameterSolution solution;
        Annotation annotation;
        int start;
        int length;

        SolutionRecord(AbsParameterSolution solution, Annotation annotation, int start, int length) {
            this.solution = solution;
            this.annotation = annotation;
            this.start = start;
            this.length = length;
        }
    }

    private abstract class SolutionRecordKeeper<SERIALIZATION> {
        ParameterGroup parameterGroup;
        ArrayList<SolutionRecord> solutionRecords = new ArrayList<>();
        boolean input;

        SolutionRecordKeeper(boolean input, ParameterGroup parameterGroup) {
            this.input = input;
            this.parameterGroup = parameterGroup;
        }

        void assembleSolution(AbsParameterSolution solution, int slotStart, int slotLength) {
            Annotation annotation = solution.fixedAnnotationType != null ?
                    parameterGroup.getParameterAt(slotStart)
                            .getAnnotation(solution.fixedAnnotationType) : null;
            solutionRecords.add(new SolutionRecord(solution, annotation, slotStart, slotLength));
        }

        @SuppressWarnings("unchecked")
        final SERIALIZATION assemble(SERIALIZATION rawInput, Object[] avengers) {
            for (int i = 0; i < solutionRecords.size(); i++) {
                SolutionRecord record = solutionRecords.get(i);
                if (record.length > 1 || record.solution.indeterminateMode) {
                    ParaVal[] array = new ParaVal[record.length];
                    for (int j = 0; j < array.length; j++) {
                        array[j] = onCreateAccessibleParameter(record.start + j, avengers);
                    }
                    AbsAccumulator<Annotation, ParaVal[], SERIALIZATION> accumulator =
                            (AbsAccumulator<Annotation, ParaVal[], SERIALIZATION>)
                            (input ? record.solution.inputTogether : record.solution.outputTogether);
                    rawInput = accumulator.advance(record.annotation, rawInput, array);
                    // TODO： warning if user does not call set
                } else {
                    Accumulator<Annotation, Object, SERIALIZATION> accumulator
                            = (Accumulator<Annotation, Object, SERIALIZATION>)
                            (input ? record.solution.inputBridge : record.solution.outputBridge);
                    rawInput = accumulator.advance(record.annotation, rawInput,
                            onCreateAccessibleParameter(record.start, avengers));
                }
            }
            return rawInput;
        }

        abstract AccessibleParameter onCreateAccessibleParameter(int index, Object[] parameterHost);

        abstract class AccessibleParameter implements ParaVal<Object> {
            int index;
            Object[] parameterHost;

            AccessibleParameter(int index, Object[] parameterHost) {
                this.index = index;
                this.parameterHost = parameterHost;
            }

            @Override
            public Parameter getParameter() {
                return parameterGroup.getParameterAt(index);
            }

            @Override
            public Object get() {
                return parameterHost[index];
            }

            @Override
            public void set(Object value) {
                parameterHost[index] = value;
            }
        }
    }

    private class OutputConverterImpl extends SolutionRecordKeeper<OUT> implements Function<OUT, Object[]> {

        OutputConverterImpl(ParameterGroup parameterGroup) {
            super(false, parameterGroup);
        }

        @Override
        AccessibleParameter onCreateAccessibleParameter(int index, Object[] parameterHost) {
            return new WritableParameter(index, parameterHost);
        }

        class WritableParameter extends AccessibleParameter {

            WritableParameter(int index, Object[] parameterHost) {
                super(index, parameterHost);
            }

            @Override
            public Object get() {
                throw new UnsupportedOperationException("Invalid call");
            }
        }

        @Override
        public Object[] apply(OUT rawData) {
            final int count = parameterGroup.getParameterCount();
            if (count == 0) {
                return null;
            }
            Object[] parameterHost = new Object[count];
            assemble(rawData, parameterHost);
            return parameterHost;
        }
    }

    private class InputConverterImpl extends SolutionRecordKeeper<IN> implements Function<Object[], IN> {

        private Call call;

        InputConverterImpl(ParameterGroup parameterGroup, Call call) {
            super(true, parameterGroup);
            this.call = call;
        }

        @Override
        AccessibleParameter onCreateAccessibleParameter(int index, Object[] parameterHost) {
            return new ReadableParameter(index, parameterHost);
        }

        class ReadableParameter extends AccessibleParameter {

            ReadableParameter(int index, Object[] parameterHost) {
                super(index, parameterHost);
            }

            @Override
            public void set(Object value) {
                throw new UnsupportedOperationException("Invalid call");
            }
        }

        @Override
        public IN apply(Object[] parameterInput) {
            IN rawInput = inputProvider != null ? inputProvider
                    .apply((A) call.getAnnotation(), call.getKey()) : null;
            final int count = parameterGroup.getParameterCount();
            if (count == 0) {
                return rawInput;
            }
            return assemble(rawInput, parameterInput);
        }
    }

    private class ReturnConverterImpl implements Function<OUT, Object>, ParaVal<Object> {

        AbsParameterSolution targetSolution;
        Parameter returnAsParameter;
        Object tmpResult;

        ReturnConverterImpl(AbsParameterSolution targetSolution, Parameter returnAsParameter) {
            this.targetSolution = targetSolution;
            this.returnAsParameter = returnAsParameter;
        }

        @Override
        public Object apply(OUT value) {
            Accumulator<Annotation, Object, OUT> accumulator
                    = (Accumulator<Annotation, Object, OUT>) targetSolution.outputBridge;
            synchronized (this) {
                accumulator.advance(null, value, this);
                Object result = tmpResult;
                tmpResult = null;
                return result;
            }
        }

        @Override
        public Parameter getParameter() {
            return returnAsParameter;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("Invalid call");
        }

        @Override
        public void set(Object value) {
            tmpResult = value;
        }
    }
}
