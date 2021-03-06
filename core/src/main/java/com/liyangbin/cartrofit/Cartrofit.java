package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.solution.SolutionProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Cartrofit {

    private Cartrofit() {
    }

    private static final HashMap<Class<?>, ApiRecord<?>> API_CACHE = new HashMap<>();
    private static final HashMap<Class<? extends Annotation>, ContextFactory<?>>
            CONTEXT_FACTORY_CACHE = new HashMap<>();
    static final HashMap<Class<?>, FlowConverter<?>> FLOW_CONVERTER_MAP = new HashMap<>();
    static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();
    public static final Object SKIP = new Object();

    static {
        CONTEXT_FACTORY_CACHE.put(Delegate.class,
                new ContextFactory<>(Delegate.class, DelegateContext::new));
    }

    public static <T> T from(Class<T> apiClass) {
        final ApiRecord<T> apiRecord = getApi(apiClass, true);
        return contextOf(apiRecord).from(apiRecord);
    }

    public static <T extends CartrofitContext> T contextOf(Class<?> apiClass) {
        return contextOf(getApi(apiClass));
    }

    @SuppressWarnings("unchecked")
    static <T extends CartrofitContext> T contextOf(ApiRecord<?> apiRecord) {
        ContextFactory<?> factory = CONTEXT_FACTORY_CACHE.get(apiRecord.scopeType);
        CartrofitContext context = factory != null ? factory.get(apiRecord.scopeObj) : null;
        return (T) Objects.requireNonNull(context,
                "Can not find context provider for:" + apiRecord.clazz);
    }

    private static class Singleton {
        private Supplier<? extends CartrofitContext> initProvider;
        private CartrofitContext instance;

        Singleton(Supplier<? extends CartrofitContext> initProvider) {
            this.initProvider = initProvider;
        }

        CartrofitContext get() {
            if (instance == null) {
                synchronized (this) {
                    if (instance == null) {
                        instance = Objects.requireNonNull(initProvider.get());
                        initProvider = null;
                    }
                }
            }
            return instance;
        }
    }

    public static <A extends Annotation> void registerAsSingleton(Class<A> keyType,
                                                                  Supplier<CartrofitContext> provider) {
        CONTEXT_FACTORY_CACHE.put(keyType, new ContextFactory<>(keyType, provider));
    }

    public static <A extends Annotation> ContextFactory<A> createSingletonFactory(Class<A> keyType,
                                                                                  Function<A, ?> keyMapper) {
        ContextFactory<A> factory = new ContextFactory<>(keyType, keyMapper);
        CONTEXT_FACTORY_CACHE.put(keyType, factory);
        return factory;
    }

    public static final class ContextFactory<A extends Annotation> {
        private Function<A, ?> keyMapper;
        private Class<A> keyType;
        private Singleton normalSingleton;
        private HashMap<Object, Singleton> mappedSingleton = new HashMap<>();

        ContextFactory(Class<A> keyType, Supplier<CartrofitContext> provider) {
            this.keyType = Objects.requireNonNull(keyType);
            this.normalSingleton = new Singleton(provider);
        }

        ContextFactory(Class<A> keyType, Function<A, ?> keyMapper) {
            this.keyType = Objects.requireNonNull(keyType);
            this.keyMapper = Objects.requireNonNull(keyMapper);
        }

        CartrofitContext get(Annotation keySrc) {
            if (normalSingleton != null) {
                return normalSingleton.get();
            }
            if (!keyType.equals(keySrc.annotationType())) {
                return null;
            }
            Object key = keyMapper.apply((A) keySrc);
            Singleton mappedProvider = mappedSingleton.get(key);
            return mappedProvider != null ? mappedProvider.get() : null;
        }

        public void register(Object key, Supplier<? extends CartrofitContext> provider) {
            mappedSingleton.put(key, new Singleton(provider));
        }
    }

    private static class DelegateContext extends CartrofitContext {

        @Override
        public SolutionProvider onProvideCallSolution() {
            return new SolutionProvider();
        }

        @Override
        public Call onCreateCall(Key key, int category) {
            if (!key.isAnnotationPresent(Delegate.class)) {
                throw new CartrofitGrammarException("Must specify type annotation on "
                        + key.record.clazz);
            }
            return super.onCreateCall(key, category);
        }
    }

    static <T> ApiRecord<T> getApi(Class<T> apiClass) {
        return getApi(apiClass, false);
    }

    static <T> ApiRecord<T> getApi(Class<T> apiClass, boolean allowEmpty) {
        synchronized (CartrofitContext.class) {
            @SuppressWarnings("unchecked")
            ApiRecord<T> singletonRecord = (ApiRecord<T>) API_CACHE.get(apiClass);
            if (singletonRecord == null) {
                Class<?> loopedClass = apiClass;
                anchor: do {
                    Annotation[] annotations = loopedClass.getDeclaredAnnotations();
                    for (Annotation annotation : annotations) {
                        if (CONTEXT_FACTORY_CACHE.containsKey(annotation.annotationType())) {
                            API_CACHE.put(apiClass, singletonRecord = new ApiRecord<>(annotation,
                                    annotation.annotationType(), apiClass));
                            break anchor;
                        }
                    }
                    loopedClass = loopedClass.getEnclosingClass();
                } while (loopedClass != null);
            }
            if (allowEmpty && singletonRecord == null) {
                singletonRecord = new ApiRecord<>(null, Delegate.class, apiClass);
            }
            if (singletonRecord == null) {
                throw new CartrofitGrammarException("Failed to find valid context annotation from:" + apiClass);
            }
            return singletonRecord;
        }
    }

    public static FlowConverter<?> findFlowConverter(Class<?> target) {
        return FLOW_CONVERTER_MAP.get(target);
    }

    public static void addGlobalConverter(FlowConverter<?>... converters) {
        for (FlowConverter<?> converter : converters) {
            FLOW_CONVERTER_MAP.put(findFlowConverterTarget(converter), converter);
        }
    }

    static Class<?> getClassFromType(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return getClassFromType(parameterizedType.getRawType());
        } else if (type instanceof Class) {
            return (Class<?>) type;
        }
        return null;
    }

    private static Class<?> lookUp(Class<?> clazz, Class<?> lookForTarget) {
        if (clazz == lookForTarget) {
            throw new CartrofitGrammarException("Invalid parameter:" + clazz);
        }
        if (!lookForTarget.isAssignableFrom(clazz)) {
            return null;
        }
        Class<?>[] ifClazzArray = clazz.getInterfaces();
        for (Class<?> ifClazz : ifClazzArray){
            if (ifClazz == lookForTarget) {
                return clazz;
            } else if (lookForTarget.isAssignableFrom(ifClazz)) {
                Class<?>[] ifParents = ifClazz.getInterfaces();
                for (Class<?> subIfClazz : ifParents){
                    if (subIfClazz == lookForTarget) {
                        return ifClazz;
                    }
                }
                for (Class<?> subIfClazz : ifParents){
                    Class<?> extendsBy = lookUp(subIfClazz, lookForTarget);
                    if (extendsBy != null) {
                        return extendsBy;
                    }
                }
            }
        }
        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz != null && superClazz != Object.class) {
            return lookUp(superClazz, lookForTarget);
        } else {
            return null;
        }
    }

    private static Class<?> findFlowConverterTarget(FlowConverter<?> converter) {
        Objects.requireNonNull(converter);
        Class<?> implementsBy = lookUp(converter.getClass(), FlowConverter.class);
        if (implementsBy == null) {
            throw new CartrofitGrammarException("invalid input converter:" + converter.getClass());
        }
        if (implementsBy.isSynthetic()) {
            throw new CartrofitGrammarException("Do not use lambda expression in addConverter()");
        }
        Type[] ifTypes = implementsBy.getGenericInterfaces();
        for (Type type : ifTypes) {
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                if (parameterizedType.getRawType() == FlowConverter.class) {
                    Type[] converterDeclaredType = parameterizedType.getActualTypeArguments();
                    WrappedData wrappedData = implementsBy.getDeclaredAnnotation(WrappedData.class);
                    if (wrappedData != null) {
                        WRAPPER_CLASS_MAP.put(implementsBy, wrappedData.type());
                    }
                    return getClassFromType(converterDeclaredType[0]);
                }
            }
        }

        throw new CartrofitGrammarException("invalid converter class:" + implementsBy
                + " type:" + Arrays.toString(ifTypes));
    }
}
