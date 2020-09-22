package com.liyangbin.carretrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.carretrofit.annotation.Apply;
import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.CarValue;
import com.liyangbin.carretrofit.annotation.Combine;
import com.liyangbin.carretrofit.annotation.ConsiderSuper;
import com.liyangbin.carretrofit.annotation.Delegate;
import com.liyangbin.carretrofit.annotation.Get;
import com.liyangbin.carretrofit.annotation.Inject;
import com.liyangbin.carretrofit.annotation.Set;
import com.liyangbin.carretrofit.annotation.Track;
import com.liyangbin.carretrofit.annotation.UnTrack;
import com.liyangbin.carretrofit.annotation.WrappedData;
import com.liyangbin.carretrofit.funtion.Function2;
import com.liyangbin.carretrofit.funtion.Function3;
import com.liyangbin.carretrofit.funtion.Function4;
import com.liyangbin.carretrofit.funtion.Function5;
import com.liyangbin.carretrofit.funtion.Function6;
import com.liyangbin.carretrofit.funtion.Operator;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public final class CarRetrofit {

    private static final ConverterStore GLOBAL_CONVERTER = new ConverterStore(null);
    private static final Object SKIP = new Object();
    private static CarRetrofit sDefault;

    private static final int FLAG_FIRST_BIT = 1;

    private static final int FLAG_PARSE_SET = FLAG_FIRST_BIT;
    private static final int FLAG_PARSE_GET = FLAG_FIRST_BIT << 1;
    private static final int FLAG_PARSE_TRACK = FLAG_FIRST_BIT << 2;
    private static final int FLAG_PARSE_UN_TRACK = FLAG_FIRST_BIT << 3;
    private static final int FLAG_PARSE_INJECT = FLAG_FIRST_BIT << 4;
    private static final int FLAG_PARSE_APPLY = FLAG_FIRST_BIT << 5;
    private static final int FLAG_PARSE_COMBINE = FLAG_FIRST_BIT << 6;
    private static final int FLAG_PARSE_DELEGATE = FLAG_FIRST_BIT << 7;
    private static final int FLAG_PARSE_ALL = FLAG_PARSE_SET | FLAG_PARSE_GET | FLAG_PARSE_TRACK
            | FLAG_PARSE_UN_TRACK | FLAG_PARSE_INJECT | FLAG_PARSE_APPLY | FLAG_PARSE_COMBINE
            |FLAG_PARSE_DELEGATE;
    private static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();

    private HashMap<String, DataSource> mDataMap;
    private ConverterStore mConverterStore;
    private InterceptorChain mChainHead;
    private StickyType mDefaultStickyType;

    private final HashMap<Class<?>, ApiRecord<?>> mApiCache = new HashMap<>();
    private final HashMap<Key, CommandImpl> mCommandCache = new HashMap<>();

    static {
        RxJavaConverter.addSupport();
        ObservableConverter.addSupport();
        LiveDataConverter.addSupport();
    }

    private CarRetrofit(Builder builder) {
        mConverterStore = new ConverterStore("scope:" + this);
        mConverterStore.addParent(GLOBAL_CONVERTER);
        mDataMap = builder.dataMap;
        mDefaultStickyType = builder.stickyType;
        if (mDataMap.isEmpty()) {
            throw new IllegalArgumentException("CarRetrofit must be setup with data source");
        }
        mDataMap.put(CarApi.DUMMY_SCOPE, new DummyDataSource());
        for (int i = 0; i < builder.converters.size(); i++) {
            mConverterStore.addConverter(builder.converters.get(i));
        }
        for (int i = 0; i < builder.interceptors.size(); i++) {
            mChainHead = new InterceptorChain(mChainHead, builder.interceptors.get(i));
        }
    }

    private static class DummyDataSource implements DataSource {
        @Override
        public <VALUE> VALUE get(int key, int area, CarType type) {
            throw new IllegalStateException("impossible");
        }
        @Override
        public <VALUE> void set(int key, int area, VALUE value) {
            throw new IllegalStateException("impossible");
        }
        @Override
        public Flow<CarPropertyValue<?>> track(int key, int area) {
            throw new IllegalStateException("impossible");
        }
        @Override
        public <VALUE> Class<VALUE> extractValueType(int key) {
            throw new IllegalStateException("impossible");
        }
    }

    public static void setDefault(CarRetrofit retrofit) {
        sDefault = Objects.requireNonNull(retrofit);
    }

    public static CarRetrofit getDefault() {
        return Objects.requireNonNull(sDefault);
    }

    public static <T> T fromDefault(Class<T> api) {
        return Objects.requireNonNull(sDefault,
                "Call setDefault() before calling fromDefault()").from(api);
    }

    private static Class<?> getClassFromType(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return getClassFromType(parameterizedType.getRawType());
        } else if (type instanceof Class) {
            return (Class<?>) type;
        }
        return null;
    }

    private class ApiRecord<T> {
        private static final String INTERCEPTOR_PREFIX = "INTERCEPTOR";
        private static final String CONVERTER_PREFIX = "CONVERTER";

        Class<T> clazz;
        String dataScope;
        int apiArea;
        T apiObj;
        StickyType stickyType;

        ArrayList<Interceptor> apiSelfInterceptorList = new ArrayList<>();
        ArrayList<Converter<?, ?>> apiSelfConverterList = new ArrayList<>();

        DataSource source;
        InterceptorChain interceptorChain;
        ConverterStore converterStore;

        ArrayList<Key> childrenKey;
        ArrayList<ApiRecord<?>> parentApi;

        ApiRecord(Class<T> clazz) {
            this.clazz = clazz;
            CarApi carApi = Objects.requireNonNull(clazz.getAnnotation(CarApi.class));
            this.dataScope = carApi.scope();
            this.apiArea = carApi.area();
            this.stickyType = carApi.defaultSticky();
            if (stickyType == StickyType.NO_SET) {
                stickyType = mDefaultStickyType;
            }

            try {
                Field[] fields = clazz.getDeclaredFields();
                for (Field selfField : fields) {
                    if (Modifier.isStatic(selfField.getModifiers())) {
                        String name = selfField.getName();
                        if (name.startsWith(INTERCEPTOR_PREFIX)) {
                            apiSelfInterceptorList.add((Interceptor) selfField.get(null));
                        } else if (name.startsWith(CONVERTER_PREFIX)) {
                            apiSelfConverterList.add((Converter<?, ?>) selfField.get(null));
                        }
                    }
                }
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }

            interceptorChain = mChainHead;
            if (apiSelfInterceptorList.size() > 0) {
                for (int i = 0; i < apiSelfInterceptorList.size(); i++) {
                    interceptorChain = new InterceptorChain(interceptorChain,
                            apiSelfInterceptorList.get(i));
                }
            }

            if (apiSelfConverterList.size() > 0) {
                converterStore = new ConverterStore("api:" + clazz);
                for (int i = 0; i < apiSelfConverterList.size(); i++) {
                    converterStore.addConverter(apiSelfConverterList.get(i));
                }
                converterStore.addParent(mConverterStore);
            } else {
                converterStore = mConverterStore;
            }

            this.source = Objects.requireNonNull(mDataMap.get(dataScope), "Invalid scope:"
                    + dataScope + " make sure use a valid scope registered in Builder().addDataSource()");
        }

        ArrayList<Key> getChildKey() {
            if (childrenKey != null) {
                return childrenKey;
            }
            ArrayList<Key> result = new ArrayList<>();
            if (clazz.isInterface()) {
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    Key childKey = new Key(method);
                    if (!childKey.isInvalid()) {
                        result.add(childKey);
                    }
                }
            } else {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    Key childKey = new Key(field);
                    if (!childKey.isInvalid()) {
                        result.add(childKey);
                    }
                }
                Class<?> loopClass = clazz;
                while (loopClass.isAnnotationPresent(ConsiderSuper.class)) {
                    Class<?> superClass = loopClass.getSuperclass();
                    if (superClass != null && superClass != Object.class) {
                        ApiRecord<?> record = getApi(loopClass);
                        if (record == null) {
                            fields = superClass.getDeclaredFields();
                            for (Field field : fields) {
                                Key childKey = new Key(field);
                                if (!childKey.isInvalid()) {
                                    result.add(childKey);
                                }
                            }
                            loopClass = superClass;
                            continue;
                        }
                    }
                    break;
                }
            }
            childrenKey = result;
            return result;
        }

        ArrayList<ApiRecord<?>> getParentApi() {
            if (parentApi != null) {
                return parentApi;
            }
            ArrayList<ApiRecord<?>> result = new ArrayList<>();
            if (clazz.isInterface()) {
                Class<?>[] classSuperArray = clazz.getInterfaces();
                for (Class<?> clazz : classSuperArray) {
                    ApiRecord<?> record = getApi(clazz);
                    if (record != null) {
                        result.add(record);
                    }
                }
            } else {
                if (clazz.isAnnotationPresent(ConsiderSuper.class)) {
                    Class<?> superClass = clazz.getSuperclass();
                    if (superClass != null && superClass != Object.class) {
                        ApiRecord<?> record = getApi(clazz);
                        if (record != null) {
                            result.add(record);
                        }
                    }
                }
            }
            parentApi = result;
            return result;
        }

        @Override
        public String toString() {
            return "ApiRecord{" +
                    "api=" + clazz +
                    ", dataScope='" + dataScope + '\'' +
                    (apiArea != CarApi.DEFAULT_AREA_ID ?
                    ", apiArea=0x" + Integer.toHexString(apiArea) : "") +
                    '}';
        }
    }

    private static Class<?> lookUp(Class<?> clazz, Class<?> lookForTarget) {
        if (clazz == lookForTarget) {
            throw new CarRetrofitException("Invalid parameter:" + clazz);
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

    public static class ConverterStore {
        final ArrayList<ConverterWrapper<?, ?>> converterWrapperList = new ArrayList<>();
        final ArrayList<ConverterWrapper<?, ?>> commandPredictorList = new ArrayList<>();
        final ArrayList<Converter<?, ?>> allConverters = new ArrayList<>();
        String scopeName;
        ConverterStore parentStore;

        ConverterStore(String name) {
            this.scopeName = name;
        }

        void addParent(ConverterStore parent) {
            ConverterStore loopParent = parent;
            while (loopParent != null) {
                if (loopParent == this) {
                    throw new CarRetrofitException("Conflicted parent:" + parent.scopeName
                            + " this:" + scopeName);
                }
                loopParent = loopParent.parentStore;
            }
            parentStore = parent;
        }

        void addConverter(Converter<?, ?> converter) {
            Objects.requireNonNull(converter);
            Class<?> lookingFor;
            if (converter instanceof TwoWayConverter) {
                lookingFor = TwoWayConverter.class;
            } else if (converter instanceof FlowConverter) {
                lookingFor = FlowConverter.class;
            } else {
                lookingFor = Converter.class;
            }
            Class<?> implementsBy = lookUp(converter.getClass(), lookingFor);
            if (implementsBy == null) {
                throw new CarRetrofitException("invalid input converter:" + converter.getClass());
            }
            if (implementsBy.isSynthetic()) {
                throw new CarRetrofitException("Do not use lambda expression in addConverter()");
            }
            Type[] ifTypes = implementsBy.getGenericInterfaces();
            Class<?> convertFrom = null;
            Class<?> convertTo = null;
            for (Type type : ifTypes) {
                if (type instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) type;
                    if (parameterizedType.getRawType() == lookingFor) {
                        Type[] converterDeclaredType = parameterizedType.getActualTypeArguments();
                        if (lookingFor == FlowConverter.class) {
                            convertFrom = Flow.class;
                            convertTo = getClassFromType(converterDeclaredType[0]);
                        } else {
                            convertFrom = getClassFromType(converterDeclaredType[0]);
                            convertTo = getClassFromType(converterDeclaredType[1]);
                        }
                        break;
                    }
                }
            }

            if (convertFrom != null && convertTo != null) {
                if (converter instanceof CommandPredictor) {
                    commandPredictorList.add(new ConverterWrapper<>(convertFrom, convertTo,
                            converter));
                } else {
                    if (checkConverter(convertFrom, convertTo)) {
                        throw new CarRetrofitException("Can not add duplicate converter from:" +
                                toClassString(convertFrom) + " to:" + toClassString(convertTo));
                    }
                    converterWrapperList.add(new ConverterWrapper<>(convertFrom, convertTo,
                            converter));
                }
                WrappedData wrappedData = converter.getClass()
                        .getDeclaredAnnotation(WrappedData.class);
                if (wrappedData != null) {
                    synchronized (WRAPPER_CLASS_MAP) {
                        WRAPPER_CLASS_MAP.put(convertTo, wrappedData.type());
                    }
                }
                allConverters.add(converter);
            } else {
                throw new CarRetrofitException("invalid converter class:" + implementsBy
                        + " type:" + Arrays.toString(ifTypes));
            }
        }

        private boolean checkConverter(Class<?> from, Class<?> to) {
            final ConverterStore parent = this.parentStore;
            this.parentStore = null;
            Converter<?, ?> converter = findWithoutCommand(from, to);
            this.parentStore = parent;
            return converter != null;
        }

        private Converter<?, ?> findWithCommand(Command command, Class<?> from, Class<?> to) {
            if (command == null) {
                return null;
            }
            if (commandPredictorList.size() > 0) {
                for (int i = 0; i < commandPredictorList.size(); i++) {
                    ConverterWrapper<?, ?> converterWrapper = commandPredictorList.get(i);
                    if (converterWrapper.checkCommand(command)) {
                        Converter<?, ?> converter = converterWrapper.asConverter(from, to);
                        if (converter != null) {
                            return converter;
                        }
                    }
                }
            }
            return parentStore != null ? parentStore.findWithCommand(command, from, to) : null;
        }

        private Converter<?, ?> findWithoutCommand(Class<?> from, Class<?> to) {
            for (int i = 0; i < converterWrapperList.size(); i++) {
                ConverterWrapper<?, ?> converterWrapper = converterWrapperList.get(i);
                Converter<?, ?> converter = converterWrapper.asConverter(from, to);
                if (converter != null) {
                    return converter;
                }
            }
            return parentStore != null ? parentStore.findWithoutCommand(from, to) : null;
        }

        private static Class<?> boxTypeOf(Class<?> primitive) {
            if (primitive == int.class) {
                return Integer.class;
            } else if (primitive == float.class) {
                return Float.class;
            } else if (primitive == byte.class) {
                return Byte.class;
            } else if (primitive == boolean.class) {
                return Boolean.class;
            } else if (primitive == long.class) {
                return Long.class;
            } else {
                return primitive;
            }
        }

        static Converter<?, ?> find(Command command, Class<?> from, Class<?> to,
                                    ConverterStore store) {
            from = from.isPrimitive() ? boxTypeOf(from) : from;
            to = to.isPrimitive() ? boxTypeOf(to) : to;
            Converter<?, ?> converter = store.findWithCommand(command, from, to);
            if (from == to || converter != null) {
                return converter;
            }
            converter = store.findWithoutCommand(from, to);
            if (converter != null) {
                return converter;
            }
            throw new CarRetrofitException("Can not resolve converter from:"
                    + toClassString(from) + " to:" + toClassString(to)
                    + " with command:" + command);
        }

        private static String toClassString(Class<?> clazz) {
            return clazz.isArray() ? clazz.getComponentType() + "[]" : clazz.toString();
        }
    }

    public static final class Builder {
        private ArrayList<Converter<?, ?>> converters = new ArrayList<>();
        private HashMap<String, DataSource> dataMap = new HashMap<>();
        private ArrayList<Interceptor> interceptors = new ArrayList<>();
        private StickyType stickyType = StickyType.NO_SET;

        public Builder addDataSource(String sourceKey, DataSource source) {
            this.dataMap.put(sourceKey, source);
            return this;
        }

        public Builder addDataSource(CarRetrofit retrofit) {
            this.dataMap.putAll(retrofit.mDataMap);
            return this;
        }

        public Builder addConverter(Converter<?, ?> converter) {
            converters.add(converter);
            return this;
        }

        public Builder addConverter(CarRetrofit retrofit) {
            converters.addAll(retrofit.mConverterStore.allConverters);
            return this;
        }

        public Builder addInterceptor(Interceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        public Builder setDefaultStickyType(StickyType type) {
            stickyType = type;
            return this;
        }

        public CarRetrofit build() {
            return new CarRetrofit(this);
        }

        public void buildAsDefault() {
            setDefault(build());
        }
    }

    public static void addGlobalConverter(Converter<?, ?>... converters) {
        for (Converter<?, ?> converter : converters) {
            GLOBAL_CONVERTER.addConverter(converter);
        }
    }

    public <T> T from(Class<T> api) {
        ApiRecord<T> record = getApi(api, true);
        if (record.apiObj != null) {
            return record.apiObj;
        }
        synchronized (mApiCache) {
            record.apiObj = (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(record, args);
                        }
                        if (method.isDefault()) {
                            throw new UnsupportedOperationException(
                                "Do not declare any default method in CarRetrofit interface");
                        }
                        if (args != null && args.length > 1) {
                            throw new UnsupportedOperationException(
                                    "Do not declare any method with multiple parameters");
                        }
                        return getOrCreateCommand(record, new Key(method))
                                .invokeWithChain(args != null ? args[0] : null);
                    });
        }
        return record.apiObj;
    }

    private <T> ApiRecord<T> getApi(Class<T> api) {
        return getApi(api, false);
    }

    private <T> ApiRecord<T> getApi(Class<T> api, boolean throwIfNotDeclareCarApi) {
        synchronized (mApiCache) {
            ApiRecord<T> record = (ApiRecord<T>) mApiCache.get(api);
            if (record == null) {
                if (api.isAnnotationPresent(CarApi.class)) {
                    record = new ApiRecord<>(api);
                    mApiCache.put(api, record);
                } else if (throwIfNotDeclareCarApi) {
                    throw new CarRetrofitException("Do declare CarApi annotation in class:" + api);
                }
            }
            return record;
        }
    }

    private CommandImpl getOrCreateCommand(ApiRecord<?> record, Key key) {
        return getOrCreateCommandIfChecked(record, key, null, FLAG_PARSE_ALL);
    }

    private CommandImpl getOrCreateCommandIfChecked(ApiRecord<?> record, Key key,
                                                    Predicate<Key> checker, int flag) {
        if (record == null) {
            return null;
        }
        CommandImpl command;
        synchronized (mCommandCache) {
            command = mCommandCache.get(key);
        }
        if (command != null && (checker == null || checker.test(key))) {
            return command;
        }
        if (command == null) {
            if (key.isInvalid()) {
                throw new CarRetrofitException("Invalid key:" + key);
            }
            command = parseAnnotation(record, key, checker, flag);
            if (command != null) {
                synchronized (mCommandCache) {
                    mCommandCache.put(key, command);
                }
            }
            return command;
        }
        return null;
    }

    private static class Key {
        Method method;
        Field field;

        Key(Method method) {
            this.method = method;
        }

        Key(Field field) {
            this.field = field;
        }

        boolean isInvalid() {
            if (method != null) {
                if (method.isDefault()) {
                    return true;
                }
                return Modifier.isStatic(method.getModifiers());
            } else {
                return Modifier.isStatic(field.getModifiers());
            }
        }

        boolean isInjectInvalid() {
            if (field == null) {
                return true;
            }
            int modifiers = field.getModifiers();
            return Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers);
        }

        <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return method != null ? method.getDeclaredAnnotation(annotationClass)
                    : field.getDeclaredAnnotation(annotationClass);
        }

        boolean isInjectReturn() {
            if (field != null) {
                return true;
            }
            return method != null && method.getReturnType() != void.class;
        }

        Class<?> getGetClass() {
            if (field != null) {
                return field.getType();
            } else if (method.getReturnType() != void.class) {
                return method.getReturnType();
            } else {
                Class<?>[] classArray = method.getParameterTypes();
                if (classArray.length != 1) {
                    throw new CarRetrofitException("Invalid parameter:"
                            + Arrays.toString(classArray));
                }
                return classArray[0];
            }
        }

        Class<?> getSetClass() {
            if (field != null) {
                return field.getType();
            } else {
                Class<?>[] classArray = method.getParameterTypes();
                if (classArray.length != 1) {
                    throw new CarRetrofitException("Invalid parameter:"
                            + Arrays.toString(classArray));
                }
                if (method.getReturnType() != void.class) {
                    throw new CarRetrofitException("Invalid return type:"
                            + method.getReturnType());
                }
                return classArray[0];
            }
        }

        Class<?> getTrackClass() {
            if (field != null) {
                return field.getType();
            } else {
                Class<?> returnType = method.getReturnType();
                if (returnType == void.class) {
                    throw new CarRetrofitException("Invalid return void type");
                }
                return returnType;
            }
        }

        Type getTrackType() {
            if (field != null) {
                return field.getGenericType();
            } else if (method.getReturnType() != void.class) {
                Type returnType = method.getGenericReturnType();
                if (returnType == void.class) {
                    throw new CarRetrofitException("Invalid return void type");
                }
                return returnType;
            } else {
                Type[] parameterArray = method.getGenericParameterTypes();
                if (parameterArray.length != 1) {
                    throw new CarRetrofitException("Invalid parameter:"
                            + Arrays.toString(parameterArray));
                }
                return parameterArray[0];
            }
        }

        Class<?> getUserConcernClass() {
            Type originalType = getTrackType();
            if (hasUnresolvableType(originalType)) {
                throw new CarRetrofitException("Can not parse type:" + originalType + " from:" + this);
            }
            Class<?> wrapperClass = getClassFromType(originalType);
            if (wrapperClass == null) {
                throw new IllegalStateException("invalid type:" + originalType);
            }
            Class<?> userTargetType;
            WrappedData dataAnnotation = wrapperClass.getAnnotation(WrappedData.class);
            if (dataAnnotation != null) {
                userTargetType = dataAnnotation.type();
            } else {
                synchronized (WRAPPER_CLASS_MAP) {
                    userTargetType = WRAPPER_CLASS_MAP.get(wrapperClass);
                }
            }
            if (userTargetType == null && originalType instanceof ParameterizedType) {
                Type[] typeArray = ((ParameterizedType) originalType).getActualTypeArguments();
                if (typeArray.length > 1) {
                    throw new CarRetrofitException("Can not extract target type:"
                            + originalType + " from:" + this);
                }
                Type typeInFlow = typeArray.length > 0 ? typeArray[0] : null;
                boolean carRawTypeClass = getClassFromType(typeInFlow) == CarPropertyValue.class;
                if (carRawTypeClass) {
                    if (typeInFlow instanceof ParameterizedType) {
                        typeArray = ((ParameterizedType) typeInFlow).getActualTypeArguments();
                        userTargetType = getClassFromType(typeArray[0]);
                    }
                } else {
                    userTargetType = typeInFlow != null ? getClassFromType(typeInFlow) : null;
                }
            }
            return userTargetType;
        }

        String getName() {
            return method != null ? method.getName() : field.getName();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return (method != null && method.equals(key.method))
                    || (field != null && field.equals(key.field));
        }

        @Override
        public int hashCode() {
            return method != null ? method.hashCode() : field.hashCode();
        }

        @Override
        public String toString() {
            return "Key{" + (method != null ? ("method="
                    + method.getDeclaringClass().getSimpleName()
                    + "::" + method.getName())
                    : ("field=" + field.getDeclaringClass().getSimpleName()
                    + "::" + field.getName())) + '}';
        }
    }

    private CommandImpl parseAnnotation(ApiRecord<?> record, Key key,
                                        Predicate<Key> checker, int flag) {
        Set set = (flag & FLAG_PARSE_SET) != 0 ? key.getAnnotation(Set.class) : null;
        if (set != null) {
            if (checker != null && !checker.test(key)) {
                return null;
            }
            CommandSet command = new CommandSet();
            command.init(record, set, key);
            return command;
        }

        Get get = (flag & FLAG_PARSE_GET) != 0 ? key.getAnnotation(Get.class) : null;
        if (get != null) {
            if (checker != null && !checker.test(key)) {
                return null;
            }
            CommandGet command = new CommandGet();
            command.init(record, get, key);
            return command;
        }

        Track track = (flag & FLAG_PARSE_TRACK) != 0 ? key.getAnnotation(Track.class) : null;
        if (track != null) {
            if (checker != null && !checker.test(key)) {
                return null;
            }
            CommandTrack command = new CommandTrack();
            command.init(record, track, key);
            return command;
        }

        UnTrack unTrack = (flag & FLAG_PARSE_UN_TRACK) != 0 ? key.getAnnotation(UnTrack.class) : null;
        if (unTrack != null) {
            if (checker != null && !checker.test(key)) {
                return null;
            }
            CommandUnTrack command = new CommandUnTrack();
            final String targetTrack = unTrack.track();
            searchAndCreateChildCommand(record,
                    childKey -> targetTrack.equals(childKey.getName()),
                    child -> {
                        command.setTrackCommand((CommandFlow) child);
                        return false;
                    }, FLAG_PARSE_TRACK | FLAG_PARSE_COMBINE | FLAG_PARSE_DELEGATE);
            command.init(record, unTrack, key);
            return command;
        }

        Inject inject = (flag & FLAG_PARSE_INJECT) != 0 ? key.getAnnotation(Inject.class) : null;
        if (inject != null) {
            if (checker != null && !checker.test(key)) {
                return null;
            }
            Class<?> targetClass = key.getGetClass();
            if (targetClass.isPrimitive() || targetClass.isArray() || targetClass == String.class) {
                throw new CarRetrofitException("Can not use Inject on class type:" + targetClass);
            }
            CommandInject command = new CommandInject(targetClass, key.isInjectReturn());
            searchAndCreateChildCommand(getApi(targetClass), childKey -> {
                        if (childKey.isInjectInvalid()) {
                            throw new CarRetrofitException("Invalid key:" + childKey
                                    + " in command Inject");
                        }
                        return true;
                    }, child -> {
                        command.addChildCommand(child);
                        return true;
                    }, FLAG_PARSE_INJECT | FLAG_PARSE_GET | FLAG_PARSE_TRACK);
            if (command.childrenCommand.size() == 0
                    && !InjectReceiver.class.isAssignableFrom(targetClass)) {
                throw new CarRetrofitException("Failed to parse Inject command from type:"
                        + targetClass);
            }
            command.init(record, inject, key);
            return command;
        }

        Apply apply = (flag & FLAG_PARSE_APPLY) != 0 ? key.getAnnotation(Apply.class) : null;
        if (apply != null) {
            if (checker != null && !checker.test(key)) {
                return null;
            }
            Class<?> targetClass = key.getSetClass();
            if (targetClass.isPrimitive() || targetClass.isArray() || targetClass == String.class) {
                throw new CarRetrofitException("Can not use Apply on class type:" + targetClass);
            }
            CommandApply command = new CommandApply(targetClass);
            searchAndCreateChildCommand(getApi(targetClass), null,
                    child -> {
                        command.addChildCommand(child);
                        return true;
                    }, FLAG_PARSE_APPLY | FLAG_PARSE_SET);
            if (command.childrenCommand.size() == 0
                    && !ApplyReceiver.class.isAssignableFrom(targetClass)) {
                throw new CarRetrofitException("Failed to parse Apply command from type:"
                        + targetClass);
            }
            command.init(record, apply, key);
            return command;
        }

        Combine combine = (flag & FLAG_PARSE_COMBINE) != 0 ? key.getAnnotation(Combine.class) : null;
        if (combine != null) {
            if (checker != null && !checker.test(key)) {
                return null;
            }
            CommandCombine command = new CommandCombine();
            List<String> elementList = new ArrayList<>(Arrays.asList(combine.elements()));
            searchAndCreateChildCommand(record,
                    childKey -> {
                        if (childKey.equals(key)) {
                            return false;
                        }
                        return elementList.contains(childKey.getName());
                    },
                    child -> {
                        command.addChildCommand(child);
                        elementList.remove(child.getName());
                        return elementList.size() > 0;
                    }, FLAG_PARSE_GET | FLAG_PARSE_TRACK | FLAG_PARSE_COMBINE
                            | FLAG_PARSE_DELEGATE);
            if (elementList.size() > 0) {
                throw new CarRetrofitException("Can not find target child element on Combine:"
                        + key + " missing element:" + elementList);
            }
            if (command.childrenCommand.size() <= 1) {
                throw new CarRetrofitException("Must declare more than one element on Combine:"
                        + key + " element:" + command.childrenCommand);
            }
            command.init(record, combine, key);
            return command;
        }

        Delegate delegate = (flag & FLAG_PARSE_DELEGATE) != 0 ? key.getAnnotation(Delegate.class) : null;
        if (delegate != null) {
            if (checker != null && !checker.test(key)) {
                return null;
            }
            String target = delegate.target();
            CommandDelegate command = new CommandDelegate();
            searchAndCreateChildCommand(record,
                    childKey -> childKey.getName().equals(target),
                    targetCommand -> {
                        command.setTargetCommand(targetCommand);
                        return false;
                    }, FLAG_PARSE_ALL &~ FLAG_PARSE_DELEGATE);
            if (command.targetCommand == null) {
                throw new CarRetrofitException("Can not resolve delegate target:" + target);
            }
            command.init(record, delegate, key);
            return command;
        }
        return null;
    }

    private boolean searchAndCreateChildCommand(ApiRecord<?> record, Predicate<Key> checker,
                                                Predicate<CommandImpl> commandReceiver, int flag) {
        ArrayList<Key> childKeys = record.getChildKey();
        for (int i = 0; i < childKeys.size(); i++) {
            Key childKey = childKeys.get(i);
            CommandImpl command = getOrCreateCommandIfChecked(record, childKey, checker, flag);
            if (command != null) {
                if (!commandReceiver.test(command)) {
                    return false;
                }
            }
        }
        ArrayList<ApiRecord<?>> parentRecordList = record.getParentApi();
        for (int i = 0; i < parentRecordList.size(); i++) {
            boolean more = searchAndCreateChildCommand(parentRecordList.get(i), checker,
                    commandReceiver, flag);
            if (!more) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUnresolvableType(Type type) {
        if (type instanceof Class<?>) {
            return false;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            for (Type typeArgument : parameterizedType.getActualTypeArguments()) {
                if (hasUnresolvableType(typeArgument)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof GenericArrayType) {
            return true;
        }
        if (type instanceof TypeVariable) {
            return true;
        }
        if (type instanceof WildcardType) {
            return true;
        }
        String className = type == null ? "null" : type.getClass().getName();
        throw new CarRetrofitException("Expected a Class or ParameterizedType," +
                " but <" + type + "> is of type " + className);
    }

    private static abstract class CommandImpl implements Command, Cloneable {
        int propertyId;
        int area;
        String token;
        ApiRecord<?> record;
        ConverterStore store;
        InterceptorChain chain;
        DataSource source;
        Class<?> userDataClass;
        Key key;

        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            this.record = record;
            this.source = record.source;
            this.store = record.converterStore;
            this.chain = record.interceptorChain;
            this.key = key;
        }

        final void resolveArea(int userDeclaredArea) {
            if (userDeclaredArea != CarApi.DEFAULT_AREA_ID) {
                this.area = userDeclaredArea;
            } else {
                if (this.record.apiArea != CarApi.DEFAULT_AREA_ID) {
                    this.area = record.apiArea;
                } else {
                    this.area = CarApi.GLOBAL_AREA_ID;
                }
            }
        }

        CommandImpl shallowCopy() {
            try {
                return (CommandImpl) clone();
            } catch (CloneNotSupportedException error) {
                throw new CarRetrofitException(error);
            }
        }

        @Override
        public Method getMethod() {
            return delegateTarget().key.method;
        }

        @Override
        public Field getField() {
            return delegateTarget().key.field;
        }

        @Override
        public String getName() {
            return delegateTarget().key.getName();
        }

        @Override
        public boolean fromInject() {
            return false;
        }

        @Override
        public boolean fromApply() {
            return false;
        }

        @Override
        public void setPropertyId(int propertyId) {
            delegateTarget().propertyId = propertyId;
        }

        @Override
        public int getPropertyId() {
            return delegateTarget().propertyId;
        }

        @Override
        public void setArea(int area) {
            delegateTarget().area = area;
        }

        @Override
        public int getArea() {
            return delegateTarget().area;
        }

        @Override
        public void setSource(DataSource source) {
            delegateTarget().source = source;
        }

        @Override
        public String getToken() {
            return delegateTarget().token;
        }

        @Override
        public DataSource getSource() {
            return delegateTarget().source;
        }

        @Override
        public final String toString() {
            return "Command[" + type() + delegateTarget().toCommandString() + "]";
        }

        CommandImpl delegateTarget() {
            return this;
        }

        String toCommandString() {
            int area = getArea();
            return " id:0x" + Integer.toHexString(getPropertyId())
                    + (area != CarApi.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "")
                    + " from:" + delegateTarget().key;
        }

        final Object invokeWithChain(Object parameter) throws Throwable {
            if (chain != null) {
                return chain.doProcess(this, parameter);
            } else {
                return invoke(parameter);
            }
        }
    }

    private static class CommandApply extends CommandGroup {

        Class<?> targetClass;

        CommandApply(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public boolean fromApply() {
            return true;
        }

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            super.init(record, annotation, key);
            String userSet = ((Apply) annotation).token();
            if (userSet.length() > 0) {
                token = userSet;
            }
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            Object target = null;
            if (parameter != null) {
                if (targetClass.isInstance(parameter)) {
                    target = parameter;
                }
            }

            if (target == null) {
                throw new NullPointerException("invalid target. class:"
                        + targetClass + " parameter:" + parameter);
            }

            if (target instanceof ApplyReceiver) {
                ((ApplyReceiver) target).onBeforeApply();
            }

            final int count = childrenCommand.size();
            for (int i = 0; i < count; i++) {
                CommandImpl command = childrenCommand.get(i);
                Field field = command.getField();
                if (command instanceof CommandApply) {
                    try {
                        Object applyFrom = field.get(target);
                        if (applyFrom != null) {
                            command.invokeWithChain(applyFrom);
                        }
                    } catch (IllegalAccessException ie) {
                        throw new CarRetrofitException("Apply failed", ie);
                    }
                } else {
                    command.invokeWithChain(target);
                }
            }

            if (target instanceof ApplyReceiver) {
                ((ApplyReceiver) target).onAfterApplied();
            }

            return SKIP;
        }

        @Override
        public CommandType type() {
            return CommandType.APPLY;
        }

        @Override
        String toCommandString() {
            boolean fromField = getField() != null;
            return "CommandApply target:" + targetClass.getSimpleName()
                    + " from" + (!fromField ? ("method:" + getMethod().getName())
                    : ("field:" + getField().getName()));
        }
    }

    private static class CommandInject extends CommandGroup {

        Class<?> targetClass;
        boolean doReturn;

        CommandInject(Class<?> injectClass, boolean doReturn) {
            this.targetClass = injectClass;
            this.doReturn = doReturn;
        }

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            super.init(record, annotation, key);
            String userSet = ((Inject) annotation).token();
            if (userSet.length() > 0) {
                token = userSet;
            }
        }

        @Override
        public boolean fromInject() {
            return true;
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            Object target = null;
            if (targetClass.isInstance(parameter)) {
                target = parameter;
            }

            if (target == null && doReturn) {
                try {
                    target = targetClass.getConstructor().newInstance();
                } catch (ReflectiveOperationException re) {
                    throw new CarRetrofitException("Can not create inject target", re);
                }
            }

            if (target == null) {
                throw new NullPointerException("invalid target. injectClass:"
                        + targetClass + " parameter:" + parameter);
            }

            if (target instanceof InjectReceiver) {
                ((InjectReceiver) target).onBeforeInject();
            }

            final int count = childrenCommand.size();
            for (int i = 0; i < count; i++) {
                CommandImpl command = childrenCommand.get(i);
                Field field = command.getField();
                try {
                    if (command.type() == CommandType.INJECT) {
                        Object injectTarget = field.get(target);
                        if (injectTarget != null) {
                            command.invokeWithChain(injectTarget);
                        } else {
                            field.set(target, command.invokeWithChain(null));
                        }
                    } else {
                        field.set(target, command.invokeWithChain(null));
                    }
                } catch (IllegalAccessException e) {
                    throw new CarRetrofitException("Inject failed", e);
                }
            }

            if (target instanceof InjectReceiver) {
                ((InjectReceiver) target).onAfterInjected();
            }

            return !doReturn ? SKIP : target;
        }

        @Override
        public CommandType type() {
            return CommandType.INJECT;
        }

        @Override
        String toCommandString() {
            boolean fromField = getField() != null;
            return "CommandInject target:" + targetClass.getSimpleName()
                    + " from" + (!fromField ? ("method:" + getMethod().getName())
                    : ("field:" + getField().getName()));
        }
    }

    private static abstract class CommandGroup extends CommandImpl {
        ArrayList<CommandImpl> childrenCommand = new ArrayList<>();

        void addChildCommand(CommandImpl command) {
            childrenCommand.add(command);
        }

        @Override
        String toCommandString() {
            StringBuilder childrenToken = new StringBuilder("{");
            for (int i = 0; i < childrenCommand.size(); i++) {
                if (i > 0) {
                    childrenToken.append(", ");
                }
                childrenToken.append(childrenCommand.get(i));
            }
            childrenToken.append('}');
            return super.toCommandString() + childrenToken.toString();
        }
    }

    private static class CommandCombine extends CommandFlow {

        Operator operator;
        String operatorExp;
        Class<?> functionClass;

        @Override
        void addChildCommand(CommandImpl command) {
            CommandImpl childCopy = command.shallowCopy();
            childrenCommand.add(childCopy);
        }

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            super.init(record, annotation, key);
            Combine combine = (Combine) annotation;
            List<String> elementList = Arrays.asList(combine.elements());
            Collections.sort(childrenCommand, (o1, o2) ->
                    elementList.indexOf(o1.getName()) - elementList.indexOf(o2.getName()));
            String userSet = combine.token();
            if (userSet.length() > 0) {
                token = userSet;
            }
            resolveStickyType(combine.sticky());

            ArrayList<CommandFlow> flowChildren = null;
            ArrayList<CommandImpl> otherChildren = null;
            for (int i = 0; i < childrenCommand.size(); i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                if (childCommand instanceof CommandFlow) {
                    CommandFlow flowChild = (CommandFlow) childCommand;
                    returnFlow |= flowChild.returnFlow;
                    flowChild.registerTrack = false;
                    flowChild.mapFlowSuppressed = true;
                    if (this.stickyType == StickyType.NO_SET
                            || this.stickyType == StickyType.OFF) {
                        flowChild.stickyType = StickyType.ON;
                    } else {
                        flowChild.stickyType = this.stickyType;
                    }
                    if (flowChildren == null) {
                        flowChildren = new ArrayList<>();
                    }
                    flowChildren.add((CommandFlow) childCommand);
                } else {
                    if (otherChildren == null) {
                        otherChildren = new ArrayList<>();
                    }
                    otherChildren.add(childCommand);
                }
            }
            if (flowChildren != null && otherChildren != null) {
                for (int i = 0; i < flowChildren.size(); i++) {
                    for (int j = 0; j < otherChildren.size(); j++) {
                        if (flowChildren.get(i).checkElement(otherChildren.get(j))) {
                            throw new CarRetrofitException("Duplicate combine element:" + this);
                        }
                    }
                }
            }

            operatorExp = combine.combinator();
            try {
                Field combinatorField = record.clazz.getDeclaredField(operatorExp);
                Object operatorObj = combinatorField.get(null);
                String operatorFullName = Operator.class.getName();
                String prefix = operatorFullName.substring(0, operatorFullName.lastIndexOf("."));
                int childElementCount = childrenCommand.size();
                String targetFunctionFullName = prefix + ".Function" + childElementCount;
                functionClass = Class.forName(targetFunctionFullName);
                if (functionClass.isInstance(operatorObj)) {
                    this.operator = (Operator) Objects.requireNonNull(operatorObj,
                            "Failed to resolve operator:" + operatorExp);
                } else {
                    throw new CarRetrofitException("Operator:" + operatorExp
                            + " doesn't match element count:" + childElementCount);
                }
            } catch (ReflectiveOperationException e) {
                throw new CarRetrofitException(e);
            }

            resolveConverter();
        }

        private void resolveConverter() {
            Class<?> originalType = null;
            Class<?> implementByClass = lookUp(operator.getClass(), functionClass);
            if (implementByClass != null) {
                Type[] ifTypeArray = implementByClass.getGenericInterfaces();
                for (Type ifType : ifTypeArray) {
                    if (ifType instanceof ParameterizedType) {
                        ParameterizedType parameterizedType = (ParameterizedType) ifType;
                        if (parameterizedType.getRawType() == functionClass) {
                            Type[] functionType = parameterizedType.getActualTypeArguments();
                            originalType = getClassFromType(functionType[functionType.length - 1]);
                            break;
                        }
                    }
                }
            }
            if (originalType == null) {
                throw new CarRetrofitException("Failed to resolve result type from:" + operator
                        + " functionClass:" + functionClass.getSimpleName());
            }

            if (returnFlow) {
                Converter<?, ?> converter = ConverterStore.find(this, Flow.class,
                        key.getTrackClass(), store);
                resultConverter = (Converter<Object, ?>) converter;

                try {
                    mapConverter = findMapConverter(originalType);
                } catch (Exception e) {
                    throw new CarRetrofitException(e);
                }
            } else {
                Converter<?, ?> converter = ConverterStore.find(this, originalType,
                        key.getTrackClass(), store);
                resultConverter = (Converter<Object, ?>) converter;
            }
        }

        private Object processResult(int effectIndex, Object[] elements) {
            try {
                switch (elements.length) {
                    case 2:
                        return ((Function2<Object, Object, Object>)operator).apply(effectIndex,
                                elements[0], elements[1]);
                    case 3:
                        return ((Function3<Object, Object, Object, Object>)operator)
                                .apply(effectIndex, elements[0], elements[1], elements[2]);
                    case 4:
                        return ((Function4<Object, Object, Object, Object, Object>)operator)
                                .apply(effectIndex, elements[0], elements[1], elements[2],
                                        elements[3]);
                    case 5:
                        return ((Function5<Object, Object, Object, Object, Object, Object>)operator)
                                .apply(effectIndex, elements[0], elements[1], elements[2],
                                        elements[3], elements[4]);
                    case 6:
                        return ((Function6<Object, Object, Object, Object, Object, Object, Object>)operator)
                                .apply(effectIndex, elements[0], elements[1], elements[2],
                                        elements[3], elements[4], elements[5]);
                    default:
                        throw new IllegalStateException("impossible length:" + elements.length);
                }
            } catch (ClassCastException castError) {
                throw new CarRetrofitException("Value:" + Arrays.toString(elements)
                        + " can not apply tp operator:" + operatorExp, castError);
            }
        }

        @Override
        Object doInvoke() throws Throwable {
            final int size = childrenCommand.size();
            Object[] elementResult = new Object[size];
            for (int i = 0; i < size; i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                elementResult[i] = childCommand.invokeWithChain(null);
            }
            Object result;
            if (returnFlow) {
                Flow<Object> flow;
                CombineFlow combineFlow = new CombineFlow(elementResult);
                if (mapConverter != null) {
                    flow = new MediatorFlow<>(combineFlow,
                            data -> mapConverter.convert(
                                    processResult(data.effectIndex, data.trackingObj)));
                } else {
                    flow = new MediatorFlow<>(combineFlow,
                            data -> processResult(data.effectIndex, data.trackingObj));
                }
                if (stickyType == StickyType.ON || stickyType == StickyType.ON_NO_CACHE) {
                    flow = new StickyFlowImpl<>(flow, stickyType == StickyType.ON,
                            () -> {
                                    CombineData data = combineFlow.get();
                                    return processResult(-1, data.trackingObj);
                                });
                }
                flow = new FlowWrapper<>(flow, createCommandReceive());
                if (mapFlowSuppressed) {
                    return flow;
                }
                result = flow;
            } else {
                result = processResult(-1, elementResult);
            }
            return resultConverter != null ? resultConverter.convert(result) : result;
        }

        @Override
        public CommandType type() {
            return CommandType.COMBINE;
        }

        @Override
        String toCommandString() {
            return super.toCommandString() + " operator:" + operatorExp;
        }

        private static class CombineData {
            int effectIndex;
            Object[] trackingObj;

            CombineData copy() {
                CombineData data = new CombineData();
                data.effectIndex = this.effectIndex;
                data.trackingObj = Arrays.copyOf(trackingObj, trackingObj.length);
                return data;
            }

            void update(int index, Object obj) {
                effectIndex = index;
                trackingObj[index] = obj;
            }
        }

        private static class CombineFlow implements StickyFlow<CombineData> {
            CombineData trackingData;
            StickyFlow<?>[] flowArray;
            InternalObserver[] flowObservers;
            ArrayList<Consumer<CombineData>> consumers = new ArrayList<>();
            boolean notifyValueSuppressed;

            CombineFlow(Object[] elementsArray) {
                final int size = elementsArray.length;
                trackingData = new CombineData();
                trackingData.trackingObj = new Object[size];
                flowArray = new StickyFlow<?>[size];
                flowObservers = new InternalObserver[size];
                for (int i = 0; i < size; i++) {
                    Object obj = elementsArray[i];
                    if (obj instanceof Flow) {
                        if (obj instanceof StickyFlow) {
                            flowArray[i] = (StickyFlow<?>) obj;
                            flowObservers[i] = new InternalObserver(i);
                            continue;
                        }
                        throw new IllegalStateException("impossible obj:" + obj);
                    } else {
                        trackingData.trackingObj[i] = obj;
                    }
                }
            }

            @Override
            public void addObserver(Consumer<CombineData> consumer) {
                consumers.add(consumer);
                if (consumers.size() == 1) {
                    notifyValueSuppressed = true;
                    trackingData.effectIndex = -1;
                    for (int i = 0; i < flowArray.length; i++) {
                        StickyFlow<Object> stickyFlow = (StickyFlow<Object>) flowArray[i];
                        if (stickyFlow != null) {
                            stickyFlow.addObserver(flowObservers[i]);
                        }
                    }
                    notifyValueSuppressed = false;
                }
            }

            @Override
            public void removeObserver(Consumer<CombineData> consumer) {
                if (consumers.remove(consumer) && consumers.size() == 0) {
                    for (int i = 0; i < flowArray.length; i++) {
                        StickyFlow<Object> stickyFlow = (StickyFlow<Object>) flowArray[i];
                        if (stickyFlow != null) {
                            stickyFlow.removeObserver(flowObservers[i]);
                        }
                    }
                }
            }

            private void notifyChangeLocked() {
                if (consumers.size() > 0 && !notifyValueSuppressed) {
                    ArrayList<Consumer<CombineData>> consumersClone
                            = (ArrayList<Consumer<CombineData>>) consumers.clone();
                    for (int i = 0; i < consumersClone.size(); i++) {
                        consumersClone.get(i).accept(trackingData);
                    }
                }
            }

            @Override
            public CombineData get() {
                synchronized (this) {
                    return trackingData.copy();
                }
            }

            private class InternalObserver implements Consumer<Object> {

                int index;

                InternalObserver(int index) {
                    this.index = index;
                }

                @Override
                public void accept(Object o) {
                    synchronized (CombineFlow.this) {
                        trackingData.update(index, o);
                        notifyChangeLocked();
                    }
                }
            }
        }
    }

    private static class CommandSet extends CommandImpl {
        BuildInValue buildInValue;
        Converter<Object, ?> argConverter;

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            super.init(record, annotation, key);
            Set set = (Set) annotation;
            this.propertyId = set.id();
            buildInValue = BuildInValue.build(set.value());
            resolveArea(set.area());
            String userSet = set.token();
            if (userSet.length() > 0) {
                token = userSet;
            }

            if (buildInValue != null) {
                return;
            }
            resolveArgConverter(key.getSetClass(), null);
        }

        private void resolveArgConverter(Class<?> userArgClass, AccessibleObject refObj) {
            if (buildInValue != null) {
                return;
            }
            Class<?> carArgClass;
            try {
                carArgClass = source.extractValueType(propertyId);
            } catch (Exception e) {
                throw new CarRetrofitException(e);
            }
            Converter<?, ?> converter = ConverterStore.find(this,
                    userArgClass, carArgClass, store);
            userDataClass = userArgClass;
            if (converter != null) {
                argConverter = (Converter<Object, ?>) converter;
            }
        }

        Object collectArgs(Object parameter) throws Throwable {
            if (buildInValue != null) {
                return buildInValue.extractValue(source.extractValueType(propertyId));
            }
            Object rawArg;
            if (key.field != null) {
                try {
                    rawArg = key.field.get(parameter);
                } catch (IllegalAccessException ex) {
                    throw new CarRetrofitException("Can not access to parameter", ex);
                }
            } else {
                rawArg = parameter;
            }
            return argConverter != null && rawArg != null ? argConverter.convert(rawArg) : rawArg;
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            source.set(propertyId, area, collectArgs(parameter));
            return SKIP;
        }

        @Override
        public CommandType type() {
            return CommandType.SET;
        }

        @Override
        public boolean fromApply() {
            return key.field != null;
        }
    }

    private static class CommandGet extends CommandImpl {

        Converter<Object, ?> resultConverter;
        CarType type;
        boolean stickyGet;

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            super.init(record, annotation, key);
            if (annotation instanceof Get) {
                Get get = (Get) annotation;
                propertyId = get.id();
                type = get.type();
                if (type == CarType.ALL) {
                    throw new CarRetrofitException("Can not use type ALL mode in Get operation");
                }
                resolveArea(get.area());
                String userSet = get.token();
                if (userSet.length() > 0) {
                    token = userSet;
                }
            } else if (annotation instanceof Track) {
                Track track = (Track) annotation;
                propertyId = track.id();
                type = track.type();
                resolveArea(track.area());
                String userSet = track.token();
                if (userSet.length() > 0) {
                    token = userSet;
                }
                stickyGet = true;
            }

            resolveResultConverter(stickyGet ? key.getUserConcernClass() : key.getGetClass());
        }

        private void resolveResultConverter(Class<?> userReturnClass) {
            Class<?> carReturnClass;
            try {
                carReturnClass = type == CarType.AVAILABILITY ?
                        boolean.class : source.extractValueType(propertyId);
            } catch (Exception e) {
                throw new CarRetrofitException(e);
            }
            Converter<?, ?> converter = ConverterStore.find(this, carReturnClass,
                    userReturnClass, store);
            resultConverter = (Converter<Object, ?>) converter;
            userDataClass = userReturnClass;
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            Object obj;
            if (stickyGet && type == CarType.ALL) {
                obj = source.get(propertyId, area, CarType.VALUE);
                obj = new CarPropertyValue<>(propertyId, area,
                        resultConverter != null ? resultConverter.convert(obj) : obj);
                return obj;
            } else {
                obj = source.get(propertyId, area, type);
                return resultConverter != null ? resultConverter.convert(obj) : obj;
            }
        }

        @Override
        public CommandType type() {
            return stickyGet ? CommandType.STICKY_GET : CommandType.GET;
        }

        @Override
        public boolean fromInject() {
            return key.field != null;
        }

        @Override
        String toCommandString() {
            String stable = super.toCommandString();
            if (stickyGet) {
                stable += " stickyGet";
            }
            if (type != CarType.VALUE) {
                stable += " valueType:" + type;
            }
            return stable;
        }
    }

    private static abstract class CommandFlow extends CommandImpl {
        Converter<Object, ?> resultConverter;
        Converter<Object, ?> mapConverter;
        boolean mapFlowSuppressed;

        StickyType stickyType;

        boolean returnFlow;
        private boolean registerTrack;
        Flow<Object> trackingFlow;
        ArrayList<CommandImpl> childrenCommand = new ArrayList<>();

        void addChildCommand(CommandImpl command) {
            childrenCommand.add(command);
        }

        CommandReceive createCommandReceive() {
            CommandReceive receive = new CommandReceive(this);
            receive.init(record, null, key);
            return receive;
        }

        @Override
        CommandFlow shallowCopy() {
            CommandFlow commandFlow = (CommandFlow) super.shallowCopy();
            commandFlow.trackingFlow = null;
            return commandFlow;
        }

        void resolveStickyType(StickyType stickyType) {
            if (stickyType == StickyType.NO_SET) {
                this.stickyType = record.stickyType;
            } else {
                this.stickyType = stickyType;
            }
        }

        StickyType suppressStickyType(StickyType stickyType) {
            StickyType oldType = this.stickyType;
            if (this.stickyType == stickyType || stickyType == StickyType.NO_SET) {
                return this.stickyType;
            }
            if (!registerTrack) {
                this.stickyType = stickyType;
            }
            return oldType;
        }

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            super.init(record, annotation, key);
            if (key.method != null && key.method.getReturnType() == void.class) {
                Class<?>[] classArray = key.method.getParameterTypes();
                if (classArray.length == 1) {
                    Class<?> parameterClass = classArray[0];
                    if (Consumer.class.isAssignableFrom(parameterClass)) {
                        registerTrack = true;
                        mapFlowSuppressed = true;
                    } else {
                        throw new CarRetrofitException("invalid track parameter:" + this);
                    }
                }
            }
        }

        boolean checkElement(CommandImpl command) {
            if (command == this) {
                return true;
            }
            for (int i = 0; i < childrenCommand.size(); i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                if (childCommand instanceof CommandFlow) {
                    if (((CommandFlow) childCommand).checkElement(command)) {
                        return true;
                    }
                } else if (command == childCommand) {
                    return true;
                }
            }
            return false;
        }

        Converter<Object, ?> findMapConverter(Class<?> carType) {
            Class<?> userConcernClass = key.getUserConcernClass();
            if (userConcernClass != null) {
                userDataClass = userConcernClass;
                return (Converter<Object, ?>) ConverterStore.find(this, carType,
                        userConcernClass, store);
            }
            return null;
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            if (returnFlow && registerTrack) {
                if (trackingFlow == null) {
                    trackingFlow = (Flow<Object>) doInvoke();
                }
                Consumer<Object> consumer = (Consumer<Object>) parameter;
                trackingFlow.addObserver(consumer);
                return SKIP;
            } else {
                return doInvoke();
            }
        }

        abstract Object doInvoke() throws Throwable;

        @Override
        String toCommandString() {
            String fromSuper = super.toCommandString();
            if (stickyType != StickyType.NO_SET) {
                fromSuper += " stickyType:" + stickyType;
            }
            return fromSuper;
        }
    }

    private static class CommandTrack extends CommandFlow {

        CarType type;
        CommandGet stickyGet;
        Annotation annotation;

        CommandTrack() {
            returnFlow = true;
        }

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            super.init(record, annotation, key);
            Track track = (Track) annotation;
            propertyId = track.id();
            type = track.type();
            this.annotation = annotation;
            if (type == CarType.CONFIG) {
                throw new CarRetrofitException("Can not use type CONFIG mode in Track operation");
            }

            resolveArea(track.area());
            resolveStickyType(track.sticky());
            
            String userSet = track.token();
            if (userSet.length() > 0) {
                token = userSet;
            }

            resolveConverter();
        }

        @Override
        CommandTrack shallowCopy() {
            CommandTrack commandTrack = (CommandTrack) super.shallowCopy();
            commandTrack.stickyGet = null;
            return commandTrack;
        }

        @Override
        public boolean fromInject() {
            return key.field != null;
        }

        private void resolveConverter() {
            if (!mapFlowSuppressed) {
                Converter<?, ?> converter = ConverterStore.find(this, Flow.class,
                        key.getTrackClass(), store);
                resultConverter = (Converter<Object, ?>) converter;
            }

            Class<?> carType;
            if (type == CarType.AVAILABILITY) {
                carType = boolean.class;
            } else {
                try {
                    carType = source.extractValueType(propertyId);
                } catch (Exception e) {
                    throw new CarRetrofitException(e);
                }
            }
            mapConverter = findMapConverter(carType);
        }

        @Override
        Object doInvoke() throws Throwable {
            Flow<CarPropertyValue<?>> flow = source.track(propertyId, area);
            Flow<?> result = flow;
            switch (type) {
                case VALUE:
                    if (mapConverter != null) {
                        result = new MediatorFlow<>(flow,
                                carPropertyValue -> mapConverter.apply(carPropertyValue.getValue()));
                    } else {
                        result = new MediatorFlow<>(flow, CarPropertyValue::getValue);
                    }
                    break;
                case AVAILABILITY:
                    if (mapConverter != null) {
                        result = new MediatorFlow<>(flow, value -> mapConverter.apply(value != null
                                && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE));
                    } else {
                        result = new MediatorFlow<>(flow, value -> value != null
                                && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE);
                    }
                    break;
                case ALL:
                    if (mapConverter != null) {
                        result = new MediatorFlow<>(flow, rawValue -> new CarPropertyValue<>(
                                rawValue.getPropertyId(),
                                rawValue.getAreaId(),
                                rawValue.getStatus(),
                                rawValue.getTimestamp(),
                                mapConverter.apply(rawValue.getValue())));
                    } else {
                        result = new MediatorFlow<>(flow, null);
                    }
                    break;
            }
            if (stickyType == StickyType.ON || stickyType == StickyType.ON_NO_CACHE) {
                if (stickyGet == null) {
                    stickyGet = new CommandGet();
                    stickyGet.init(record, annotation, key);
                }
                result = new StickyFlowImpl<>(result, stickyType == StickyType.ON,
                        () -> {
                    try {
                        return stickyGet.invokeWithChain(null);
                    } catch (Throwable throwable) {
                        throw new CarRetrofitException(throwable);
                    }
                });
            }
            result = new FlowWrapper<>(result, createCommandReceive());
            if (mapFlowSuppressed) {
                return result;
            }
            return resultConverter != null ? resultConverter.convert(result) : result;
//            return !valueMapped && mapConverter != null && resultConverter != null ?
//                    ((ConverterMapper<Object, Object>)resultConverter).map(obj, mapConverter) : obj;
        }

        @Override
        public CommandType type() {
            return CommandType.TRACK;
        }

        @Override
        String toCommandString() {
            String stable = super.toCommandString();
            if (type != CarType.VALUE) {
                stable += " valueType:" + type;
            }
            return stable;
        }
    }

    private static class CommandUnTrack extends CommandImpl {
        CommandFlow trackCommand;

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            super.init(record, annotation, key);
            if (key.method != null && key.method.getReturnType() == void.class) {
                Class<?>[] classArray = key.method.getParameterTypes();
                if (classArray.length == 1) {
                    Class<?> parameterClass = classArray[0];
                    if (Consumer.class.isAssignableFrom(parameterClass)) {
                        return;
                    }
                }
            }
            throw new CarRetrofitException("invalid unTrack:" + this);
        }

        void setTrackCommand(CommandFlow trackCommand) {
            this.trackCommand = trackCommand;
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            if (trackCommand.trackingFlow != null) {
                trackCommand.trackingFlow.removeObserver((Consumer<Object>) parameter);
            }
            return null;
        }

        @Override
        public CommandType type() {
            return CommandType.UN_TRACK;
        }

        @Override
        String toCommandString() {
            return "paired:" + trackCommand;
        }
    }

    private static class CommandReceive extends CommandImpl {
        CommandFlow commandFlow;
        FlowWrapper<Object> flowWrapper;

        CommandReceive(CommandFlow commandFlow) {
            this.commandFlow = commandFlow;
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            flowWrapper.superHandleResult(parameter);
            return null;
        }

        @Override
        CommandImpl delegateTarget() {
            return commandFlow;
        }

        @Override
        public CommandType type() {
            return CommandType.RECEIVE;
        }
    }

    private static class CommandDelegate extends CommandFlow {
        CommandImpl targetCommand;
        Converter<Object, ?> argConverter;
        boolean setCommand;

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Key key) {
            super.init(record, annotation, key);
            Delegate delegate = (Delegate) annotation;
            resolveStickyType(delegate.sticky());

            if (targetCommand instanceof CommandFlow) {
                CommandFlow targetFlowCommand = (CommandFlow) targetCommand;
                this.returnFlow = targetFlowCommand.returnFlow;
                targetFlowCommand.stickyType = this.stickyType;
                targetFlowCommand.registerTrack = false;
                targetFlowCommand.mapFlowSuppressed = true;
            }
            setCommand = targetCommand.type() == CommandType.SET;
            resolveConverter();
        }

        private void resolveConverter() {
            if (setCommand) {
                if (targetCommand.userDataClass != null) {
                    argConverter = (Converter<Object, ?>) ConverterStore.find(this,
                            targetCommand.userDataClass, userDataClass = key.getSetClass(), store);
                }
            } else if (returnFlow) {
                resultConverter = (Converter<Object, ?>) ConverterStore.find(this,
                        Flow.class, key.getTrackClass(), store);
                mapConverter = findMapConverter(targetCommand.userDataClass);
            } else {
                resultConverter = findMapConverter(targetCommand.userDataClass);
            }
        }

       void setTargetCommand(CommandImpl targetCommand) {
            this.targetCommand = targetCommand.shallowCopy();
        }

        @Override
        boolean checkElement(CommandImpl command) {
            if (command == this) {
                return true;
            }
            if (targetCommand instanceof CommandFlow) {
                return ((CommandFlow) targetCommand).checkElement(command);
            }
            return false;
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            if (setCommand) {
                return targetCommand.invoke(argConverter != null ?
                        argConverter.convert(parameter) : parameter);
            }
            return super.invoke(parameter);
        }

        @Override
        Object doInvoke() throws Throwable {
            Object obj = targetCommand.invokeWithChain(null);
            Object result;
            if (obj instanceof Flow) {
                result = new FlowWrapper<>((Flow<Object>) obj, mapConverter, createCommandReceive());
            } else {
                result = obj;
            }
            if (result == null || result == SKIP) {
                return result;
            }
            if (mapFlowSuppressed) {
                return result;
            }
            return resultConverter != null ? resultConverter.convert(result) : result;
        }

        @Override
        public boolean fromApply() {
            return targetCommand.fromApply();
        }

        @Override
        public boolean fromInject() {
            return targetCommand.fromInject();
        }

        @Override
        public CommandType type() {
            return targetCommand.type();
        }

        @Override
        CommandImpl delegateTarget() {
            return targetCommand.delegateTarget();
        }
    }

    private static class MediatorFlow<FROM, TO> implements Flow<TO>, Consumer<FROM> {

        private Flow<FROM> base;
        Function<FROM, TO> mediator;
        private ArrayList<Consumer<TO>> consumers = new ArrayList<>();
        private InterceptorChain receiveChain;

        private MediatorFlow(Flow<FROM> base,
                             Function<FROM, TO> mediator) {
            this.base = base;
            this.mediator = mediator;
        }

        @Override
        public void addObserver(Consumer<TO> consumer) {
            consumers.add(consumer);
            if (consumers.size() == 1) {
                base.addObserver(this);
            }
        }

        @Override
        public void removeObserver(Consumer<TO> consumer) {
            if (consumers.remove(consumer) && consumers.size() == 0) {
                base.removeObserver(this);
            }
        }

        <TO2> void mediates(Function<TO, TO2> exceedMediator) {
            this.mediator = mediator != null ? (Function<FROM, TO>)mediator.andThen(exceedMediator)
                    : (Function<FROM, TO>) exceedMediator;
        }

        @Override
        public void accept(FROM value) {
            TO obj;
            if (mediator != null) {
                obj = mediator.apply(value);
            } else {
                obj = (TO) value;
            }
            handleResult(obj);
        }

        void handleResult(TO to) {
            synchronized (this) {
                for (int i = 0; i < consumers.size(); i++) {
                    consumers.get(i).accept(to);
                }
            }
        }
    }

    private static class FlowWrapper<T> extends MediatorFlow<T, T> {

        CommandReceive commandReceive;

        private FlowWrapper(Flow<T> base, CommandReceive command) {
            this(base, null, command);
        }

        private FlowWrapper(Flow<T> base, Function<?, ?> function, CommandReceive command) {
            super(base, (Function<T, T>) function);
            this.commandReceive = command;
            command.flowWrapper = (FlowWrapper<Object>) this;
        }

        @Override
        void handleResult(T t) {
            try {
                commandReceive.invokeWithChain(t);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        void superHandleResult(T t) {
            super.handleResult(t);
        }
    }

    private static class StickyFlowImpl<T> extends FlowWrapper<T> implements StickyFlow<T> {
        private T lastValue;
        private boolean valueReceived;
        private boolean stickyUseCache;
        private Supplier<Object> stickyGet;

        private StickyFlowImpl(Flow<T> base, boolean stickyUseCache, Supplier<Object> stickyGet) {
            super(base, null);
            this.stickyUseCache = stickyUseCache;
            this.stickyGet = stickyGet;
        }

        @Override
        public void addObserver(Consumer<T> consumer) {
            super.addObserver(consumer);
            consumer.accept(get());
        }

        @Override
        synchronized void handleResult(T value) {
            if (this.stickyUseCache) {
                valueReceived = true;
                lastValue = value;
            }
            super.handleResult(value);
        }

        @Override
        public synchronized T get() {
            T result;
            if (stickyUseCache && valueReceived) {
                result = lastValue;
            } else {
                T t = (T) stickyGet.get();
                result = mediator != null ? mediator.apply(t) : t;
            }
            if (stickyUseCache && !valueReceived) {
                valueReceived = true;
                lastValue = result;
            }
            return result;
        }
    }

    private static class ConverterWrapper<CarData, AppData> implements Converter<AppData, CarData> {
        Class<?> fromClass;
        Class<?> toClass;

        TwoWayConverter<CarData, AppData> twoWayConverter;
        Converter<Object, ?> oneWayConverter;

        ConverterWrapper(Class<?> from, Class<?> to, Converter<?, ?> converter) {
            fromClass = from;
            toClass = to;
            if (converter instanceof TwoWayConverter) {
                twoWayConverter = (TwoWayConverter<CarData, AppData>) converter;
                oneWayConverter = (Converter<Object, ?>) twoWayConverter;
            } else {
                oneWayConverter = (Converter<Object, ?>) converter;
            }
            if (oneWayConverter == null) {
                throw new NullPointerException("converter can not be null");
            }
        }

        Converter<?, ?> asConverter(Class<?> from, Class<?> to) {
            if (fromClass.equals(from) && toClass.equals(to)) {
                return oneWayConverter;
            } else if (twoWayConverter != null && toClass.equals(from) && fromClass.equals(to)) {
                return this;
            }
            return null;
        }

        boolean checkCommand(Command command) {
            return oneWayConverter instanceof CommandPredictor
                    && ((CommandPredictor) oneWayConverter).checkCommand(command);
        }

        @Override
        public CarData convert(AppData value) {
            return Objects.requireNonNull(twoWayConverter).fromApp2Car(value);
        }
    }

    private static class BuildInValue {
        int intValue;
        int[] intArray;

        boolean booleanValue;
        boolean[] booleanArray;

        long longValue;
        long[] longArray;

        byte byteValue;
        byte[] byteArray;

        float floatValue;
        float[] floatArray;

        String stringValue;
        String[] stringArray;

        static BuildInValue build(CarValue value) {
            if (CarValue.EMPTY_VALUE.equals(value.string())) {
                return null;
            }
            BuildInValue result = new BuildInValue();

            result.intValue = value.Int();
            result.intArray = value.IntArray();

            result.booleanValue = value.Boolean();
            result.booleanArray = value.BooleanArray();

            result.byteValue = value.Byte();
            result.byteArray = value.ByteArray();

            result.floatValue = value.Float();
            result.floatArray = value.FloatArray();

            result.longValue = value.Long();
            result.longArray = value.LongArray();

            result.stringValue = value.string();
            result.stringArray = value.stringArray();

            return result;
        }

        Object extractValue(Class<?> clazz) {
            if (String.class == clazz) {
                return stringValue;
            } else if (String[].class == clazz) {
                return stringArray;
            }
            else if (int.class == clazz) {
                return intValue;
            } else if (int[].class == clazz) {
                return intArray;
            }
            else if (byte.class == clazz) {
                return byteValue;
            } else if (byte[].class == clazz) {
                return byteArray;
            }
            else if (float.class == clazz) {
                return floatValue;
            } else if (float[].class == clazz) {
                return floatArray;
            }
            else if (long.class == clazz) {
                return longValue;
            } else if (long[].class == clazz) {
                return longArray;
            }
            else {
                throw new CarRetrofitException("invalid type:" + clazz);
            }
        }
    }

    public static class CarRetrofitException extends RuntimeException {
        CarRetrofitException(String msg) {
            super(msg);
        }

        CarRetrofitException(String msg, Throwable cause) {
            super(msg, cause);
        }

        CarRetrofitException(Throwable cause) {
            super(cause);
        }
    }
}
