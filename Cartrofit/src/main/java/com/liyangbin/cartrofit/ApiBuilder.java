package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Function2;
import com.liyangbin.cartrofit.funtion.Function3;
import com.liyangbin.cartrofit.funtion.Function4;
import com.liyangbin.cartrofit.funtion.Function5;

import java.util.Objects;
import java.util.function.Predicate;

public abstract class ApiBuilder {

    public abstract void setDefaultAreaId(int areaId);

    public abstract void setDefaultStickyType(StickyType stickyType);

    public abstract ApiBuilder intercept(Interceptor interceptor);

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

        public ApiBuilder filter(Predicate<T> predicate) {
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

    abstract ApiBuilder convert(AbsConverterBuilder builder);

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

    public abstract void apply(Constraint... constraints);

    abstract static class AbsConverterBuilder {
        Class<?> classFrom;
        Class<?>[] combineFromArray;
        Class<?> classTo;
        Converter<?, ?> converter;

        private AbsConverterBuilder(Class<?>... clazzArray) {
            if (clazzArray.length > 1) {
                this.combineFromArray = clazzArray;
            } else {
                this.classFrom = clazzArray[0];
            }
        }

        void apply(Cartrofit.ConverterStore store) {
            if (combineFromArray != null) {
                store.addConverter(Object[].class, classTo, converter);
            } else {
                store.addConverter(classFrom, classTo, converter);
            }
        }
    }

    public final class ConverterBuilder<FROM> extends AbsConverterBuilder  {

        private ConverterBuilder(Class<FROM> fromClazz) {
            super(fromClazz);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public final class ConverterBuilderTo<TO> {
            public ApiBuilder by(Converter<FROM, TO> converter) {
                ConverterBuilder.this.converter = converter;
                return ApiBuilder.this.convert(ConverterBuilder.this);
            }
        }
    }

    public final class ConverterBuilder2<FROM1, FROM2> extends AbsConverterBuilder {

        private ConverterBuilder2(Class<FROM1> fromClazz1, Class<FROM2> fromClazz2) {
            super(fromClazz1, fromClazz2);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public final class ConverterBuilderTo<TO> {
            public ApiBuilder by(Function2<FROM1, FROM2, TO> function2) {
                ConverterBuilder2.this.converter = function2;
                return ApiBuilder.this.convert(ConverterBuilder2.this);
            }
        }
    }

    public final class ConverterBuilder3<FROM1, FROM2, FROM3> extends AbsConverterBuilder {

        private ConverterBuilder3(Class<FROM1> fromClazz1, Class<FROM2> fromClazz2, Class<FROM3> fromClazz3) {
            super(fromClazz1, fromClazz2, fromClazz3);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public final class ConverterBuilderTo<TO> {
            public ApiBuilder by(Function3<FROM1, FROM2, FROM3, TO> function3) {
                ConverterBuilder3.this.converter = function3;
                return ApiBuilder.this.convert(ConverterBuilder3.this);
            }
        }
    }

    public final class ConverterBuilder4<FROM1, FROM2, FROM3, FROM4> extends AbsConverterBuilder {

        private ConverterBuilder4(Class<FROM1> fromClazz1, Class<FROM2> fromClazz2, Class<FROM3> fromClazz3, Class<FROM4> fromClazz4) {
            super(fromClazz1, fromClazz2, fromClazz3, fromClazz4);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public final class ConverterBuilderTo<TO> {
            public ApiBuilder by(Function4<FROM1, FROM2, FROM3, FROM4, TO> function4) {
                ConverterBuilder4.this.converter = function4;
                return ApiBuilder.this.convert(ConverterBuilder4.this);
            }
        }
    }

    public final class ConverterBuilder5<FROM1, FROM2, FROM3, FROM4, FROM5> extends AbsConverterBuilder {

        private ConverterBuilder5(Class<FROM1> fromClazz1, Class<FROM2> fromClazz2,
                                  Class<FROM3> fromClazz3, Class<FROM4> fromClazz4, Class<FROM5> fromClazz5) {
            super(fromClazz1, fromClazz2, fromClazz3, fromClazz4, fromClazz5);
        }

        public <TO> ConverterBuilderTo<TO> to(Class<TO> clazz) {
            classTo = clazz;
            return new ConverterBuilderTo<>();
        }

        public final class ConverterBuilderTo<TO> {
            public ApiBuilder by(Function5<FROM1, FROM2, FROM3, FROM4, FROM5, TO> function5) {
                ConverterBuilder5.this.converter = function5;
                return ApiBuilder.this.convert(ConverterBuilder5.this);
            }
        }
    }

    public static final class Constraint {
        public static final Constraint ALL = new Constraint();
        int priority;
        int apiId;
        String category;
        CommandType type;

        private Constraint() {
        }

        public static Constraint of(int apiId) {
            Constraint constraint = new Constraint();
            if (apiId == 0) {
                throw new IllegalArgumentException("Invalid apiId:" + apiId);
            }
            constraint.apiId = apiId;
            return constraint;
        }

        public static Constraint of(String category) {
            Constraint constraint = new Constraint();
            constraint.category = Objects.requireNonNull(category);
            return constraint;
        }

        public static Constraint of(CommandType type) {
            Constraint constraint = new Constraint();
            constraint.type = Objects.requireNonNull(type);
            return constraint;
        }

        public Constraint and(int apiId) {
            if (apiId == 0) {
                throw new IllegalArgumentException("Invalid apiId:" + apiId);
            }
            this.apiId = apiId;
            return this;
        }

        public Constraint and(String category) {
            this.category = Objects.requireNonNull(category);
            return this;
        }

        public Constraint and(CommandType type) {
            this.type = Objects.requireNonNull(type);
            return this;
        }

        public Constraint priority(int priority) {
            this.priority = priority;
            return this;
        }

        boolean check(Command command) {
            if (this == ALL) {
                return true;
            }
            final CommandType thatType = command.getType();
            if (type == null && (thatType == CommandType.STICKY_GET
                    || thatType == CommandType.RECEIVE)) {
                return false;
            }
            if (apiId != 0 && command.getId() != apiId) {
                return false;
            }
            if (type != null && type != thatType) {
                return false;
            }
            if (category != null) {
                String[] commandCategory = command.getCategory();
                if (commandCategory != null) {
                    for (String category : commandCategory) {
                        if (this.category.equals(category)) {
                            return true;
                        }
                    }
                }
                return false;
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Constraint that = (Constraint) o;

            if (apiId != that.apiId) return false;
            if (!Objects.equals(category, that.category))
                return false;
            return type == that.type;
        }

        @Override
        public int hashCode() {
            int result = apiId;
            result = 31 * result + (category != null ? category.hashCode() : 0);
            result = 31 * result + (type != null ? type.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Constraint{" +
                    "apiId=" + apiId +
                    ", category='" + category + '\'' +
                    ", type=" + type +
                    '}';
        }
    }
}
