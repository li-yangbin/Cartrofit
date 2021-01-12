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

    ArrayList<EssentialSolution> essentialSolutions = new ArrayList<>();
    //        Class<? extends Annotation>[] initialedAnnotationArray;
//        Class<? extends Annotation>[] extraAnnotationArray;
    ArrayList<AttributeSolution<?, ?>> accumulateSolutions;

    Class<?>[] concernedTypes;
    Converter<Object, SERIALIZATION> converterIn;
    Converter<SERIALIZATION, Object> converterOut;
    EssentialSolution tempSolution;

//        HashMap<Class<?>>

    Converter<Union, SERIALIZATION> checkConvertIn(Cartrofit.ParameterGroup group) {
        int essentialBits = 0;
        int attributeBits = 0;
        int extraBits = 0;// TODO
        int emptyBits = 0;

        anchor: for (int i = 0; i < group.getParameterCount(); i++) {
            Cartrofit.Parameter parameter = group.getParameterAt(i);
            Annotation[] annotations = parameter.getAnnotations();
            for (Annotation annotation : annotations) {
                ParameterCategory category = annotation.annotationType()
                        .getDeclaredAnnotation(ParameterCategory.class);
                if (category != null) {
                    switch (category.value()) {
                        case ParameterCategory.ESSENTIAL:
                            essentialBits |= 1 << i;
                            continue anchor;
                        case ParameterCategory.ATTRIBUTE:
                            attributeBits |= 1 << i;
                            continue anchor;
                        case ParameterCategory.EXTRA:
                            extraBits |= 1 << i;
                            continue anchor;
                    }
                }
            }
            emptyBits |= 1 << i;
        }

        int annotatedCount = essentialBits != 0 ? Integer.bitCount(essentialBits) : 0;
        int emptyCount = emptyBits != 0 ? Integer.bitCount(emptyBits) : 0;
        final int essentialElementCount = annotatedCount + emptyCount;
        OutputConverterImpl outputConverter = new OutputConverterImpl();

        anchor:for (int i = 0; i < essentialSolutions.size(); i++) {
            EssentialSolution solution = essentialSolutions.get(i);
            if (essentialElementCount == 0) {
                if (solution.simpleProviderMode) {
                    outputConverter.setInitializer(solution.converterIn);
                    break;
                }
            } else if (solution.size() == essentialElementCount) {
                int k = 0;
                for (int j = 0; j < group.getParameterCount() && k < solution.size(); j++) {
                    Cartrofit.Parameter parameter = group.getParameterAt(j);
                    if ((essentialBits & j) != 0) {
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
                if (k == solution.size()) {
                    outputConverter.setInitializer(solution.converterIn);
                    break;
                } else {
                    outputConverter.reset();
                }
            }
        }

        if (outputConverter.isEmpty()) {
            return null;
        }

        for (int i = 0; i < group.getParameterCount(); i++) {
            if ((attributeBits & i) != 0) {
                Cartrofit.Parameter parameter = group.getParameterAt(i);
                for (int j = 0; j < accumulateSolutions.size(); j++) {
                    AttributeSolution<?, ?> attributeSolution = accumulateSolutions.get(j);
                    if (attributeSolution.moreType.isAssignableFrom(parameter.getType())) {
                        Annotation annotation = parameter
                                .getAnnotation(attributeSolution.annotationType);
                        if (annotation != null) {
                            outputConverter.addAccumulatedElement(annotation, parameter,
                                    attributeSolution);
                            break;
                        }
                    }
                }
            }
        }
        return outputConverter;
    }

    public ConverterBuilder<SERIALIZATION> essential(Supplier<SERIALIZATION> provider) {
        EssentialSolution tempSolution = new EssentialSolution();
        tempSolution.simpleProvide(provider);
        essentialSolutions.add(tempSolution);
        return ConverterBuilder.this;
    }

    public final <FROM> EssentialBuilder1<FROM> essential(Class<FROM> clazz) {
        tempSolution = new EssentialSolution();
        return new EssentialBuilder1<>(new InitElement<>(clazz));
    }

    public class EssentialBuilder1<TARGET> {
        InitElement<TARGET> element1;

        private EssentialBuilder1(InitElement<TARGET> element) {
            this.element1 = element;
        }

        public EssentialBuilder1<TARGET> annotate(Class<? extends Annotation> annotationType, boolean necessary) {
            element1.fixedAnnotationType = annotationType;
            element1.annotateNecessary = necessary;
            return this;
        }

        public ConverterBuilder<SERIALIZATION> commitIn(Converter<ParaVal<TARGET>, SERIALIZATION> converter) {
            tempSolution.add(element1);
            tempSolution.withIn(converter);
            essentialSolutions.add(tempSolution);
            return ConverterBuilder.this;
        }

        public ConverterBuilder<SERIALIZATION> commitOut(Converter<SERIALIZATION, ParaVal<TARGET>> converter) {
            tempSolution.add(element1);
            tempSolution.withOut(converter);
            essentialSolutions.add(tempSolution);
            return ConverterBuilder.this;
        }

        public <TARGET2> EssentialBuilder2<TARGET, TARGET2> and(Class<TARGET2> fromClazz) {
            return new EssentialBuilder2<>(element1, new InitElement<>(fromClazz));
        }
    }

    public final class EssentialBuilder2<TARGET1, TARGET2> {
        InitElement<TARGET1> element1;
        InitElement<TARGET2> element2;

        private EssentialBuilder2(InitElement<TARGET1> element1, InitElement<TARGET2> element2) {
            this.element1 = element1;
            this.element2 = element2;
        }

        public EssentialBuilder2<TARGET1, TARGET2> annotate(Class<? extends Annotation> annotationType,
                                                            boolean necessary) {
            element2.fixedAnnotationType = annotationType;
            element2.annotateNecessary = necessary;
            return this;
        }

        public ConverterBuilder<SERIALIZATION> commitIn(
                Converter2<ParaVal<TARGET1>, ParaVal<TARGET2>, SERIALIZATION> converter) {
            tempSolution.add(element1);
            tempSolution.add(element2);
            tempSolution.withIn(converterIn);
            essentialSolutions.add(tempSolution);
            return ConverterBuilder.this;
        }

        public <TARGET3> EssentialBuilder3<TARGET1, TARGET2, TARGET3> and(Class<TARGET3> fromClazz) {
            return new EssentialBuilder3<>(this, new InitElement<>(fromClazz));
        }
    }

    public final class EssentialBuilder3<TARGET1, TARGET2, TARGET3> {
        InitElement<TARGET1> element1;
        InitElement<TARGET2> element2;
        InitElement<TARGET3> element3;

        private EssentialBuilder3(EssentialBuilder2<TARGET1, TARGET2> others,
                                  InitElement<TARGET3> element) {
            this.element1 = others.element1;
            this.element2 = others.element2;
            this.element3 = element;
        }

        public EssentialBuilder3<TARGET1, TARGET2, TARGET3>
        annotate(Class<? extends Annotation> annotationType, boolean necessary) {
            element2.fixedAnnotationType = annotationType;
            element2.annotateNecessary = necessary;
            return this;
        }

        public ConverterBuilder<SERIALIZATION> commitIn(
                Converter3<ParaVal<TARGET1>, ParaVal<TARGET2>,
                        ParaVal<TARGET3>, SERIALIZATION> converter) {
            tempSolution.add(element1);
            tempSolution.add(element2);
            tempSolution.add(element3);
            tempSolution.withIn(converterIn);
            essentialSolutions.add(tempSolution);
            return ConverterBuilder.this;
        }

        public <TARGET4> EssentialBuilder4<TARGET1, TARGET2, TARGET3, TARGET4> and(Class<TARGET4> fromClazz) {
            return new EssentialBuilder4<>(this, new InitElement<>(fromClazz));
        }
    }

    public final class EssentialBuilder4<TARGET1, TARGET2, TARGET3, TARGET4> {
        InitElement<TARGET1> element1;
        InitElement<TARGET2> element2;
        InitElement<TARGET3> element3;
        InitElement<TARGET4> element4;

        private EssentialBuilder4(EssentialBuilder3<TARGET1, TARGET2, TARGET3> others,
                                  InitElement<TARGET4> element) {
            this.element1 = others.element1;
            this.element2 = others.element2;
            this.element3 = others.element3;
            this.element4 = element;
        }

        public EssentialBuilder4<TARGET1, TARGET2, TARGET3, TARGET4>
        annotate(Class<? extends Annotation> annotationType, boolean necessary) {
            element2.fixedAnnotationType = annotationType;
            element2.annotateNecessary = necessary;
            return this;
        }

        public ConverterBuilder<SERIALIZATION> commitIn(
                Converter4<ParaVal<TARGET1>, ParaVal<TARGET2>, ParaVal<TARGET3>,
                        ParaVal<TARGET4>, SERIALIZATION> converter) {
            tempSolution.add(element1);
            tempSolution.add(element2);
            tempSolution.add(element3);
            tempSolution.add(element4);
            tempSolution.withIn(converterIn);
            essentialSolutions.add(tempSolution);
            return ConverterBuilder.this;
        }

        public <TARGET5> EssentialBuilder5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5>
        and(Class<TARGET5> fromClazz) {
            return new EssentialBuilder5<>(this, new InitElement<>(fromClazz));
        }
    }

    public final class EssentialBuilder5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5> {
        InitElement<TARGET1> element1;
        InitElement<TARGET2> element2;
        InitElement<TARGET3> element3;
        InitElement<TARGET4> element4;
        InitElement<TARGET5> element5;

        private EssentialBuilder5(EssentialBuilder4<TARGET1, TARGET2, TARGET3, TARGET4> others,
                                  InitElement<TARGET5> element) {
            this.element1 = others.element1;
            this.element2 = others.element2;
            this.element3 = others.element3;
            this.element4 = others.element4;
            this.element5 = element;
        }

        public EssentialBuilder5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5>
        annotate(Class<? extends Annotation> annotationType, boolean necessary) {
            element2.fixedAnnotationType = annotationType;
            element2.annotateNecessary = necessary;
            return this;
        }

        public ConverterBuilder<SERIALIZATION> commitIn(
                Converter5<ParaVal<TARGET1>, ParaVal<TARGET2>, ParaVal<TARGET3>,
                        ParaVal<TARGET4>, ParaVal<TARGET5>, SERIALIZATION> converter) {
            tempSolution.add(element1);
            tempSolution.add(element2);
            tempSolution.add(element3);
            tempSolution.add(element4);
            tempSolution.add(element5);
            tempSolution.withIn(converterIn);
            essentialSolutions.add(tempSolution);
            return ConverterBuilder.this;
        }
    }

    public <A extends Annotation> AttributeSolution<Object, A> attribute(Class<A> annotationType) {
        return new AttributeSolution<>(Object.class, annotationType);
    }

    public <T, A extends Annotation> AttributeSolution<T, A> attribute(Class<A> annotationType,
                                                                       Class<T> valueType) {
        return new AttributeSolution<>(valueType, annotationType);
    }

    abstract void onCommit(ConvertSolution builder);

    public interface Accumulator<V, R> {
        R advance(R old, ParaVal<V> more);
    }

    interface ParaVal<V> {
        <A extends Annotation> A getAnnotation();
        Cartrofit.Parameter getParameter();
        V get();
    }

    public class AttributeSolution<V, A extends Annotation> {
        Class<V> moreType;
        Class<A> annotationType;
        Accumulator<V, SERIALIZATION> accumulator;

        AttributeSolution(Class<V> moreType, Class<A> annotationType) {
            this.moreType = moreType;
            this.annotationType = annotationType;
        }

        public ConverterBuilder<SERIALIZATION> commitIn(Accumulator<V, SERIALIZATION> accumulator) {
            this.accumulator = accumulator;
            return ConverterBuilder.this;
        }
    }

    static class InitElement<T> {
        Class<T> fixedType;
        Class<? extends Annotation> fixedAnnotationType;
        boolean annotateNecessary;

        InitElement(Class<T> fixedType) {
            this.fixedType = fixedType;
        }
    }

    class EssentialSolution {
        boolean simpleProviderMode;
        ArrayList<InitElement<?>> fixedElements;

        Converter<Object, SERIALIZATION> converterIn;

        Converter<SERIALIZATION, Object> converterOut;

        TwoWayConverter<Object, SERIALIZATION> convertTwoWay;

        void simpleProvide(Supplier<SERIALIZATION> supplier) {
            simpleProviderMode = true;
            fixedElements = null;
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

        void withIn(Converter<?, SERIALIZATION> converterIn) {
            this.converterIn = (Converter<Object, SERIALIZATION>) converterIn;
        }

        void withOut(Converter<SERIALIZATION, ?> converterIn) {
            this.converterOut = (Converter<SERIALIZATION, Object>) converterIn;
        }

        void withBoth(TwoWayConverter<?, SERIALIZATION> converter) {
            this.convertTwoWay = (TwoWayConverter<Object, SERIALIZATION>) converter;
        }
    }

    private class OutputConverterImpl implements Converter<Union, SERIALIZATION> {

        ArrayList<Cartrofit.Parameter> initializeParameters;
        ArrayList<Annotation> initializeAnnotationArrayList;
        Converter<Object, SERIALIZATION> initializer;

        ArrayList<Cartrofit.Parameter> accumulatedParameters;
        ArrayList<Annotation> accumulatedAnnotationArrayList;
        ArrayList<Accumulator<Object, SERIALIZATION>> accumulators;

        class ParameterValueImpl implements ParaVal<Object> {

            int index;
            Union parameterHost;
            ArrayList<Cartrofit.Parameter> parameters;
            ArrayList<Annotation> annotations;

            ParameterValueImpl(int index, Union parameterHost, boolean accumulates) {
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
                                   AttributeSolution<?, ?> solution) {
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
                rawMaterial = initializer.convert(new ParameterValueImpl(0, value, false));
            } else {
                ParaVal<?>[] result = new ParaVal[inputCount];
                for (int i = 0; i < inputCount; i++) {
                    result[i] = new ParameterValueImpl(i, value, false);
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
                        new ParameterValueImpl(i, value, true));
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
