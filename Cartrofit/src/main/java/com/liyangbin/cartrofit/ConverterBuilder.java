package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.Union;
import com.liyangbin.cartrofit.funtion.Union1;
import com.liyangbin.cartrofit.funtion.Union2;
import com.liyangbin.cartrofit.funtion.Union3;
import com.liyangbin.cartrofit.funtion.Union4;
import com.liyangbin.cartrofit.funtion.Union5;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ConverterBuilder<IN, OUT, A extends Annotation> {

    private Supplier<IN> inputProvider;
    private final ArrayList<AbsParameterSolution> forwardSolutions = new ArrayList<>();
    private final ArrayList<AbsParameterSolution> backwardSolutions = new ArrayList<>();
    private final Class<?> callType;
    private final Context.CallSolution<A> caller;
    private final AbsParameterSolution plainOutSolution = takeAny().output((old, para) -> {
        para.set(old);
        return old;
    }).noAnnotate();

    ConverterBuilder(Class<? extends FixedTypeCall<IN, OUT>> callType, Context.CallSolution<A> caller) {
        this.callType = callType;
        this.caller = caller;
    }

    private void commitSolution(AbsParameterSolution solution) {
        if (solution.hasForward()) {
            boolean inserted = false;
            for (int i = 0; i < forwardSolutions.size(); i++) {
                if (forwardSolutions.get(i).size() > solution.size()) {
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

    Converter<Union, IN> checkIn(ParameterGroup group) {
        InputConverterImpl inputConverter = new InputConverterImpl(group);
        findSolutionDependency(true, group, inputConverter);
        return inputConverter;
    }

    Converter<OUT, Union> checkOutCallback(ParameterGroup group) {
        OutputConverterImpl outputConverter = new OutputConverterImpl(group);
        findSolutionDependency(false, group, outputConverter);
        return outputConverter;
    }

    Converter<OUT, Object> checkOutReturn(Key key) {
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

    public ConverterBuilder<IN, OUT, A> provideBasic(Supplier<IN> provider) {
        this.inputProvider = provider;
        return ConverterBuilder.this;
    }

    public <FROM> ParameterSolution1<FROM> take(Class<FROM> clazz) {
        return new ParameterSolution1<>(clazz);
    }

    public ParameterSolution1<Object> takeAny() {
        return new ParameterSolution1<>(Object.class);
    }

    private Context.CallSolution<A> commitParameter() {
        if (forwardSolutions.size() > 0) {
            caller.commitInputConverter(callType, this);
        }
        if (backwardSolutions.size() > 0) {
            caller.commitOutputConverter(callType, this);
        }
        return caller;
    }

    interface AbsAccumulator<V extends Union, R> {
        R advanceDefault(R old, V para);
    }

    public interface AccumulatorIndeterminate<V, R> extends AbsAccumulator<Union, R> {

        @Override
        default R advanceDefault(R old, Union para) {
            throw new UnsupportedOperationException();
        }

        R advance(R old, ParaVal<V>[] para);
    }

    public interface Accumulator<V, R> extends AbsAccumulator<Union1<ParaVal<V>>, R> {

        @Override
        default R advanceDefault(R old, Union1<ParaVal<V>> para) {
            return advance(old, para.value1);
        }

        R advance(R old, ParaVal<V> para);
    }

    public interface Accumulator2<V1, V2, R> extends AbsAccumulator<Union2<ParaVal<V1>, ParaVal<V2>>, R> {

        @Override
        default R advanceDefault(R old, Union2<ParaVal<V1>, ParaVal<V2>> para) {
            return advance(old, para.value1, para.value2);
        }

        R advance(R old, ParaVal<V1> more1, ParaVal<V2> more2);
    }

    public interface Accumulator3<V1, V2, V3, R>
            extends AbsAccumulator<Union3<ParaVal<V1>, ParaVal<V2>, ParaVal<V3>>, R> {

        @Override
        default R advanceDefault(R old, Union3<ParaVal<V1>, ParaVal<V2>, ParaVal<V3>> para) {
            return advance(old, para.value1, para.value2, para.value3);
        }

        R advance(R old, ParaVal<V1> more1, ParaVal<V2> more2, ParaVal<V3> more3);
    }

    public interface Accumulator4<V1, V2, V3, V4, R>
            extends AbsAccumulator<Union4<ParaVal<V1>, ParaVal<V2>, ParaVal<V3>, ParaVal<V4>>, R> {

        @Override
        default R advanceDefault(R old, Union4<ParaVal<V1>, ParaVal<V2>, ParaVal<V3>, ParaVal<V4>> para) {
            return advance(old, para.value1, para.value2, para.value3, para.value4);
        }

        R advance(R old, ParaVal<V1> more1, ParaVal<V2> more2, ParaVal<V3> more3, ParaVal<V4> more4);
    }

    public interface Accumulator5<V1, V2, V3, V4, V5, R>
            extends AbsAccumulator<Union5<ParaVal<V1>, ParaVal<V2>, ParaVal<V3>, ParaVal<V4>, ParaVal<V5>>, R> {

        @Override
        default R advanceDefault(R old, Union5<ParaVal<V1>, ParaVal<V2>, ParaVal<V3>, ParaVal<V4>, ParaVal<V5>> para) {
            return advance(old, para.value1, para.value2, para.value3, para.value4, para.value5);
        }

        R advance(R old, ParaVal<V1> more1, ParaVal<V2> more2, ParaVal<V3> more3, ParaVal<V4> more4, ParaVal<V5> more5);
    }

    interface ParaVal<V> {
        Parameter getParameter();
        V get();
        void set(V value);
    }

    abstract class AbsParameterSolution {
        AbsParameterSolution parent;
        Class<?> fixedType;

        Class<? extends Annotation> fixedAnnotationType;
        Predicate<Parameter> extraCheck;

        boolean markedAsTogetherHead;
        boolean indeterminateMode;
        boolean annotateNothing;

        AbsAccumulator<?, IN> forward;
        AbsAccumulator<?, IN> inputTogether;

        AbsAccumulator<?, OUT> backward;
        AbsAccumulator<?, OUT> outputTogether;

        AbsParameterSolution(AbsParameterSolution parent, Class<?> fixedType) {
            this.parent = parent;
            this.fixedType = fixedType;
        }

        abstract int size();

        boolean hasForward() {
            return forward != null || inputTogether != null;
        }

        boolean hasBackward() {
            return backward != null || outputTogether != null;
        }

        boolean isInterestedToStart(Parameter parameter) {
            if (fixedType.isAssignableFrom(parameter.getType())) {
                if (forward != null || backward != null || markedAsTogetherHead) {
                    return ((annotateNothing && fixedAnnotationType == null)
                            || parameter.isAnnotationPresent(fixedAnnotationType))
                            && (extraCheck == null || extraCheck.test(parameter));
                }
            }
            return false;
        }

        boolean isInterestedWithoutAnnotation(Parameter parameter) {
            return fixedType == parameter.getType() && parameter.hasNoAnnotation();
        }

        public final ConverterBuilder<IN, OUT, A> build() {
            if (parent == null && fixedAnnotationType == null) {
                throw new CartrofitGrammarException("Must specify an annotation type before build()");
            }
            commitSolution(this);
            return parent != null ? parent.build() : ConverterBuilder.this;
        }

        public final Context.CallSolution<A> buildAndCommitParameter() {
            return build().commitParameter();
        }
    }

    public class ParameterSolution1<T> extends AbsParameterSolution {

        ParameterSolution1(Class<T> fixedType) {
            super(null, fixedType);
        }

        @Override
        int size() {
            return 1;
        }

        public ParameterSolution1<T> annotateWith(Class<? extends Annotation> annotationType) {
            this.fixedAnnotationType = annotationType;
            return this;
        }

        public ParameterSolution1<T> noAnnotate() {
            this.annotateNothing = true;
            return this;
        }

        public ParameterSolution1<T> input(Accumulator<T, IN> forward) {
            this.forward = forward;
            return this;
        }

        public ParameterSolution1<T> output(Accumulator<T, OUT> backward) {
            this.backward = backward;
            return this;
        }

        public ParameterSolution1<T> forwardIndeterminate(AccumulatorIndeterminate<T, IN> forward) {
            this.forward = forward;
            this.indeterminateMode = true;
            return this;
        }

        public ParameterSolution1<T> backwardIndeterminate(AccumulatorIndeterminate<T, OUT> backward) {
            this.backward = backward;
            this.indeterminateMode = true;
            return this;
        }

        public <T2> ParameterSolution2<T, T2> and(Class<T2> fromClazz) {
            if (indeterminateMode) {
                throw new CartrofitGrammarException("Can not add other types in indeterminate mode");
            }
            markedAsTogetherHead = true;
            return new ParameterSolution2<>(this, fromClazz);
        }
    }

    public class ParameterSolution2<T1, T2> extends AbsParameterSolution {

        ParameterSolution2(ParameterSolution1<T1> solutionBase, Class<T2> type) {
            super(solutionBase, type);
        }

        @Override
        int size() {
            return 2;
        }

        public ParameterSolution2<T1, T2> inputTogether(Accumulator2<T1, T2, IN> forward) {
            this.inputTogether = forward;
            return this;
        }

        public ParameterSolution2<T1, T2> outputTogether(Accumulator2<T1, T2, OUT> backward) {
            this.outputTogether = backward;
            return this;
        }

        public <T3> ParameterSolution3<T1, T2, T3> and(Class<T3> fromClazz) {
            return new ParameterSolution3<>(this, fromClazz);
        }
    }

    public class ParameterSolution3<T1, T2, T3> extends AbsParameterSolution {

        ParameterSolution3(ParameterSolution2<T1, T2> solutionBase, Class<T3> type) {
            super(solutionBase, type);
        }

        @Override
        int size() {
            return 3;
        }

        public ParameterSolution3<T1, T2, T3> inputTogether(Accumulator3<T1, T2, T3, IN> forward) {
            this.inputTogether = forward;
            return this;
        }

        public ParameterSolution3<T1, T2, T3> outputTogether(Accumulator3<T1, T2, T3, OUT> backward) {
            this.outputTogether = backward;
            return this;
        }

        public <T4> ParameterSolution4<T1, T2, T3, T4> and(Class<T4> fromClazz) {
            return new ParameterSolution4<>(this, fromClazz);
        }
    }

    public class ParameterSolution4<T1, T2, T3, T4> extends AbsParameterSolution {

        ParameterSolution4(ParameterSolution3<T1, T2, T3> solutionBase, Class<T4> type) {
            super(solutionBase, type);
        }

        @Override
        int size() {
            return 4;
        }

        public ParameterSolution4<T1, T2, T3, T4> inputTogether(Accumulator4<T1, T2, T3, T4, IN> forward) {
            this.inputTogether = forward;
            return this;
        }

        public ParameterSolution4<T1, T2, T3, T4> outputTogether(Accumulator4<T1, T2, T3, T4, OUT> backward) {
            this.outputTogether = backward;
            return this;
        }

        public <T5> ParameterSolution5<T1, T2, T3, T4, T5> and(Class<T5> fromClazz) {
            return new ParameterSolution5<>(this, fromClazz);
        }
    }

    public class ParameterSolution5<T1, T2, T3, T4, T5> extends AbsParameterSolution {

        ParameterSolution5(ParameterSolution4<T1, T2, T3, T4> solutionBase, Class<T5> type) {
            super(solutionBase, type);
        }

        @Override
        int size() {
            return 5;
        }

        public ParameterSolution5<T1, T2, T3, T4, T5> inputTogether(Accumulator5<T1, T2, T3, T4, T5, IN> forward) {
            this.inputTogether = forward;
            return this;
        }

        public ParameterSolution5<T1, T2, T3, T4, T5> outputTogether(Accumulator5<T1, T2, T3, T4, T5, OUT> backward) {
            this.outputTogether = backward;
            return this;
        }
    }

    private class SolutionRecord {
        AbsParameterSolution solution;
        int start;
        int length;

        SolutionRecord(AbsParameterSolution solution, int start, int length) {
            this.solution = solution;
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
            solutionRecords.add(new SolutionRecord(solution, slotStart, slotLength));
        }

        @SuppressWarnings("unchecked")
        final SERIALIZATION assemble(SERIALIZATION rawInput, Union avengers) {
            for (int i = 0; i < solutionRecords.size(); i++) {
                SolutionRecord record = solutionRecords.get(i);
                if (record.length > 1 || record.solution.indeterminateMode) {
                    ParaVal<Object>[] array = new ParaVal[record.length];
                    for (int j = 0; j < array.length; j++) {
                        array[j] = onCreateAccessibleParameter(record.start + j, avengers);
                    }
                    if (record.solution.indeterminateMode) {
                        AccumulatorIndeterminate<Object, SERIALIZATION> accumulator = (AccumulatorIndeterminate<Object, SERIALIZATION>)
                                (input ? record.solution.forward : record.solution.backward);
                        rawInput = accumulator.advance(rawInput, array);
                    } else {
                        AbsAccumulator<Union, SERIALIZATION> accumulator = (AbsAccumulator<Union, SERIALIZATION>)
                                (input ? record.solution.inputTogether : record.solution.outputTogether);
                        Union union = Union.of(array);
                        rawInput = accumulator.advanceDefault(rawInput, union);
                        union.recycle();
                    }
                    // TODO： warning if user does not call set
                } else {
                    Accumulator<Object, SERIALIZATION> accumulator = (Accumulator<Object, SERIALIZATION>)
                            (input ? record.solution.forward : record.solution.backward);
                    rawInput = accumulator.advance(rawInput, onCreateAccessibleParameter(record.start, avengers));
                }
            }
            return rawInput;
        }

        abstract AccessibleParameter onCreateAccessibleParameter(int index, Union parameterHost);

        private abstract class AccessibleParameter implements ParaVal<Object> {
            int index;
            Union parameterHost;

            AccessibleParameter(int index, Union parameterHost) {
                this.index = index;
                this.parameterHost = parameterHost;
            }

            @Override
            public Parameter getParameter() {
                return parameterGroup.getParameterAt(index);
            }

            @Override
            public Object get() {
                return parameterHost.get(index);
            }

            @Override
            public void set(Object value) {
                parameterHost.set(index, value);
            }
        }
    }

    private class OutputConverterImpl extends SolutionRecordKeeper<OUT> implements Converter<OUT, Union> {

        OutputConverterImpl(ParameterGroup parameterGroup) {
            super(false, parameterGroup);
        }

        @Override
        AccessibleParameter onCreateAccessibleParameter(int index, Union parameterHost) {
            return new WritableParameter(index, parameterHost);
        }

        private class WritableParameter extends AccessibleParameter {

            WritableParameter(int index, Union parameterHost) {
                super(index, parameterHost);
            }

            @Override
            public Object get() {
                throw new UnsupportedOperationException("Invalid call");
            }
        }

        @Override
        public Union convert(OUT rawData) {
            final int count = parameterGroup.getParameterCount();
            if (count == 0) {
                return Union.ofNull();
            }
            Union parameterHost = Union.ofArray(new Object[count]);
            assemble(rawData, parameterHost);
            return parameterHost;
        }
    }

    private class InputConverterImpl extends SolutionRecordKeeper<IN> implements Converter<Union, IN> {

        InputConverterImpl(ParameterGroup parameterGroup) {
            super(true, parameterGroup);
        }

        @Override
        AccessibleParameter onCreateAccessibleParameter(int index, Union parameterHost) {
            return new ReadableParameter(index, parameterHost);
        }

        private class ReadableParameter extends AccessibleParameter {

            ReadableParameter(int index, Union parameterHost) {
                super(index, parameterHost);
            }

            @Override
            public void set(Object value) {
                throw new UnsupportedOperationException("Invalid call");
            }
        }

        @Override
        public IN convert(Union parameterInput) {
            IN rawInput = inputProvider != null ? inputProvider.get() : null;
            final int count = parameterGroup.getParameterCount();
            if (count == 0) {
                return rawInput;
            }
            return assemble(rawInput, parameterInput);
        }
    }

    private class ReturnConverterImpl implements Converter<OUT, Object>, ParaVal<Object> {

        AbsParameterSolution targetSolution;
        Parameter returnAsParameter;
        Object tmpResult;

        ReturnConverterImpl(AbsParameterSolution targetSolution, Parameter returnAsParameter) {
            this.targetSolution = targetSolution;
            this.returnAsParameter = returnAsParameter;
        }

        @Override
        public Object convert(OUT value) {
            Accumulator<Object, OUT> accumulator
                    = (Accumulator<Object, OUT>) targetSolution.backward;
            synchronized (this) {
                accumulator.advance(value, this);
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
