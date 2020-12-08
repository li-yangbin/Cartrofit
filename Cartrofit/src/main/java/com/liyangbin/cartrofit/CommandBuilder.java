package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Consumer2;
import com.liyangbin.cartrofit.funtion.Function2;
import com.liyangbin.cartrofit.funtion.Function3;
import com.liyangbin.cartrofit.funtion.Function4;
import com.liyangbin.cartrofit.funtion.Function5;

import java.lang.annotation.Annotation;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class CommandBuilder {

    public abstract void setDefaultAreaId(int areaId);

    public abstract void setDefaultStickyType(StickyType stickyType);

    public abstract CommandBuilder intercept(Interceptor interceptor);

    public final <T> PredicateBuilder<T> checkInput(Class<T> target) {
        return new PredicateBuilder<>(target, true);
    }

    public final class PredicateBuilder<T> {
        private final Class<T> targetClass;
        private final boolean checkInput;

        private PredicateBuilder(Class<T> clazz, boolean checkInput) {
            targetClass = clazz;
            this.checkInput = checkInput;
        }

        public CommandBuilder filter(Predicate<T> predicate) {
            return intercept(new Interceptor() {
                @SuppressWarnings("unchecked")
                @Override
                public Object process(Command command, Object parameter) {
                    return predicate.test((T) parameter) ? command.invoke(parameter) : null;
                }

                @Override
                public boolean checkCommand(Command command) {
                    Class<?> classToBeChecked = checkInput ? command.getInputType() : command.getOutputType();
                    return classToBeChecked == targetClass;
                }
            });
        }
    }

    abstract CommandBuilder convert(ConvertSolution builder);

    public final <FROM> ConverterBuilder<FROM> convert(Class<FROM> clazz) {
        return new ConverterBuilder<>(clazz);
    }

    public final <FROM1, FROM2> ConverterBuilder2<FROM1, FROM2> combine(Class<FROM1> clazz1, Class<FROM2> clazz2) {
        return new ConverterBuilder2<>(clazz1, clazz2);
    }

    public final <FROM1, FROM2, FROM3> ConverterBuilder3<FROM1, FROM2, FROM3>
            combine(Class<FROM1> clazz1, Class<FROM2> clazz2, Class<FROM3> clazz3) {
        return new ConverterBuilder3<>(clazz1, clazz2, clazz3);
    }

    public final <FROM1, FROM2, FROM3, FROM4> ConverterBuilder4<FROM1, FROM2, FROM3, FROM4>
            combine(Class<FROM1> clazz1, Class<FROM2> clazz2, Class<FROM3> clazz3, Class<FROM4> clazz4) {
        return new ConverterBuilder4<>(clazz1, clazz2, clazz3, clazz4);
    }

    public final <FROM1, FROM2, FROM3, FROM4, FROM5> ConverterBuilder5<FROM1, FROM2, FROM3, FROM4, FROM5>
            combine(Class<FROM1> clazz1, Class<FROM2> clazz2, Class<FROM3> clazz3, Class<FROM4> clazz4, Class<FROM5> clazz5) {
        return new ConverterBuilder5<>(clazz1, clazz2, clazz3, clazz4, clazz5);
    }

    public void applyToAll() {
        apply(Constraint.ALL);
    }

    public void apply(int... id) {
        if (id == null || id.length == 0) {
            return;
        }
        Constraint[] out = new Constraint[id.length];
        for (int i = 0; i < id.length; i++) {
            out[i] = Constraint.of(id[i]);
        }
        apply(out);
    }

    public abstract void apply(Constraint... constraints);

    abstract static class ConvertSolution {
//        Class<?> classFrom;
        Class<?>[] concernedTypes;
        Class<?> classTo;

        Class<?> classReverseFrom;
        Converter converter;
        Consumer converterReverse;

        Class<? extends Annotation>[] concernedAnnotations;

        Function<Object[], Object[]> inputResolver;

        private ConvertSolution(Class<?>... clazzArray) {
            this.concernedTypes = clazzArray;
        }

        void apply(Cartrofit.ConverterStore store) {
//            if (combineFromArray != null) {
//                store.addConverter(Object[].class, classTo, converter);
//            } else {
//                store.addConverter(classFrom, classTo, converter);
//            }
        }

        Converter<?, ?> findInputConverterByType(AnnotatedType<?, ?>[] typeCategory) {
            boolean typeMatch = true;
            if (concernedAnnotations != null) {
                typeMatch = concernedAnnotations.length == typeCategory.length;
                if (typeMatch) {
                    for (int i = 0; i < typeCategory.length && typeMatch; i++) {
                        Class<? extends Annotation>[] annotationPresent = typeCategory[i].annotationType;
                        if (annotationPresent != null && annotationPresent.length > 0) {
                            boolean found = false;
                            for (Class<? extends Annotation> aClass : annotationPresent) {
                                if (aClass == concernedAnnotations[i]) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                typeMatch = false;
                            }
                        } else if (concernedAnnotations[i] != null) {
                            typeMatch = false;
                        }
                    }
                }
            }
            if (typeMatch) {
                typeMatch = concernedTypes.length == typeCategory.length;
                for (int i = 0; i < concernedTypes.length && typeMatch; i++) {
                    if (!Cartrofit.classEquals(concernedTypes[i], typeCategory[i].valueType)) {
                        typeMatch = false;
                    }
                }
            }
            return typeMatch ? converter : null;
        }

        Converter<?, ?> findOutputConverterByType(Class<?> outputType) {
            if (concernedTypes.length == 1 && concernedTypes[0] == outputType) {
                return converter;
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

    static class AnnotatedType<A extends Annotation, T> implements AnnotatedValue<A, T> {

        static AnnotatedType sPool;
        static final int LIMIT = 10;
        static int sSize;

        Class<A>[] annotationType;
        Class<T> valueType;

        A annotation;
        T value;

        AnnotatedType next;

        private AnnotatedType(A annotation, T value) {
            this.annotation = annotation;
            this.value = value;
        }

        static AnnotatedValue obtain(Annotation annotation, Object value) {
            synchronized (AnnotatedType.class) {
                if (sPool == null) {
                    return new AnnotatedType(annotation, value);
                }
                AnnotatedType out = sPool;
                out.annotation = annotation;
                out.value = value;

                sPool = out.next;
                return out;
            }
        }

        @Override
        public A getAnnotation() {
            return annotation;
        }

        @Override
        public T get() {
            return value;
        }

        void recycle() {
            this.annotation = null;
            this.value = null;
            synchronized (AnnotatedType.class) {
                if (sSize > LIMIT) {
                    return;
                }
                this.next = sPool;
                sPool = this;
                sSize++;
            }
        }
    }

    public interface AnnotatedValue<A extends Annotation, T> {
        A getAnnotation();
        T get();
    }

    public final class ConverterBuilder<FROM> extends ConvertSolution {

        private ConverterBuilder(Class<FROM> fromClazz) {
            super(fromClazz);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public <TO> ConverterBuilderTo<TO> from(Class<TO> clazz) {
            classReverseFrom = clazz;
            return new ConverterBuilderTo<>();
        }

        public class ConverterBuilderTo<TO> {

            public <FROM_A extends Annotation> AnnotatedBuilderTo<FROM_A, TO> with(Class<FROM_A> clazz) {
                ConverterBuilder.this.saveAnnotation(clazz);
                return new AnnotatedBuilderTo<>();
            }

            public CommandBuilder by(Converter<FROM, TO> converter) {
                if (classReverseFrom != null) {
                    throw new CartrofitGrammarException("invalid input " + classReverseFrom);
                }
                ConverterBuilder.this.converter = converter;
                return CommandBuilder.this.convert(ConverterBuilder.this);
            }
        }

        public final class AnnotatedBuilderTo<FROM_A extends Annotation, TO> {
            public CommandBuilder by(Converter<AnnotatedValue<FROM_A, FROM>, TO> converter) {
                ConverterBuilder.this.converter = converter;
                return CommandBuilder.this.convert(ConverterBuilder.this);
            }

            public CommandBuilder by(BiConsumer<TO, AnnotatedValue<FROM_A, FROM>> converterReverse) {
                ConverterBuilder.this.converterReverse = converterReverse;
                return CommandBuilder.this.convert(ConverterBuilder.this);
            }
        }

        public class ConverterBuilderTo2<TO1, TO2> extends ConverterBuilderTo<TO1> {

            public CommandBuilder by(Consumer2<FROM, Consumer2<TO1, TO2>> converter) {
                if (classReverseFrom != null) {
                    throw new CartrofitGrammarException("invalid input " + classReverseFrom);
                }
                ConverterBuilder.this.converter = converter;
                return CommandBuilder.this.convert(ConverterBuilder.this);
            }
        }
    }

    public final class ConverterBuilder2<FROM1, FROM2> extends ConvertSolution {

        private ConverterBuilder2(Class<FROM1> fromClazz1, Class<FROM2> fromClazz2) {
            super(fromClazz1, fromClazz2);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public final class ConverterBuilderTo<TO> {

            public <FROM_A extends Annotation, FROM_B extends Annotation>
            AnnotatedBuilderTo<FROM_A, FROM_B, TO>
            with(Class<FROM_A> clazz1, Class<FROM_B> clazz2) {
                saveAnnotation(clazz1, clazz2);
                return new AnnotatedBuilderTo<>();
            }

            public CommandBuilder by(Function2<FROM1, FROM2, TO> function2) {
                ConverterBuilder2.this.converter = function2;
                return CommandBuilder.this.convert(ConverterBuilder2.this);
            }
        }

        public final class AnnotatedBuilderTo<FROM_A extends Annotation,
                FROM_B extends Annotation, TO> {
            public CommandBuilder by(Function2<AnnotatedValue<FROM_A, FROM1>,
                    AnnotatedValue<FROM_B, FROM2>, TO> converter) {
                ConverterBuilder2.this.converter = converter;
                return CommandBuilder.this.convert(ConverterBuilder2.this);
            }
        }
    }

    public final class ConverterBuilder3<FROM1, FROM2, FROM3> extends ConvertSolution {

        private ConverterBuilder3(Class<FROM1> fromClazz1, Class<FROM2> fromClazz2, Class<FROM3> fromClazz3) {
            super(fromClazz1, fromClazz2, fromClazz3);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public final class ConverterBuilderTo<TO> {
            public CommandBuilder by(Function3<FROM1, FROM2, FROM3, TO> function3) {
                ConverterBuilder3.this.converter = function3;
                return CommandBuilder.this.convert(ConverterBuilder3.this);
            }
        }
    }

    public final class ConverterBuilder4<FROM1, FROM2, FROM3, FROM4> extends ConvertSolution {

        private ConverterBuilder4(Class<FROM1> fromClazz1, Class<FROM2> fromClazz2, Class<FROM3> fromClazz3, Class<FROM4> fromClazz4) {
            super(fromClazz1, fromClazz2, fromClazz3, fromClazz4);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public final class ConverterBuilderTo<TO> {
            public CommandBuilder by(Function4<FROM1, FROM2, FROM3, FROM4, TO> function4) {
                ConverterBuilder4.this.converter = function4;
                return CommandBuilder.this.convert(ConverterBuilder4.this);
            }
        }
    }

    public final class ConverterBuilder5<FROM1, FROM2, FROM3, FROM4, FROM5> extends ConvertSolution {

        private ConverterBuilder5(Class<FROM1> fromClazz1, Class<FROM2> fromClazz2,
                                  Class<FROM3> fromClazz3, Class<FROM4> fromClazz4, Class<FROM5> fromClazz5) {
            super(fromClazz1, fromClazz2, fromClazz3, fromClazz4, fromClazz5);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public final class ConverterBuilderTo<TO> {
            public CommandBuilder by(Function5<FROM1, FROM2, FROM3, FROM4, FROM5, TO> function5) {
                ConverterBuilder5.this.converter = function5;
                return CommandBuilder.this.convert(ConverterBuilder5.this);
            }
        }
    }
}
