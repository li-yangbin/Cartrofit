package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.ParameterCategory;
import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.Converter2;
import com.liyangbin.cartrofit.funtion.Converter3;
import com.liyangbin.cartrofit.funtion.Converter4;
import com.liyangbin.cartrofit.funtion.Converter5;
import com.liyangbin.cartrofit.funtion.TwoWayConverter;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.function.Supplier;

public abstract class ConverterBuilder<SERIALIZATION> {

    private Class<SERIALIZATION> serializeType;

    ConverterBuilder(Class<SERIALIZATION> serializeType) {
        this.serializeType = serializeType;
    }

    ArrayList<InitializeSolution> initializeSolutions;
    //        Class<? extends Annotation>[] initialedAnnotationArray;
//        Class<? extends Annotation>[] extraAnnotationArray;
    ArrayList<AccumulateSolution<?>> accumulateSolutions;

    Class<?>[] concernedTypes;
    Converter<Object, SERIALIZATION> converterIn;
    Converter<SERIALIZATION, Object> converterOut;

//        HashMap<Class<?>>

    Converter<Union, SERIALIZATION> checkConvertIn(Cartrofit.ParameterGroup group) {
        int initialBits = 0;
        int accumulatedBits = 0;
        int extraBits = 0;// TODO
        int emptyBits = 0;

        OutputConverterImpl outputConverter = new OutputConverterImpl();

        anchor: for (int i = 0; i < group.getParameterCount(); i++) {
            Cartrofit.Parameter parameter = group.getParameterAt(i);
            Annotation[] annotations = parameter.getAnnotations();
            for (Annotation annotation : annotations) {
                ParameterCategory category = annotation.annotationType()
                        .getDeclaredAnnotation(ParameterCategory.class);
                if (category != null) {
                    switch (category.value()) {
                        case ParameterCategory.INIT:
                            initialBits |= 1 << i;
                            continue anchor;
                        case ParameterCategory.ACCUMULATE:
                            accumulatedBits |= 1 << i;
                            continue anchor;
                        case ParameterCategory.EXTRA:
                            extraBits |= 1 << i;
                            continue anchor;
                    }
                }
            }
            emptyBits |= 1 << i;
        }

        int annotatedCount = initialBits != 0 ? Integer.bitCount(initialBits) : 0;
        int emptyCount = emptyBits != 0 ? Integer.bitCount(emptyBits) : 0;
        anchor:for (int i = 0; i < initializeSolutions.size(); i++) {
            InitializeSolution solution = initializeSolutions.get(i);
            if (solution.size() == annotatedCount + emptyCount) {
                for (int j = 0, k = 0; j < group.getParameterCount(); j++) {
                    Cartrofit.Parameter parameter = group.getParameterAt(j);
                    if ((initialBits & j) != 0) {
                        Class<? extends Annotation> annotationTypeExpected = solution.get(k).fixedAnnotationType;
                        Annotation annotation = parameter.getAnnotation(annotationTypeExpected);
                        if (annotation == null) {
                            outputConverter.reset();
                            continue anchor;
                        }
                        if (!solution.get(k).fixedType.isAssignableFrom(parameter.getType())) {
                            outputConverter.reset();
                            continue anchor;
                        }
                        outputConverter.addInitializeElement(annotation, parameter);
                        k++;
                    } else if ((emptyBits & j) != 0) {
                        if (solution.get(k).annotateNecessary) {
                            outputConverter.reset();
                            continue anchor;
                        }
                        if (!solution.get(k).fixedType.isAssignableFrom(parameter.getType())) {
                            outputConverter.reset();
                            continue anchor;
                        }
                        outputConverter.addInitializeElement(null, parameter);
                        k++;
                    }
                }

                outputConverter.setInitializer(solution.converterIn);
                break;
            }
        }

        if (outputConverter.isEmpty()) {
            return null;
        }

        for (int i = 0; i < group.getParameterCount(); i++) {
            if ((accumulatedBits & i) != 0) {
                Cartrofit.Parameter parameter = group.getParameterAt(i);
                for (int j = 0; j < accumulateSolutions.size(); j++) {
                    AccumulateSolution<?> accumulateSolution = accumulateSolutions.get(j);
                    if (accumulateSolution.moreType.isAssignableFrom(parameter.getType())) {
                        Annotation annotation = parameter
                                .getAnnotation(accumulateSolution.annotationType);
                        if (annotation != null) {
                            outputConverter.addAccumulatedElement(annotation, parameter,
                                    accumulateSolution);
                            break;
                        }
                    }
                }
            }
        }
        return outputConverter;
    }

    public InitBuilder initBuilder() {
        return new InitBuilder();
    }

    public class InitBuilder {

        InitializeSolution solution = new InitializeSolution();

        public ConverterBuilder<SERIALIZATION> commit(Supplier<SERIALIZATION> provider) {
            solution.simpleProvide(provider);
            initializeSolutions.add(solution);
            return ConverterBuilder.this;
        }

        public ConverterBuilder<SERIALIZATION> commit() {
            initializeSolutions.add(solution);
            return ConverterBuilder.this;
        }

        public final <FROM> ConverterBuilder1<FROM> convert(Class<FROM> clazz) {
            return new ConverterBuilder1<>(new InitElement<>(clazz));
        }

        public class ConverterBuilder1<TARGET> extends InitBuilder {
            InitElement<TARGET> element1;

            private ConverterBuilder1(InitElement<TARGET> element) {
                this.element1 = element;
            }

            public ConverterBuilder1<TARGET> annotate(Class<? extends Annotation> annotationType, boolean necessary) {
                element1.fixedAnnotationType = annotationType;
                element1.annotateNecessary = necessary;
                return this;
            }

            public ConverterBuilder<SERIALIZATION> commit(Converter<Arg<TARGET>, SERIALIZATION> converter) {
                solution.add(element1);
                solution.with(converterIn);
                initializeSolutions.add(solution);
                return ConverterBuilder.this;
            }

            public <TARGET2> ConverterBuilder2<TARGET, TARGET2> andThen(Class<TARGET2> fromClazz) {
                return new ConverterBuilder2<>(element1, new InitElement<>(fromClazz));
            }
        }

        public final class ConverterBuilder2<TARGET1, TARGET2> {
            InitElement<TARGET1> element1;
            InitElement<TARGET2> element2;

            private ConverterBuilder2(InitElement<TARGET1> element1, InitElement<TARGET2> element2) {
                this.element1 = element1;
                this.element2 = element2;
            }

            public ConverterBuilder2<TARGET1, TARGET2> annotate(Class<? extends Annotation> annotationType,
                                                                boolean necessary) {
                element2.fixedAnnotationType = annotationType;
                element2.annotateNecessary = necessary;
                return this;
            }

            public ConverterBuilder<SERIALIZATION> commit(
                    Converter2<Arg<TARGET1>, Arg<TARGET2>, SERIALIZATION> converter) {
                solution.add(element1);
                solution.add(element2);
                solution.with(converterIn);
                initializeSolutions.add(solution);
                return ConverterBuilder.this;
            }

            public <TARGET3> ConverterBuilder3<TARGET1, TARGET2, TARGET3> andThen(Class<TARGET3> fromClazz) {
                return new ConverterBuilder3<>(this, new InitElement<>(fromClazz));
            }
        }

        public final class ConverterBuilder3<TARGET1, TARGET2, TARGET3> {
            InitElement<TARGET1> element1;
            InitElement<TARGET2> element2;
            InitElement<TARGET3> element3;

            private ConverterBuilder3(ConverterBuilder2<TARGET1, TARGET2> others,
                                      InitElement<TARGET3> element) {
                this.element1 = others.element1;
                this.element2 = others.element2;
                this.element3 = element;
            }

            public ConverterBuilder3<TARGET1, TARGET2, TARGET3>
            annotate(Class<? extends Annotation> annotationType, boolean necessary) {
                element2.fixedAnnotationType = annotationType;
                element2.annotateNecessary = necessary;
                return this;
            }

            public ConverterBuilder<SERIALIZATION> commit(
                    Converter3<Arg<TARGET1>, Arg<TARGET2>, Arg<TARGET3>, SERIALIZATION> converter) {
                solution.add(element1);
                solution.add(element2);
                solution.add(element3);
                solution.with(converterIn);
                initializeSolutions.add(solution);
                return ConverterBuilder.this;
            }

            public <TARGET4> ConverterBuilder4<TARGET1, TARGET2, TARGET3, TARGET4> andThen(Class<TARGET4> fromClazz) {
                return new ConverterBuilder4<>(this, new InitElement<>(fromClazz));
            }
        }

        public final class ConverterBuilder4<TARGET1, TARGET2, TARGET3, TARGET4> {
            InitElement<TARGET1> element1;
            InitElement<TARGET2> element2;
            InitElement<TARGET3> element3;
            InitElement<TARGET4> element4;

            private ConverterBuilder4(ConverterBuilder3<TARGET1, TARGET2, TARGET3> others,
                                      InitElement<TARGET4> element) {
                this.element1 = others.element1;
                this.element2 = others.element2;
                this.element3 = others.element3;
                this.element4 = element;
            }

            public ConverterBuilder4<TARGET1, TARGET2, TARGET3, TARGET4>
            annotate(Class<? extends Annotation> annotationType, boolean necessary) {
                element2.fixedAnnotationType = annotationType;
                element2.annotateNecessary = necessary;
                return this;
            }

            public ConverterBuilder<SERIALIZATION> commit(
                    Converter4<Arg<TARGET1>, Arg<TARGET2>, Arg<TARGET3>, Arg<TARGET4>, SERIALIZATION> converter) {
                solution.add(element1);
                solution.add(element2);
                solution.add(element3);
                solution.add(element4);
                solution.with(converterIn);
                initializeSolutions.add(solution);
                return ConverterBuilder.this;
            }

            public <TARGET5> ConverterBuilder5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5>
            andThen(Class<TARGET5> fromClazz) {
                return new ConverterBuilder5<>(this, new InitElement<>(fromClazz));
            }
        }

        public final class ConverterBuilder5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5> {
            InitElement<TARGET1> element1;
            InitElement<TARGET2> element2;
            InitElement<TARGET3> element3;
            InitElement<TARGET4> element4;
            InitElement<TARGET5> element5;

            private ConverterBuilder5(ConverterBuilder4<TARGET1, TARGET2, TARGET3, TARGET4> others,
                                      InitElement<TARGET5> element) {
                this.element1 = others.element1;
                this.element2 = others.element2;
                this.element3 = others.element3;
                this.element4 = others.element4;
                this.element5 = element;
            }

            public ConverterBuilder5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5>
            annotate(Class<? extends Annotation> annotationType, boolean necessary) {
                element2.fixedAnnotationType = annotationType;
                element2.annotateNecessary = necessary;
                return this;
            }

            public ConverterBuilder<SERIALIZATION> commit(
                    Converter5<Arg<TARGET1>, Arg<TARGET2>, Arg<TARGET3>,
                            Arg<TARGET4>, Arg<TARGET5>, SERIALIZATION> converter) {
                solution.add(element1);
                solution.add(element2);
                solution.add(element3);
                solution.add(element4);
                solution.add(element5);
                solution.with(converterIn);
                initializeSolutions.add(solution);
                return ConverterBuilder.this;
            }
        }
    }

    abstract void onCommit(ConvertSolution builder);

    public interface Accumulator<V, R> {
        R advance(R old, Arg<V> more);
    }

    interface Arg<V> {
        <A extends Annotation> A getAnnotation();
        Cartrofit.Parameter getParameter();
        V get();
    }

    class AccumulateSolution<V> {
        Class<V> moreType;
        Class<? extends Annotation> annotationType;
        Accumulator<V, SERIALIZATION> accumulator;
    }

    static class InitElement<T> {
        Class<T> fixedType;
        Class<? extends Annotation> fixedAnnotationType;
        boolean annotateNecessary;

        InitElement(Class<T> fixedType) {
            this.fixedType = fixedType;
        }
    }

    class InitializeSolution {
        ArrayList<InitElement<?>> fixedElements;
        Converter<Object, SERIALIZATION> converterIn;

        void simpleProvide(Supplier<SERIALIZATION> supplier) {
            converterIn = value -> supplier.get();
        }

        int size () {
            return fixedElements != null ? fixedElements.size() : 0;
        }

        InitElement<?> get(int index) {
            return fixedElements.get(index);
        }

        void add(InitElement<?> element) {
            if (fixedElements == null) {
                fixedElements = new ArrayList<>();
            }
            fixedElements.add(element);
        }

        void with(Converter<Object, SERIALIZATION> converterIn) {
            this.converterIn = converterIn;
        }
    }

    private class OutputConverterImpl implements Converter<Union, SERIALIZATION> {

        ArrayList<Cartrofit.Parameter> initializeParameters;
        ArrayList<Annotation> initializeAnnotationArrayList;
        Converter<Object, SERIALIZATION> initializer;

        ArrayList<Cartrofit.Parameter> accumulatedParameters;
        ArrayList<Annotation> accumulatedAnnotationArrayList;
        ArrayList<Accumulator<Object, SERIALIZATION>> accumulators;

        class AnnotatedValueImpl implements Arg<Object> {

            int index;
            Union parameterHost;
            ArrayList<Cartrofit.Parameter> parameters;
            ArrayList<Annotation> annotations;

            AnnotatedValueImpl(int index, Union parameterHost, boolean accumulates) {
                this.index = index;
                this.parameterHost = parameterHost;
                this.parameters = accumulates ? accumulatedParameters : initializeParameters;
                this.annotations = accumulates ? accumulatedAnnotationArrayList
                        : initializeAnnotationArrayList;
            }

            @Override
            public <A extends Annotation> A getAnnotation() {
                return (A) annotations.get(index);
            }

            @Override
            public Cartrofit.Parameter getParameter() {
                return parameters.get(index);
            }

            @Override
            public Object get() {
                return parameterHost.get(parameters.get(index).getDeclaredIndex());
            }
        }

        void setInitializer(Converter<Object, SERIALIZATION> initializer) {
            this.initializer = initializer;
        }

        void addInitializeElement(Annotation annotation, Cartrofit.Parameter parameter) {
            if (initializeAnnotationArrayList == null) {
                initializeAnnotationArrayList = new ArrayList<>();
            }
            initializeAnnotationArrayList.add(annotation);
            if (initializeParameters == null) {
                initializeParameters = new ArrayList<>();
            }
            initializeParameters.add(parameter);
        }

        void addAccumulatedElement(Annotation annotation, Cartrofit.Parameter parameter,
                                   AccumulateSolution<?> solution) {
            if (accumulatedAnnotationArrayList == null) {
                accumulatedAnnotationArrayList = new ArrayList<>();
            }
            accumulatedAnnotationArrayList.add(annotation);
            if (accumulatedParameters == null) {
                accumulatedParameters = new ArrayList<>();
            }
            accumulatedParameters.add(parameter);
            if (accumulators == null) {
                accumulators = new ArrayList<>();
            }
            accumulators.add((Accumulator<Object, SERIALIZATION>) solution.accumulator);
        }

        void reset() {
            this.initializer = null;
            if (initializeAnnotationArrayList != null) {
                initializeAnnotationArrayList.clear();
            }
            if (initializeParameters != null) {
                initializeParameters.clear();
            }
        }

        boolean isEmpty() {
            return initializer == null;
        }

        @Override
        public SERIALIZATION convert(Union value) {
            int inputCount = initializeParameters != null ? initializeParameters.size() : 0;
            int inputCountFromConverter = initializer.getInputCount();
            if (inputCount != inputCountFromConverter) {
                throw new RuntimeException("logic impossible");
            }
            SERIALIZATION rawMaterial;
            if (inputCount == 0) {
                rawMaterial = initializer.convert(null);
            } else if (inputCount == 1) {
                rawMaterial = initializer.convert(new AnnotatedValueImpl(0, value, false));
            } else {
                Arg<?>[] result = new Arg[inputCount];
                for (int i = 0; i < inputCount; i++) {
                    result[i] = new AnnotatedValueImpl(i, value, false);
                }
                Union inputUnion = Union.of(result);
                rawMaterial = initializer.convert(inputUnion);
                inputUnion.recycle();
            }
            if (rawMaterial == null) {
                throw new NullPointerException("Failed to initialize SERIALIZATION obj");
            }

            int accumulateCount = accumulators != null ? accumulators.size() : 0;
            SERIALIZATION finalResult = rawMaterial;
            for (int i = 0; i < accumulateCount; i++) {
                finalResult = accumulators.get(i).advance(finalResult,
                        new AnnotatedValueImpl(i, value, true));
            }
            return finalResult;
        }
    }

    abstract class ConvertSolution {

        void setConvertIn(Converter<?, SERIALIZATION> converterIn) {
            this.converterIn = (Converter<Object, SERIALIZATION>) converterIn;
        }

        TwoWayConverter<?, SERIALIZATION> twoWayConverter;

        Class<? extends Annotation>[] concernedAnnotations;

        ConvertSolution(Class<?>... clazzArray) {
            this.concernedTypes = clazzArray;
        }

        ConvertSolution() {
            this.concernedTypes = new Class<?>[0];
        }

        Converter<?, SERIALIZATION> findInputConverter(Cartrofit.ParameterGroup group, Cartrofit.Key key) {
            if (concernedTypes.length == group.getParameterCount()) {
                for (int i = 0; i < concernedTypes.length; i++) {
                    if (!Cartrofit.classEquals(concernedTypes[i],
                            group.getParameterAt(i).getType())) {
                        return null;
                    }
                }
            }
            return twoWayConverter != null ? twoWayConverter : converterIn;
        }

        Converter<SERIALIZATION, ?> findOutputConverter(Cartrofit.ParameterGroup group, Cartrofit.Key key, boolean flowMap) {
            if (key.isCallbackEntry) {
                if (concernedTypes.length == group.getParameterCount()) {
                    for (int i = 0; i < concernedTypes.length; i++) {
                        if (!Cartrofit.classEquals(concernedTypes[i],
                                group.getParameterAt(i).getType())) {
                            return null;
                        }
                    }
                }
                return twoWayConverter != null ? twoWayConverter.reverseConverter() : converterOut;
            } else if (flowMap) {
                if (concernedTypes.length == 1 && concernedTypes[0] == key.getUserConcernClass()) {
                    return twoWayConverter != null ? twoWayConverter.reverseConverter() : converterOut;
                }
            } else {
                if (concernedTypes.length == 1 && concernedTypes[0] == key.getReturnType()) {
                    return twoWayConverter != null ? twoWayConverter.reverseConverter() : converterOut;
                }
            }
            return null;
        }

        @SafeVarargs
        final void saveAnnotation(Class<? extends Annotation>... annotations) {
            if (annotations.length != concernedTypes.length) {
                throw new CartrofitGrammarException("input annotation count:" + annotations.length
                        + " does not match type count:" + concernedTypes.length);
            }
            concernedAnnotations = annotations;
        }
    }
}
