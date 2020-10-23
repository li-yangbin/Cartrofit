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
import com.liyangbin.carretrofit.funtion.FunctionalCombinator;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public final class CarRetrofit {

    private static final ConverterStore GLOBAL_CONVERTER = new ConverterStore();
    private static final Object SKIP = new Object();
    private static CarRetrofit sDefault;
    private static final StickyType DEFAULT_STICKY_TYPE = StickyType.ON;

    private static final int FLAG_FIRST_BIT = 1;

    private static final int FLAG_PARSE_SET = FLAG_FIRST_BIT;
    private static final int FLAG_PARSE_GET = FLAG_FIRST_BIT << 1;
    private static final int FLAG_PARSE_TRACK = FLAG_FIRST_BIT << 2;
    private static final int FLAG_PARSE_UN_TRACK = FLAG_FIRST_BIT << 3;
    private static final int FLAG_PARSE_INJECT = FLAG_FIRST_BIT << 4;
    private static final int FLAG_PARSE_APPLY = FLAG_FIRST_BIT << 5;
    private static final int FLAG_PARSE_COMBINE = FLAG_FIRST_BIT << 6;
    private static final int FLAG_PARSE_ALL = FLAG_PARSE_SET | FLAG_PARSE_GET | FLAG_PARSE_TRACK
            | FLAG_PARSE_UN_TRACK | FLAG_PARSE_INJECT | FLAG_PARSE_APPLY | FLAG_PARSE_COMBINE;
    private static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();
    private static Method sRouterFinderMethod;

    private final HashMap<String, DataSource> mDataMap;
    private InterceptorChain mChainHead;
    private final StickyType mDefaultStickyType;
    private final ArrayList<ApiCallback> mApiCallback = new ArrayList<>();

    private final HashMap<Class<?>, ApiRecord<?>> mApiCache = new HashMap<>();
    private final HashMap<Key, CommandImpl> mCommandCache = new HashMap<>();

    static {
        RxJavaConverter.addSupport();
        ObservableConverter.addSupport();
        LiveDataConverter.addSupport();
    }

    private CarRetrofit(Builder builder) {
        mDataMap = builder.dataMap;
        mDefaultStickyType = builder.stickyType;
        if (mDataMap.isEmpty()) {
            throw new IllegalArgumentException("CarRetrofit must be setup with data source");
        }
        mDataMap.put("", new DummyDataSource());
        for (int i = 0; i < builder.interceptors.size(); i++) {
            if (mChainHead == null) {
                mChainHead = new InterceptorChain();
            }
            mChainHead.addInterceptor(builder.interceptors.get(i));
        }
        mApiCallback.addAll(builder.apiCallbacks);
    }

    private static class DummyDataSource implements DataSource {
        @Override
        public Object get(int key, int area, CarType type) {
            throw new IllegalStateException("impossible");
        }
        @Override
        public <TYPE> void set(int key, int area, TYPE value) {
            throw new IllegalStateException("impossible");
        }
        @Override
        public Flow<CarPropertyValue<?>> track(int key, int area) {
            throw new IllegalStateException("impossible");
        }
        @Override
        public Class<?> extractValueType(int key) {
            throw new IllegalStateException("impossible");
        }
        @Override
        public String getScopeId() {
            return "";
        }
        @Override
        public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
        }
    }

    public static void setDefault(CarRetrofit retrofit) {
        sDefault = Objects.requireNonNull(retrofit);
    }

    public static CarRetrofit getDefault() {
        return sDefault;
    }

    public static <T> T fromDefault(Class<T> api) {
        return Objects.requireNonNull(sDefault,
                "Call setDefault() before calling fromDefault()").from(api);
    }

    private static Class<?> findApiClassById(int id) {
        try {
            if (sRouterFinderMethod == null) {
                Class<?> idRouterClass = Class.forName(CarRetrofit.class.getPackage().getName()
                        + ".IdRouter");
                sRouterFinderMethod = idRouterClass.getMethod("findApiClassById", int.class);
            }
            return (Class<?>) sRouterFinderMethod.invoke(null, id);
        } catch (ReflectiveOperationException ignore) {
            return null;
        }
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

    private class ApiRecord<T> implements ApiBuilder {
        private static final String ID_SUFFIX = "Id";

        Class<T> clazz;
        String dataScope;
        int apiArea;
        T apiObj;
        StickyType stickyType;
        DataSource source;

        ArrayList<Key> childrenInjectKey;
        ArrayList<Key> childrenApplyKey;

        ArrayList<ApiRecord<?>> parentApi;

        HashMap<Integer, Method> selfDependency = new HashMap<>();
        HashMap<Method, Integer> selfDependencyReverse = new HashMap<>();
        ConverterManager converterManager = new ConverterManager();
        InterceptorManager interceptorManager = new InterceptorManager();

        Interceptor tempInterceptor;
        Converter<?, ?> tempConverter;

        ApiRecord(Class<T> clazz) {
            this.clazz = clazz;
            CarApi carApi = clazz.getAnnotation(CarApi.class);
            if (carApi != null) {
                this.dataScope = carApi.scope();
                this.apiArea = carApi.area();
                this.stickyType = carApi.defaultSticky();
                if (stickyType == StickyType.NO_SET) {
                    stickyType = mDefaultStickyType;
                }

                this.source = Objects.requireNonNull(mDataMap.get(dataScope),
                        "Invalid scope:" + dataScope +
                                " make sure use a valid scope registered in Builder().addDataSource()");

                if (clazz.isInterface()) {
                    try {
                        Class<?> selfScopeClass = Class.forName(clazz.getName() + ID_SUFFIX);
                        importDependency(selfScopeClass);
                    } catch (ClassNotFoundException ignore) {
                    }
                    this.source.onApiCreate(clazz, this);
                    for (int i = 0; i < mApiCallback.size(); i++) {
                        mApiCallback.get(i).onApiCreate(clazz, this);
                    }
                }
            }
        }

        int loadId(CommandImpl command) {
            if (command.key.method == null) {
                return 0;
            }
            return selfDependencyReverse.getOrDefault(command.key.method, 0);
        }

        @Override
        public ApiBuilder apply(Interceptor interceptor, int priority) {
            if (tempInterceptor != null) {
                throw new CarRetrofitException("Call apply(Interceptor) only once before commit");
            }
            tempInterceptor = interceptor;
            return this;
        }

        @Override
        public ApiBuilder apply(Converter<?, ?> converter) {
            if (tempConverter != null) {
                throw new CarRetrofitException("Call apply(Converter) only once before commit");
            }
            tempConverter = converter;
            return this;
        }

        @Override
        public void to(Constraint... constraints) {
            if (constraints == null || constraints.length == 0) {
                return;
            }
            if (tempInterceptor != null) {
                for (Constraint constraint : constraints) {
                    interceptorManager.add(constraint, tempInterceptor);
                }
                tempInterceptor = null;
            }
            if (tempConverter != null) {
                for (Constraint constraint : constraints) {
                    converterManager.add(constraint, tempConverter);
                }
                tempConverter = null;
            }
        }

        private abstract class AbstractManager<ELEMENT, GROUP> {
            ArrayList<Constraint> constraintList = new ArrayList<>();
            void addConstraint(Constraint constraint) {
                boolean inserted = false;
                for (int i = 0; i < constraintList.size(); i++) {
                    if (constraintList.get(i).priority > constraint.priority) {
                        constraintList.add(i, constraint);
                        inserted = true;
                        break;
                    }
                }
                if (!inserted) {
                    constraintList.add(constraint);
                }
            }
            abstract void add(Constraint constraint, ELEMENT element);
            abstract GROUP get(CommandImpl command);
        }

        private final class InterceptorManager extends AbstractManager<Interceptor, InterceptorChain> {
            HashMap<Constraint, ArrayList<Interceptor>> constraintMapper = new HashMap<>();

            @Override
            public void add(Constraint constraint, Interceptor element) {
                ArrayList<Interceptor> list = constraintMapper.get(constraint);
                if (list == null) {
                    list = new ArrayList<>();
                    constraintMapper.put(constraint, list);
                }
                list.add(element);
                addConstraint(constraint);
            }

            @Override
            InterceptorChain get(CommandImpl command) {
                InterceptorChain group = null;
                for (int i = 0; i < constraintList.size(); i++) {
                    Constraint constraint = constraintList.get(i);
                    if (constraint.check(command)) {
                        ArrayList<Interceptor> elements = constraintMapper.get(constraint);
                        final int size = elements.size();
                        if (size > 0) {
                            if (group == null) {
                                group = new InterceptorChain();
                            }
                            for (int j = 0; j < size; j++) {
                                group.addInterceptor(elements.get(j));
                            }
                        }
                    }
                }
                return group;
            }
        }

        private final class ConverterManager extends AbstractManager<Converter<?, ?>, ConverterStore> {
            HashMap<Constraint, ConverterStore> constraintMapper = new HashMap<>();

            @Override
            void add(Constraint constraint, Converter<?, ?> converter) {
                ConverterStore store = constraintMapper.get(constraint);
                if (store == null) {
                    store = new ConverterStore();
                    constraintMapper.put(constraint, store);
                }
                store.addConverter(converter);
                addConstraint(constraint);
            }

            @Override
            ConverterStore get(CommandImpl command) {
                ConverterStore current = null;
                for (int i = constraintList.size() - 1; i >= 0; i--) {
                    Constraint constraint = constraintList.get(i);
                    if (constraint.check(command)) {
                        ConverterStore node = constraintMapper.get(constraint);
                        if (node != null) {
                            node = node.copy();
                            if (current != null) {
                                current.addParentToEnd(node);
                            }
                            current = node;
                        }
                    }
                }
                return current;
            }
        }

        InterceptorChain getInterceptorByKey(CommandImpl command) {
            InterceptorChain chain = interceptorManager.get(command);
            if (command.delegateTarget() != command && mChainHead != null) {
                if (chain != null) {
                    chain.addInterceptorChainToBottom(mChainHead);
                } else {
                    chain = mChainHead;
                }
            }
            return chain;
        }

        ConverterStore getConverterByKey(CommandImpl command) {
            return converterManager.get(command);
        }

        void importDependency(Class<?> target) {
            try {
                Method method = target.getDeclaredMethod("init", HashMap.class);
                method.invoke(null, selfDependency);
            } catch (ReflectiveOperationException impossible) {
                throw new IllegalStateException(impossible);
            }
            for (Map.Entry<Integer, Method> entry : selfDependency.entrySet()) {
                selfDependencyReverse.put(entry.getValue(), entry.getKey());
            }
        }

        ArrayList<Key> getChildKey(boolean injectOrApply) {
            if (injectOrApply && childrenInjectKey != null) {
                return childrenInjectKey;
            } else if (!injectOrApply && childrenApplyKey != null) {
                return childrenApplyKey;
            }
            Field[] fields = clazz.getDeclaredFields();
            ArrayList<Key> result = new ArrayList<>();
            for (Field field : fields) {
                Key childKey = new Key(field, injectOrApply);
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
                            Key childKey = new Key(field, injectOrApply);
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
            if (injectOrApply) {
                childrenInjectKey = result;
            } else {
                childrenApplyKey = result;
            }
            return result;
        }

        ArrayList<ApiRecord<?>> getParentApi() {
            if (parentApi != null) {
                return parentApi;
            }
            ArrayList<ApiRecord<?>> result = new ArrayList<>();
            if (!clazz.isInterface()) {
                if (clazz.isAnnotationPresent(ConsiderSuper.class)) {
                    Class<?> superClass = clazz.getSuperclass();
                    if (superClass != null && superClass != Object.class) {
                        ApiRecord<?> record = getApi(clazz);
                        result.add(record);
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

    public static class ConverterStore implements Cloneable {
        final ArrayList<ConverterWrapper<?, ?>> converterWrapperList = new ArrayList<>();
        final ArrayList<ConverterWrapper<?, ?>> commandPredictorList = new ArrayList<>();
        final ArrayList<Converter<?, ?>> allConverters = new ArrayList<>();
        ConverterStore parentStore;

        ConverterStore() {
        }

        ConverterStore copy() {
            try {
                ConverterStore copy = (ConverterStore) clone();
                copy.parentStore = null;
                return copy;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("impossible", e);
            }
        }

        void addParentToEnd(ConverterStore parent) {
            ConverterStore store = this;
            while (store.parentStore != null) {
                store = store.parentStore;
            }
            store.parentStore = parent;
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

        static Converter<?, ?> find(CommandImpl command, Class<?> from, Class<?> to,
                                    ConverterStore store) {
            if (command.explicitConverter != null) {
                return command.explicitConverter;
            }
            from = from.isPrimitive() ? boxTypeOf(from) : from;
            to = to.isPrimitive() ? boxTypeOf(to) : to;
            Converter<?, ?> converter = store.findWithCommand(command, from, to);
            if (converter != null) {
                return converter;
            }
            converter = store.findWithoutCommand(from, to);
            if (from == to || converter != null) {
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
        private final HashMap<String, DataSource> dataMap = new HashMap<>();
        private final ArrayList<Interceptor> interceptors = new ArrayList<>();
        private final ArrayList<ApiCallback> apiCallbacks = new ArrayList<>();
        private StickyType stickyType = DEFAULT_STICKY_TYPE;

        public Builder addDataSource(DataSource source) {
            DataSource existedSource = dataMap.put(source.getScopeId(), source);
            if (existedSource != null) {
                throw new CarRetrofitException("Duplicate data source:" + existedSource);
            }
            return this;
        }

        public Builder addInterceptor(Interceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        public Builder addApiCallback(ApiCallback callback) {
            apiCallbacks.add(callback);
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
                if (api.isAnnotationPresent(CarApi.class) || !throwIfNotDeclareCarApi) {
                    record = new ApiRecord<>(api);
                    mApiCache.put(api, record);
                } else {
                    throw new CarRetrofitException("Do declare CarApi annotation in class:" + api);
                }
            }
            return record;
        }
    }

    private CommandImpl getOrCreateCommand(ApiRecord<?> record, Key key) {
        return getOrCreateCommandIfChecked(record, key, FLAG_PARSE_ALL);
    }

    private CommandImpl getOrCreateCommandById(ApiRecord<?> record, int id, int flag) {
        return getOrCreateCommandById(record, id, flag, true);
    }

    private CommandImpl getOrCreateCommandById(ApiRecord<?> record, int id, int flag,
                                               boolean throwIfNotFound) {
        Method method = record.selfDependency.get(id);
        if (method == null) {
            Class<?> apiClass = findApiClassById(id);
            if (apiClass == record.clazz || apiClass == null) {
                throw new CarRetrofitException("Can not find target Id:" + id + " from:" + apiClass);
            }
            return getOrCreateCommandById(getApi(apiClass, true), id, flag, throwIfNotFound);
        }
        CommandImpl command = getOrCreateCommandIfChecked(getApi(method.getDeclaringClass()),
                new Key(method), flag);
        if (throwIfNotFound && command == null) {
            throw new CarRetrofitException("Can not resolve target Id:" + id
                    + " in specific type from:" + this);
        }
        return command;
    }

    private CommandImpl getOrCreateCommandIfChecked(ApiRecord<?> record, Key key, int flag) {
        if (record == null) {
            return null;
        }
        CommandImpl command;
        synchronized (mCommandCache) {
            command = mCommandCache.get(key);
        }
        if (command != null) {
            return command;
        }
        if (command == null) {
            key.doQualifyCheck();
            if (key.isInvalid()) {
                throw new CarRetrofitException("Invalid key:" + key);
            }
            command = createCommand(record, key, flag);
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
        private static final Class<? extends Annotation>[] QUALIFY_CHECK =
                new Class[]{Get.class, Set.class, Delegate.class,
                        Combine.class, Apply.class, Inject.class};

        private static final Class<? extends Annotation>[] QUALIFY_INJECT_CHECK =
                new Class[]{Get.class, Delegate.class, Combine.class, Inject.class};

        private static final Class<? extends Annotation>[] QUALIFY_APPLY_CHECK =
                new Class[]{Set.class, Delegate.class, Apply.class};

        Method method;

        Field field;
        final boolean injectOrApply;

        Key(Method method) {
            this.method = method;
            this.injectOrApply = false;
        }

        Key(Field field, boolean injectOrApply) {
            this.field = field;
            this.injectOrApply = injectOrApply;
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

        void doQualifyCheck() {
            boolean qualified = false;
            int checkIndex = 0;
            Class<? extends Annotation>[] checkMap = method != null ? QUALIFY_CHECK
                    : (injectOrApply ? QUALIFY_INJECT_CHECK : QUALIFY_APPLY_CHECK);
            while (checkIndex < checkMap.length) {
                if (isAnnotationPresent(checkMap[checkIndex++])) {
                    if (qualified) {
                        throw new CarRetrofitException("More than one annotation presented by:" + this);
                    }
                    qualified = true;
                }
            }
            if (injectOrApply && Modifier.isFinal(field.getModifiers())) {
                throw new CarRetrofitException("Invalid key:" + this
                        + " in command Inject");
            }
        }

        <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return method != null ? method.getDeclaredAnnotation(annotationClass)
                    : field.getDeclaredAnnotation(annotationClass);
        }

        <T extends Annotation> boolean isAnnotationPresent(Class<T> annotationClass) {
            return method != null ? method.isAnnotationPresent(annotationClass)
                    : field.isAnnotationPresent(annotationClass);
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

            if (injectOrApply != key.injectOrApply) return false;
            if (!Objects.equals(method, key.method)) return false;
            return Objects.equals(field, key.field);
        }

        @Override
        public int hashCode() {
            int result = method != null ? method.hashCode() : 0;
            result = 31 * result + (field != null ? field.hashCode() : 0);
            result = 31 * result + (injectOrApply ? 1 : 0);
            return result;
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

    private CommandImpl createCommand(ApiRecord<?> record, Key key, int flag) {
        Set set = (flag & FLAG_PARSE_SET) != 0 ? key.getAnnotation(Set.class) : null;
        if (set != null) {
            CommandSet command = new CommandSet();
            command.init(record, set, key);
            if (set.restoreTrack() != 0) {
                CommandTrack trackCommand = (CommandTrack) getOrCreateCommandById(record,
                        set.restoreTrack(), FLAG_PARSE_TRACK);
                trackCommand.setRestoreCommand(command);
            }
            return command;
        }

        Get get = (flag & FLAG_PARSE_GET) != 0 ? key.getAnnotation(Get.class) : null;
        if (get != null) {
            CommandGet command = new CommandGet();
            command.init(record, get, key);
            return command;
        }

        Track track = (flag & FLAG_PARSE_TRACK) != 0 ? key.getAnnotation(Track.class) : null;
        if (track != null) {
            CommandTrack command = new CommandTrack();
            if (track.restoreSet() != 0) {
                command.setRestoreCommand(getOrCreateCommandById(record, track.restoreSet(),
                        FLAG_PARSE_SET));
            }
            command.init(record, track, key);
            return command;
        }

        UnTrack unTrack = (flag & FLAG_PARSE_UN_TRACK) != 0 ? key.getAnnotation(UnTrack.class) : null;
        if (unTrack != null) {
            CommandUnTrack command = new CommandUnTrack();
            final int targetTrack = unTrack.track();
            command.setTrackCommand((CommandFlow) getOrCreateCommandById(record, targetTrack,
                    FLAG_PARSE_TRACK | FLAG_PARSE_COMBINE));
            command.init(record, unTrack, key);
            return command;
        }

        Inject inject = (flag & FLAG_PARSE_INJECT) != 0 ? key.getAnnotation(Inject.class) : null;
        if (inject != null) {
            Class<?> targetClass = key.getGetClass();
            if (targetClass.isPrimitive() || targetClass.isArray() || targetClass == String.class) {
                throw new CarRetrofitException("Can not use Inject on class type:" + targetClass);
            }
            CommandInject command = new CommandInject(targetClass, key.isInjectReturn());
            searchAndCreateChildCommand(getApi(targetClass), true,
                    command::addChildCommand,
                    FLAG_PARSE_INJECT | FLAG_PARSE_GET | FLAG_PARSE_TRACK | FLAG_PARSE_COMBINE);
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
            Class<?> targetClass = key.getSetClass();
            if (targetClass.isPrimitive() || targetClass.isArray() || targetClass == String.class) {
                throw new CarRetrofitException("Can not use Apply on class type:" + targetClass);
            }
            CommandApply command = new CommandApply(targetClass);
            searchAndCreateChildCommand(getApi(targetClass), false,
                    command::addChildCommand, FLAG_PARSE_APPLY | FLAG_PARSE_SET);
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
            CommandCombine command = new CommandCombine();
            int[] elements = combine.elements();
            if (elements.length <= 1) {
                throw new CarRetrofitException("Must declare more than one element on Combine:"
                        + key + " elements:" + Arrays.toString(elements));
            }
            for (int element : elements) {
                CommandImpl childCommand = getOrCreateCommandById(record, element,
                        FLAG_PARSE_GET | FLAG_PARSE_TRACK | FLAG_PARSE_COMBINE);
                command.addChildCommand(childCommand);
            }
            command.init(record, combine, key);
            return command;
        }

        Delegate delegate = key.getAnnotation(Delegate.class);
        if (delegate != null) {
            CommandImpl delegateTarget = getOrCreateCommandById(record, delegate.value(),
                    flag, false);
            if (delegateTarget != null) {
                CommandDelegate command = new CommandDelegate();
                command.setTargetCommand(delegateTarget);
                if (delegate.restoreId() != 0) {
                    command.restoreCommand = getOrCreateCommandById(record,
                            delegate.restoreId(), FLAG_PARSE_SET | FLAG_PARSE_TRACK);
                } else if (delegateTarget instanceof CommandFlow) {
                    command.restoreCommand = ((CommandFlow) delegateTarget).restoreCommand;
                }
                command.init(record, delegate, key);
                return command;
            }
        }
        return null;
    }

    private boolean searchAndCreateChildCommand(ApiRecord<?> record, boolean injectOrApply,
                                                Consumer<CommandImpl> commandReceiver, int flag) {
        ArrayList<Key> childKeys = record.getChildKey(injectOrApply);
        for (int i = 0; i < childKeys.size(); i++) {
            Key childKey = childKeys.get(i);
            CommandImpl command = getOrCreateCommandIfChecked(record, childKey, flag);
            if (command != null) {
                commandReceiver.accept(command);
            }
        }
        ArrayList<ApiRecord<?>> parentRecordList = record.getParentApi();
        for (int i = 0; i < parentRecordList.size(); i++) {
            boolean more = searchAndCreateChildCommand(parentRecordList.get(i), injectOrApply,
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
        ApiRecord<?> record;
        ConverterStore store;
        InterceptorChain chain;
        DataSource source;
        Class<?> userDataClass;
        Converter<?, ?> explicitConverter;
        String category;
        Key key;
        int id;

        final void init(ApiRecord<?> record, Annotation annotation, Key key) {
            this.record = record;
            this.source = record.source;
            if (this.source == null && requireSource()) {
                throw new CarRetrofitException("Declare CarApi.scope() attribute" +
                        " in your scope class:" + record.clazz);
            }
            this.key = key;

            this.chain = record.getInterceptorByKey(this);
            this.store = record.getConverterByKey(this);
            this.id = record.loadId(this);
            if (key.field != null) {
                key.field.setAccessible(true);
            }
            onInit(annotation);
        }

        boolean requireSource() {
            return true;
        }

        void onInit(Annotation annotation) {
        }

        void copyFrom(CommandImpl owner) {
            this.record = owner.record;
            this.source = owner.source;
            this.key = owner.key;
            this.propertyId = owner.propertyId;
            this.area = owner.area;

            this.chain = owner.chain;
            this.store = owner.store;
            this.id = owner.id;
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
                return (CommandImpl) delegateTarget().clone();
            } catch (CloneNotSupportedException error) {
                throw new CarRetrofitException(error);
            }
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public Method getMethod() {
            return key.method;
        }

        @Override
        public Field getField() {
            return key.field;
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
        public DataSource getSource() {
            return delegateTarget().source;
        }

        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public final String toString() {
            return "Command 0x" + Integer.toHexString(hashCode())
                    + " [" + type() + " " + toCommandString() + "]";
        }

        void addInterceptor(Interceptor interceptor, boolean toBottom) {
            if (chain != null) {
                if (toBottom) {
                    chain.addInterceptorToBottom(interceptor);
                } else {
                    chain.addInterceptor(interceptor);
                }
            } else {
                chain = new InterceptorChain(interceptor);
            }
        }

        CommandImpl delegateTarget() {
            return this;
        }

        boolean isReturnFlow() {
            return false;
        }

        void overrideFromDelegate(CommandFlow delegateCommand) {
        }

        String toCommandString() {
            int area = getArea();
            return "id:0x" + Integer.toHexString(getPropertyId())
                    + (area != CarApi.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "")
                    + " from:" + key;
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
                Object applyFrom = field.get(target);
                if (command.type() == CommandType.APPLY) {
                    if (applyFrom != null) {
                        command.invokeWithChain(applyFrom);
                    }
                } else {
                    command.invokeWithChain(applyFrom);
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
            }

            if (target instanceof InjectReceiver) {
                ((InjectReceiver) target).onAfterInjected();
            }

            return doReturn ? target : SKIP;
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

        CombineFlow combineFlow;

        @Override
        void addChildCommand(CommandImpl command) {
            CommandImpl childCopy = command.shallowCopy();
            childrenCommand.add(childCopy);
        }

        @Override
        boolean requireSource() {
            return false;
        }

        @Override
        void onInit(Annotation annotation) {
            Combine combine = (Combine) annotation;
            resolveStickyType(combine.sticky());
            if (stickyType == StickyType.NO_SET || stickyType == StickyType.OFF) {
                throw new CarRetrofitException("Must declare stickyType ON for combine command");
            }

            ArrayList<CommandFlow> flowChildren = null;
            ArrayList<CommandImpl> otherChildren = null;
            for (int i = 0; i < childrenCommand.size(); i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                childCommand.overrideFromDelegate(this);
                if (childCommand instanceof CommandFlow) {
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

//            combinatorId = combine.combinator();
//            try {
//                Object operatorObj = record.getConverterById(combinatorId);
//                String operatorFullName = Function2.class.getName();
//                String prefix = operatorFullName.substring(0, operatorFullName.lastIndexOf("."));
//                int childElementCount = childrenCommand.size();
//                String targetFunctionFullName = prefix + ".Function" + childElementCount;
//                functionClass = Class.forName(targetFunctionFullName);
//                if (functionClass.isInstance(operatorObj)) {
//                    this.combinator = (Converter<?, ?>) Objects.requireNonNull(operatorObj,
//                            "Failed to resolve id:" + combinatorId);
//                } else {
//                    throw new CarRetrofitException("Converter:" + combinatorId
//                            + " doesn't match element count:" + childElementCount);
//                }
//            } catch (ReflectiveOperationException e) {
//                throw new CarRetrofitException(e);
//            }

            resolveConverter();
        }

        @Override
        public boolean isReturnFlow() {
            for (int i = 0; i < childrenCommand.size(); i++) {
                if (childrenCommand.get(i).isReturnFlow()) {
                    return true;
                }
            }
            return false;
        }

        private void resolveConverter() {
            if (isReturnFlow()) {
                Converter<?, ?> converter = ConverterStore.find(this, Flow.class,
                        key.getTrackClass(), store);
                resultConverter = (Converter<Object, ?>) converter;

                mapConverter = findMapConverter(Object[].class);
                if (mapConverter == null) {
                    throw new CarRetrofitException("Must indicate a converter with Object[] type input");
                }
            } else {
                resultConverter = (Converter<Object, ?>) ConverterStore.find(this, Object[].class,
                        key.getTrackClass(), store);
                if (resultConverter == null) {
                    throw new CarRetrofitException("Must indicate a converter with Object[] type input");
                }
            }
        }

        @Override
        Object doInvoke() throws Throwable {
            if (combineFlow == null) {
                final int size = childrenCommand.size();
                Object[] elementResult = new Object[size];
                for (int i = 0; i < size; i++) {
                    CommandImpl childCommand = childrenCommand.get(i);
                    elementResult[i] = childCommand.invokeWithChain(null);
                }
                combineFlow = new CombineFlow(elementResult);
            }
            Object result;
            if (isReturnFlow()) {
                Flow<Object> flow;
                if (mapConverter instanceof FunctionalCombinator) {
                    flow = new MediatorFlow<>(combineFlow,
                            data -> ((FunctionalCombinator<?>) mapConverter)
                                    .apply(data.effectIndex, data.trackingObj));
                } else {
                    flow = new MediatorFlow<>(combineFlow,
                            data -> mapConverter.convert(data.trackingObj));
                }
                if (stickyType == StickyType.ON || stickyType == StickyType.ON_NO_CACHE) {
                    flow = new StickyFlowImpl<>(flow, stickyType == StickyType.ON,
                            getCommandStickyGet());
                    ((StickyFlowImpl<?>)flow).addCommandReceiver(createCommandReceive());
                } else {
                    flow = new FlowWrapper<>(flow, createCommandReceive());
                }
                if (mapFlowSuppressed) {
                    return flow;
                }
                result = flow;
            } else {
                result = loadInitialData();
            }
            return resultConverter != null ? resultConverter.convert(result) : result;
        }

        @Override
        Object loadInitialData() {
            if (combineFlow == null) {
                return null;
            }
            CombineData data = combineFlow.get();
            return mapConverter.convert(data.trackingObj);
        }

        @Override
        public CommandType type() {
            return CommandType.COMBINE;
        }

//        @Override
//        String toCommandString() {
//            return super.toCommandString() + " combinator:" + combinatorId;
//        }

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
        void onInit(Annotation annotation) {
            Set set = (Set) annotation;
            this.propertyId = set.id();
            buildInValue = BuildInValue.build(set.value());
            resolveArea(set.area());

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

        @Override
        void onInit(Annotation annotation) {
            Get get = (Get) annotation;
            propertyId = get.id();
            type = get.type();
            if (type == CarType.ALL) {
                throw new CarRetrofitException("Can not use type ALL mode in Get operation");
            }
            resolveArea(get.area());
            resolveResultConverter(key.getGetClass());
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
            Object obj = source.get(propertyId, area, type);
            return resultConverter != null ? resultConverter.convert(obj) : obj;
        }

        @Override
        public CommandType type() {
            return CommandType.GET;
        }

        @Override
        public boolean fromInject() {
            return key.field != null;
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

    private static abstract class CommandFlow extends CommandImpl {
        private static final Timer sTimeOutTimer = new Timer("timeout-tracker");
        private static final long TIMEOUT_DELAY = 1500;

        Converter<Object, ?> resultConverter;
        Converter<Object, ?> mapConverter;
        boolean mapFlowSuppressed;

        StickyType stickyType;

        private boolean registerTrack;
        ArrayList<CommandImpl> childrenCommand = new ArrayList<>();

        CommandImpl restoreCommand;

        // runtime variable
        CommandStickyGet commandStickyGet;
        Flow<Object> trackingFlow;
        TimerTask task;
        ArrayList<WeakReference<CommandReceive>> receiveCommandList;
        AtomicBoolean monitorSetResponseRegistered = new AtomicBoolean();

        /**
         * Principle: Flow command paired non-Flow command
         */
        void setRestoreCommand(CommandImpl restoreCommand) {
            if (isReturnFlow()) {
                if (restoreCommand.isReturnFlow()) {
                    throw new CarRetrofitException("Invalid flow restore:" + restoreCommand
                            + " on flow:" + this);
                }
                this.restoreCommand = restoreCommand;
            } else {
                if (!restoreCommand.isReturnFlow()) {
                    throw new CarRetrofitException("Invalid non-flow restore:" + restoreCommand
                            + " on non-Flow:" + this);
                }
                ((CommandFlow) restoreCommand).setupRestoreInterceptor(this);
            }
        }

        void setupRestoreInterceptor(CommandImpl restoreCommand) {
            if (stickyType == StickyType.NO_SET || stickyType == StickyType.OFF) {
                throw new CarRetrofitException("Sticky type must be ON if restore command is set");
            }
            System.out.println("setRestoreCommand :" + restoreCommand);
            restoreCommand.addInterceptor((command, parameter) -> {
                if (monitorSetResponseRegistered.get()) {
                    System.out.println("commandSet addInterceptorToBottom:" + parameter);
                    synchronized (sTimeOutTimer) {
                        if (task != null) {
                            task.cancel();
                        }
                        sTimeOutTimer.schedule(task = new TimerTask() {
                            @Override
                            public void run() {
                                System.out.println("commandSet restore command scheduled");
                                restoreDispatch();
                            }
                        }, TIMEOUT_DELAY);
                    }
                }
                return command.invoke(parameter);
            }, true);
        }

        CommandReceive createCommandReceive() {
            CommandReceive receive = new CommandReceive(this);
            if (restoreCommand != null) {
                if (receiveCommandList == null) {
                    receiveCommandList = new ArrayList<>();
                }
                receiveCommandList.add(new WeakReference<>(receive));
                if (monitorSetResponseRegistered.compareAndSet(false, true)) {
                    receive.addInterceptor((command, parameter) -> {
                        synchronized (sTimeOutTimer) {
                            if (task != null) {
                                System.out.println("commandSet restore command unscheduled");
                                task.cancel();
                                task = null;
                            }
                        }
                        return command.invoke(parameter);
                    }, false);
                }
            }
            return receive;
        }

        CommandStickyGet getCommandStickyGet() {
            if (commandStickyGet == null) {
                commandStickyGet = new CommandStickyGet(this);
            }
            return commandStickyGet;
        }

        void restoreDispatch() {
            ArrayList<WeakReference<CommandReceive>> commandReceiveList;
            synchronized (sTimeOutTimer) {
                task = null;
                if (receiveCommandList != null) {
                    commandReceiveList = (ArrayList<WeakReference<CommandReceive>>) receiveCommandList.clone();
                } else {
                    commandReceiveList = null;
                }
            }
            if (commandReceiveList != null) {
                boolean purgeExpired = false;
                for (int i = 0; i < commandReceiveList.size(); i++) {
                    WeakReference<CommandReceive> receiveRef = commandReceiveList.get(i);
                    CommandReceive commandReceive = receiveRef.get();
                    if (commandReceive != null) {
                        StickyFlow<?> stickyFlow = (StickyFlow<?>) commandReceive.flowWrapper;
                        try {
                            System.out.println("commandSet restore command executed");
                            commandReceive.invokeWithChain(stickyFlow.get());
                        } catch (Throwable throwable) {
                            throw new RuntimeException(throwable);
                        }
                    } else {
                        purgeExpired = true;
                    }
                }
                if (purgeExpired) {
                    synchronized (sTimeOutTimer) {
                        for (int i = receiveCommandList.size() - 1; i >= 0; i--) {
                            WeakReference<CommandReceive> receiveRef =
                                    receiveCommandList.get(i);
                            if (receiveRef.get() == null) {
                                receiveCommandList.remove(i);
                            }
                        }
                    }
                }
            }
        }

        void addChildCommand(CommandImpl command) {
            childrenCommand.add(command);
        }

        @Override
        CommandFlow shallowCopy() {
            CommandFlow commandFlow = (CommandFlow) super.shallowCopy();
            commandFlow.commandStickyGet = null;
            commandFlow.trackingFlow = null;
            commandFlow.task = null;
            commandFlow.receiveCommandList = null;
            commandFlow.monitorSetResponseRegistered = new AtomicBoolean();
            return commandFlow;
        }

        Object loadInitialData() throws Throwable {
            throw new IllegalStateException("impossible");
        }

        @Override
        void overrideFromDelegate(CommandFlow delegateCommand) {
            registerTrack = false;
            mapFlowSuppressed = true;
            if (delegateCommand.stickyType != StickyType.NO_SET) {
                stickyType = delegateCommand.stickyType;
            }
            if (delegateCommand.restoreCommand != null) {
                setRestoreCommand(delegateCommand.restoreCommand);
            }
        }

        void resolveStickyType(StickyType stickyType) {
            if (stickyType == StickyType.NO_SET) {
                this.stickyType = record.stickyType;
            } else {
                this.stickyType = stickyType;
            }
        }

        @Override
        void onInit(Annotation annotation) {
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
            if (isReturnFlow() && registerTrack) {
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
                fromSuper += " sticky:" + stickyType;
            }
            return fromSuper;
        }
    }

    private static class CommandTrack extends CommandFlow {
        CarType type;
        Annotation annotation;

        @Override
        void setRestoreCommand(CommandImpl restoreCommand) {
            super.setRestoreCommand(restoreCommand);
            setupRestoreInterceptor(restoreCommand);
        }

        @Override
        void onInit(Annotation annotation) {
            Track track = (Track) annotation;
            propertyId = track.id();
            type = track.type();
            this.annotation = annotation;
            if (type == CarType.CONFIG) {
                throw new CarRetrofitException("Can not use type CONFIG mode in Track operation");
            }

            resolveArea(track.area());
            resolveStickyType(track.sticky());

            resolveConverter();
        }

        @Override
        public boolean isReturnFlow() {
            return true;
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
                result = new StickyFlowImpl<>(result, stickyType == StickyType.ON,
                        getCommandStickyGet());
                ((StickyFlowImpl<?>)result).addCommandReceiver(createCommandReceive());
            } else {
                result = new FlowWrapper<>(result, createCommandReceive());
            }
            if (mapFlowSuppressed) {
                return result;
            }
            return resultConverter != null ? resultConverter.convert(result) : result;
//            return !valueMapped && mapConverter != null && resultConverter != null ?
//                    ((ConverterMapper<Object, Object>)resultConverter).map(obj, mapConverter) : obj;
        }

        @Override
        Object loadInitialData() throws Throwable {
            Object obj;
            if (type == CarType.ALL) {
                obj = source.get(propertyId, area, CarType.VALUE);
                return new CarPropertyValue<>(propertyId, area,
                        mapConverter != null ? mapConverter.convert(obj) : obj);
            } else {
                obj = source.get(propertyId, area, type);
                return mapConverter != null ? mapConverter.convert(obj) : obj;
            }
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
        void onInit(Annotation annotation) {
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
            copyFrom(commandFlow);
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            flowWrapper.superHandleResult(this, parameter);
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

        @Override
        String toCommandString() {
            return commandFlow.toCommandString();
        }
    }

    private static class CommandStickyGet extends CommandImpl {
        CommandFlow commandFlow;
        Converter<Object, ?> converter;
        CarType carType = CarType.VALUE;
        CommandStickyGet nextGetCommand;

        CommandStickyGet(CommandFlow commandFlow) {
            this.commandFlow = commandFlow;
            this.converter = commandFlow.mapConverter;
            if (commandFlow instanceof CommandTrack) {
                this.carType = ((CommandTrack) commandFlow).type;
            }
            copyFrom(commandFlow);
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            if (nextGetCommand != null) {
                return nextGetCommand.invokeWithChain(parameter);
            }
            return commandFlow.loadInitialData();
        }

        @Override
        CommandImpl delegateTarget() {
            return commandFlow;
        }

        @Override
        public CommandType type() {
            return CommandType.STICKY_GET;
        }

        @Override
        String toCommandString() {
            return commandFlow.toCommandString();
        }
    }

    private static class CommandDelegate extends CommandFlow {
        CommandImpl targetCommand;
        Converter<Object, ?> argConverter;
        CarType carType;
        boolean commandSet;

        @Override
        void onInit(Annotation annotation) {
            Delegate delegate = (Delegate) annotation;
            resolveStickyType(delegate.sticky());
            carType = delegate.type();
            commandSet = type() == CommandType.SET;

            targetCommand.overrideFromDelegate(this);

            resolveConverter();
        }

        @Override
        boolean requireSource() {
            return false;
        }

        @Override
        public boolean isReturnFlow() {
            return targetCommand.isReturnFlow();
        }

        private void resolveConverter() {
            if (commandSet) {
                if (targetCommand.userDataClass != null) {
                    argConverter = (Converter<Object, ?>) ConverterStore.find(this,
                            targetCommand.userDataClass, userDataClass = key.getSetClass(), store);
                }
            } else if (isReturnFlow()) {
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
            if (commandSet) {
                return targetCommand.invokeWithChain(argConverter != null ?
                        argConverter.convert(parameter) : parameter);
            }
            return super.invoke(parameter);
        }

        @Override
        Object doInvoke() throws Throwable {
            Object obj = targetCommand.invokeWithChain(null);
            Object result;
            if (obj instanceof FlowWrapper) {
                FlowWrapper<Object> flowWrapper = (FlowWrapper<Object>) obj;
                if (mapConverter != null) {
                    flowWrapper.addMediator((Function<Object, Object>) mapConverter);
                }
                flowWrapper.addCommandReceiver(createCommandReceive());
                result = flowWrapper;

                if (flowWrapper instanceof StickyFlowImpl) {
                    StickyFlowImpl<Object> stickyFlow = (StickyFlowImpl<Object>) flowWrapper;
                    stickyFlow.addCommandStickyGet(getCommandStickyGet());
                }
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

        @Override
        String toCommandString() {
            return key + " Delegate{" + targetCommand.toCommandString() + "}";
        }
    }

    private static class MediatorFlow<FROM, TO> implements Flow<TO>, Consumer<FROM> {

        private Flow<FROM> base;
        Function<FROM, TO> mediator;
        private ArrayList<Consumer<TO>> consumers = new ArrayList<>();

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

        void addMediator(Function<TO, TO> mediator) {
            this.mediator = this.mediator != null ? this.mediator.andThen(mediator)
                    : (Function<FROM, TO>) mediator;
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

        ArrayList<CommandReceive> receiverList = new ArrayList<>();

        private FlowWrapper(Flow<T> base, CommandReceive command) {
            this(base, null, command);
        }

        private FlowWrapper(Flow<T> base, Function<?, ?> function, CommandReceive receiver) {
            super(base, (Function<T, T>) function);
            addCommandReceiver(receiver);
        }

        void addCommandReceiver(CommandReceive receiver) {
            if (receiver != null) {
                receiverList.add(receiver);
                receiver.flowWrapper = (FlowWrapper<Object>) this;
            }
        }

        @Override
        void handleResult(T t) {
            if (receiverList.size() == 0) {
                super.handleResult(t);
            } else {
                try {
                    receiverList.get(0).invokeWithChain(t);
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            }
        }

        void superHandleResult(CommandReceive receiver, T t) {
            CommandReceive nextReceiver = null;
            synchronized (this) {
                for (int i = 0; i < receiverList.size() - 1; i++) {
                    if (receiverList.get(i) == receiver) {
                        nextReceiver = receiverList.get(i + 1);
                        break;
                    }
                }
            }
            if (nextReceiver != null) {
                try {
                    nextReceiver.invokeWithChain(t);
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
                return;
            }
            super.handleResult(t);
        }
    }

    private static class StickyFlowImpl<T> extends FlowWrapper<T> implements StickyFlow<T> {
        private T lastValue;
        private boolean valueReceived;
        private boolean stickyUseCache;
        private CommandStickyGet headStickyGetCommand;

        private StickyFlowImpl(Flow<T> base, boolean stickyUseCache, CommandStickyGet stickyGet) {
            super(base, null);
            this.stickyUseCache = stickyUseCache;
            this.headStickyGetCommand = stickyGet;
        }

        @Override
        public void addObserver(Consumer<T> consumer) {
            super.addObserver(consumer);
            consumer.accept(get());
        }

        @Override
        void handleResult(T value) {
            synchronized (this) {
                if (this.stickyUseCache) {
                    valueReceived = true;
                    lastValue = value;
                }
            }
            super.handleResult(value);
        }

        void addCommandStickyGet(CommandStickyGet commandStickyGet) {
            commandStickyGet.nextGetCommand = headStickyGetCommand;
            headStickyGetCommand = commandStickyGet;
        }

        private T loadInitialStickyData() {
            try {
                return (T) headStickyGetCommand.invokeWithChain(null);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        @Override
        public T get() {
            T result = null;
            boolean fetchFirstData = false;
            synchronized (this) {
                if (stickyUseCache && valueReceived) {
                    result = lastValue;
                } else {
                    fetchFirstData = true;
                }
            }
            if (fetchFirstData) {
                T t = loadInitialStickyData();
                result = mediator != null ? mediator.apply(t) : t;
            }
            synchronized (this) {
                if (stickyUseCache && !valueReceived) {
                    valueReceived = true;
                    lastValue = result;
                }
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
        public CarRetrofitException(String msg) {
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
