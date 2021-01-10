package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.Converter2;
import com.liyangbin.cartrofit.funtion.Converter3;
import com.liyangbin.cartrofit.funtion.Converter4;
import com.liyangbin.cartrofit.funtion.Converter5;
import com.liyangbin.cartrofit.funtion.TwoWayConverter;
import com.liyangbin.cartrofit.funtion.TwoWayConverter2;
import com.liyangbin.cartrofit.funtion.TwoWayConverter3;
import com.liyangbin.cartrofit.funtion.TwoWayConverter4;
import com.liyangbin.cartrofit.funtion.TwoWayConverter5;
import com.liyangbin.cartrofit.funtion.Union;
import com.liyangbin.cartrofit.funtion.Union2;
import com.liyangbin.cartrofit.funtion.Union3;
import com.liyangbin.cartrofit.funtion.Union4;
import com.liyangbin.cartrofit.funtion.Union5;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Supplier;

public abstract class ConverterBuilder<SERIALIZATION> {

    private Class<SERIALIZATION> serializeType;

    ConverterBuilder(Class<SERIALIZATION> serializeType) {
        this.serializeType = serializeType;
    }

    abstract void onCommit(ConvertSolution builder);

    public final ConverterBuilder0 provide(Supplier<SERIALIZATION> provider) {
        return new ConverterBuilder0<>(provider);
    }

    public final <FROM> ConverterBuilder1<FROM> convert(Class<FROM> clazz) {
        return new ConverterBuilder1<>(clazz);
    }

    public final <FROM1, FROM2> ConverterBuilder2<FROM1, FROM2> convert(Class<FROM1> clazz1,
                                                                        Class<FROM2> clazz2) {
        return new ConverterBuilder2<>(clazz1, clazz2);
    }

    public final <FROM1, FROM2, FROM3> ConverterBuilder3<FROM1, FROM2, FROM3>
            convert(Class<FROM1> clazz1, Class<FROM2> clazz2, Class<FROM3> clazz3) {
        return new ConverterBuilder3<>(clazz1, clazz2, clazz3);
    }

    public final <FROM1, FROM2, FROM3, FROM4> ConverterBuilder4<FROM1, FROM2, FROM3, FROM4>
            convert(Class<FROM1> clazz1, Class<FROM2> clazz2, Class<FROM3> clazz3, Class<FROM4> clazz4) {
        return new ConverterBuilder4<>(clazz1, clazz2, clazz3, clazz4);
    }

    public final <FROM1, FROM2, FROM3, FROM4, FROM5> ConverterBuilder5<FROM1, FROM2, FROM3, FROM4, FROM5>
            convert(Class<FROM1> clazz1, Class<FROM2> clazz2, Class<FROM3> clazz3, Class<FROM4> clazz4,
                    Class<FROM5> clazz5) {
        return new ConverterBuilder5<>(clazz1, clazz2, clazz3, clazz4, clazz5);
    }

    public final ConverterBuilderArray convert(Class<?>[] clazzArray) {
        return new ConverterBuilderArray(clazzArray);
    }

    public interface Accumulator<V, R, A extends Annotation> {
        R advance(R old, A annotation, V more);
    }

    static class AccumulateSolution<V, R, A extends Annotation> {
        Class<V> moreType;
        Class<A> annotateType;
        Accumulator<V, R, A> accumulator;
        int minMatchCount;
        int maxMatchCount = 100;
    }

    class OutputConverter implements Converter<Union, SERIALIZATION> {
        @Override
        public SERIALIZATION convert(Union value) {
            return null;
        }
    }

//    static class InitializeSolution<V, R> {
//        Class<V> moreType;
//        Class<? extends Annotation> annotateType;
//        Accumulator<V, R> accumulator;
//    }

    abstract class ConvertSolution {

        Class<? extends Annotation>[] initialedAnnotationArray;
        Class<? extends Annotation>[] accumulatedAnnotationArray;
        Class<? extends Annotation>[] extraAnnotationArray;
        ArrayList<AccumulateSolution<?, ?, ?>> accumulateSolutions;
//        HashMap<Class<?>>

        boolean takeParameterGroup(Cartrofit.ParameterGroup group) {
            int initialBits = 0;
            int accumulatedBits = 0;
            int extraBits = 0;
            anchor: for (int i = 0; i < group.getParameterCount(); i++) {
                Cartrofit.Parameter parameter = group.getParameterAt(i);
                if (initialedAnnotationArray != null) {
                    for (Class<? extends Annotation> initialMark : initialedAnnotationArray) {
                        if (parameter.isAnnotationPresent(initialMark)) {
                            initialBits |= 1 << i;
                            continue anchor;
                        }
                    }
                }
                if (accumulatedAnnotationArray != null) {
                    for (Class<? extends Annotation> accumulatedMark : accumulatedAnnotationArray) {
                        if (parameter.isAnnotationPresent(accumulatedMark)) {
                            accumulatedBits |= 1 << i;
                            continue anchor;
                        }
                    }
                }
                if (extraAnnotationArray != null) {
                    for (Class<? extends Annotation> extraMark : extraAnnotationArray) {
                        if (parameter.isAnnotationPresent(extraMark)) {
                            extraBits |= 1 << i;
                            continue anchor;
                        }
                    }
                }
                initialBits |= 1 << i;
            }
            if ((concernedTypes == null && initialBits != 0)
                    || Integer.bitCount(initialBits) != concernedTypes.length) {
                return false;
            }
            for (int i = 0, j = 0; i < group.getParameterCount(); i++) {
                if ((initialBits & i) != 0) {
                    if (!Cartrofit.classEquals(group.getParameterAt(i).getType(), concernedTypes[j++])) {
                        return false;
                    }
                }
            }
            int accumulateSolutionCount = accumulateSolutions != null ?
                    accumulateSolutions.size() : 0;
            for (int i = 0; i < accumulateSolutionCount; i++) {
                AccumulateSolution<?, ?, ?> accumulateSolution = accumulateSolutions.get(i);
                int matchCount = 0;
                for (int j = 0; j < group.getParameterCount(); j++) {
                    if ((accumulatedBits & j) != 0) {
                        Cartrofit.Parameter parameter = group.getParameterAt(j);
                        if (parameter.isAnnotationPresent(accumulateSolution.annotateType)
                                && accumulateSolution.moreType.isAssignableFrom(parameter.getType())) {
                            matchCount++;
                        }
                    }
                }
                if (matchCount >= accumulateSolution.minMatchCount
                        && matchCount <= accumulateSolution.maxMatchCount) {

                }
            }
            if ((accumulateSolutions == null || accumulateSolutions.size() == 0)
                    && accumulatedBits != 0) {
                return false;
            }
            return false;
        }

        Class<?>[] concernedTypes;


        Converter<?, SERIALIZATION> converterIn;
        Converter<SERIALIZATION, ?> converterOut;

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

    public final class ConverterBuilder0 extends ConvertSolution {
//        Supplier
    }

    public final class ConverterBuilder1<TARGET> extends ConvertSolution {

        private ConverterBuilder1(Class<TARGET> fromClazz) {
            super(fromClazz);
        }

        public void in(Converter<TARGET, SERIALIZATION> converter) {
            ConverterBuilder1.this.converterIn = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder1.this);
        }

        public void out(Converter<SERIALIZATION, TARGET> converter) {
            ConverterBuilder1.this.converterOut = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder1.this);
        }

        public void inout(TwoWayConverter<TARGET, SERIALIZATION> converter) {
            ConverterBuilder1.this.twoWayConverter = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder1.this);
        }

    }

    public final class ConverterBuilder2<TARGET1, TARGET2> extends ConvertSolution {

        private ConverterBuilder2(Class<TARGET1> fromClazz1, Class<TARGET2> fromClazz2) {
            super(fromClazz1, fromClazz2);
        }

        public void in(Converter2<TARGET1, TARGET2, SERIALIZATION> converter) {
            ConverterBuilder2.this.converterIn = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder2.this);
        }

        public void out(Converter<SERIALIZATION, Union2<TARGET1, TARGET2>> converter) {
            ConverterBuilder2.this.converterOut = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder2.this);
        }

        public void inout(TwoWayConverter2<TARGET1, TARGET2, SERIALIZATION> converter) {
            ConverterBuilder2.this.twoWayConverter = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder2.this);
        }
    }

    public final class ConverterBuilder3<TARGET1, TARGET2, TARGET3> extends ConvertSolution {

        private ConverterBuilder3(Class<TARGET1> fromClazz1, Class<TARGET2> fromClazz2,
                                  Class<TARGET3> fromClazz3) {
            super(fromClazz1, fromClazz2, fromClazz3);
        }

        public void in(Converter3<TARGET1, TARGET2, TARGET3, SERIALIZATION> converter) {
            ConverterBuilder3.this.converterIn = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder3.this);
        }

        public void out(Converter<SERIALIZATION, Union3<TARGET1, TARGET2, TARGET3>> converter) {
            ConverterBuilder3.this.converterOut = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder3.this);
        }

        public void inout(TwoWayConverter3<TARGET1, TARGET2, TARGET3, SERIALIZATION> converter) {
            ConverterBuilder3.this.twoWayConverter = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder3.this);
        }
    }

    public final class ConverterBuilder4<TARGET1, TARGET2, TARGET3, TARGET4> extends ConvertSolution {

        private ConverterBuilder4(Class<TARGET1> fromClazz1, Class<TARGET2> fromClazz2,
                                  Class<TARGET3> fromClazz3, Class<TARGET4> fromClazz4) {
            super(fromClazz1, fromClazz2, fromClazz3, fromClazz4);
        }

        public void in(Converter4<TARGET1, TARGET2, TARGET3, TARGET4, SERIALIZATION> converter) {
            ConverterBuilder4.this.converterIn = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder4.this);
        }

        public void out(Converter<SERIALIZATION, Union4<TARGET1, TARGET2, TARGET3, TARGET4>> converter) {
            ConverterBuilder4.this.converterOut = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder4.this);
        }

        public void inout(TwoWayConverter4<TARGET1, TARGET2, TARGET3, TARGET4, SERIALIZATION> converter) {
            ConverterBuilder4.this.twoWayConverter = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder4.this);
        }
    }

    public final class ConverterBuilder5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5> extends ConvertSolution {

        private ConverterBuilder5(Class<TARGET1> fromClazz1, Class<TARGET2> fromClazz2,
                                  Class<TARGET3> fromClazz3, Class<TARGET4> fromClazz4,
                                  Class<TARGET5> fromClazz5) {
            super(fromClazz1, fromClazz2, fromClazz3, fromClazz4, fromClazz5);
        }

        public void in(Converter5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5, SERIALIZATION> converter) {
            ConverterBuilder5.this.converterIn = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder5.this);
        }

        public void out(Converter<SERIALIZATION, Union5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5>> converter) {
            ConverterBuilder5.this.converterOut = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder5.this);
        }

        public void inout(TwoWayConverter5<TARGET1, TARGET2, TARGET3, TARGET4, TARGET5, SERIALIZATION> converter) {
            ConverterBuilder5.this.twoWayConverter = converter;
            ConverterBuilder.this.onCommit(ConverterBuilder5.this);
        }
    }

    public final class ConverterBuilderArray extends ConvertSolution {

        private ConverterBuilderArray(Class<?>[] classArray) {
            super(classArray);
        }

        public void in(Converter<Union, SERIALIZATION> converter) {
            ConverterBuilderArray.this.converterIn = converter;
            ConverterBuilder.this.onCommit(ConverterBuilderArray.this);
        }

        public void out(Converter<SERIALIZATION, Union> converter) {
            ConverterBuilderArray.this.converterOut = converter;
            ConverterBuilder.this.onCommit(ConverterBuilderArray.this);
        }

        public void inout(TwoWayConverter<Union, SERIALIZATION> converter) {
            ConverterBuilderArray.this.twoWayConverter = converter;
            ConverterBuilder.this.onCommit(ConverterBuilderArray.this);
        }
    }
}
