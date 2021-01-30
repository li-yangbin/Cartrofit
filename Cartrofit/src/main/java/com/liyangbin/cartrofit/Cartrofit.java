package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.ApiCategory;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.funtion.FlowConverter;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public final class Cartrofit {

    private Cartrofit() {
    }

    private static final HashMap<Class<?>, ApiRecord<?>> API_CACHE = new HashMap<>();
    private static final HashMap<Class<? extends Annotation>, Singleton> DEFAULT_CONTEXT_MAP = new HashMap<>();
    static final HashMap<Class<?>, FlowConverter<?>> FLOW_CONVERTER_MAP = new HashMap<>();
    static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();

    public static <T> T from(Class<T> clazz) {
        ApiRecord<T> apiRecord = getApi(clazz);
        Singleton contextSingleton = DEFAULT_CONTEXT_MAP.get(apiRecord.scopeType);
        if (contextSingleton != null) {
            AbsContext singletonContext = contextSingleton.get();
            return singletonContext.from(apiRecord);
        }
        throw new IllegalStateException("Can not find context provider for:" + apiRecord);
    }

    private static class Singleton {
        private Supplier<AbsContext> initProvider;
        private AbsContext instance;

        Singleton(Supplier<AbsContext> initProvider) {
            this.initProvider = initProvider;
        }

        AbsContext get() {
            if (instance == null) {
                synchronized (this) {
                    if (instance == null) {
                        instance = initProvider.get();
                        instance.createFromCartrofit();
                    }
                }
            }
            return instance;
        }
    }

    public static <A extends Annotation> void addContextProvider(Class<A> keyType,
                                                                 Supplier<AbsContext> provider) {
        DEFAULT_CONTEXT_MAP.put(keyType, new Singleton(provider));
    }

    static <T> ApiRecord<T> getApi(Class<T> apiClass) {
        synchronized (AbsContext.class) {
            ApiRecord<T> singletonRecord = (ApiRecord<T>) API_CACHE.get(apiClass);
            if (singletonRecord == null) {
                Class<?> loopedClass = apiClass;
                anchor: do {
                    Annotation[] annotations = apiClass.getDeclaredAnnotations();
                    for (Annotation annotation : annotations) {
                        Class<? extends Annotation> annotationType = annotation.annotationType();
                        if (annotationType.isAnnotationPresent(ApiCategory.class)) {
                            API_CACHE.put(apiClass, singletonRecord = new ApiRecord<>(annotation, apiClass));
                            break anchor;
                        }
                    }
                    loopedClass = loopedClass.getEnclosingClass();
                } while (loopedClass != null);
            }
            return Objects.requireNonNull(singletonRecord, "Declare ApiCategory on class annotation");
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

    static Class<?> findFlowConverterTarget(FlowConverter<?> converter) {
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
