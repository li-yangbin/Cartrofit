package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.WrappedData;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

public final class Cartrofit {

    private Cartrofit() {
    }

    static final ContextEnvironment DEFAULT_ENVIRONMENT = new ContextEnvironment("Cartrofit-default");
    static final HashMap<Class<?>, FlowConverter<?>> FLOW_CONVERTER_MAP = new HashMap<>();
    static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();
    public static final Object SKIP = new Object();

    public static <T> T from(Class<T> apiClass) {
        return DEFAULT_ENVIRONMENT.from(apiClass);
    }

    public static <T extends CartrofitContext<?>> T defaultContextOf(Class<?> apiClass) {
        return (T) DEFAULT_ENVIRONMENT.findContext(ContextEnvironment.getApi(apiClass));
    }

    public static <T extends CartrofitContext<?>> T getContext(Object apiObj) {
        try {
            ContextEnvironment.IProxyExt proxyExt = (ContextEnvironment.IProxyExt) apiObj;
            return (T) proxyExt.getContext();
        } catch (ClassCastException castError) {
            throw new IllegalArgumentException("Can not resolve context from api object:" + apiObj);
        }
    }

    public static <A extends Annotation> void register(CartrofitContext<A> context) {
        DEFAULT_ENVIRONMENT.add(context);
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

    private static Class<?> findFlowConverterTarget(FlowConverter<?> converter) {
        Objects.requireNonNull(converter);
        Class<?> implementsBy = ContextEnvironment.findImplement(converter.getClass(), FlowConverter.class);
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
                    Class<?> resultType = getClassFromType(converterDeclaredType[0]);
                    if (wrappedData != null) {
                        WRAPPER_CLASS_MAP.put(resultType, wrappedData.value());
                    }
                    return resultType;
                }
            }
        }

        throw new CartrofitGrammarException("invalid converter class:" + implementsBy
                + " type:" + Arrays.toString(ifTypes));
    }
}
