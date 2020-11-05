package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.Category;
import com.liyangbin.cartrofit.annotation.Combine;
import com.liyangbin.cartrofit.annotation.ConsiderSuper;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Inject;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.annotation.UnTrack;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.funtion.FunctionalCombinator;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
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
public final class Cartrofit {

    private static final ConverterStore GLOBAL_CONVERTER = new ConverterStore();
    private static Cartrofit sDefault;
    private static final StickyType DEFAULT_STICKY_TYPE = StickyType.ON;

    private static final int FLAG_FIRST_BIT = 1;

    private static final int FLAG_PARSE_SET = FLAG_FIRST_BIT;
    private static final int FLAG_PARSE_GET = FLAG_FIRST_BIT << 1;
    private static final int FLAG_PARSE_TRACK = FLAG_FIRST_BIT << 2;
    private static final int FLAG_PARSE_UN_TRACK = FLAG_FIRST_BIT << 3;
    private static final int FLAG_PARSE_INJECT = FLAG_FIRST_BIT << 4;
    private static final int FLAG_PARSE_COMBINE = FLAG_FIRST_BIT << 5;
    private static final int FLAG_PARSE_REGISTER = FLAG_FIRST_BIT << 6;
    private static final int FLAG_PARSE_INJECT_CHILDREN = FLAG_PARSE_SET | FLAG_PARSE_GET
            | FLAG_PARSE_TRACK | FLAG_PARSE_INJECT | FLAG_PARSE_COMBINE;
    private static final int FLAG_PARSE_ALL = FLAG_PARSE_SET | FLAG_PARSE_GET | FLAG_PARSE_TRACK
            | FLAG_PARSE_UN_TRACK | FLAG_PARSE_INJECT | FLAG_PARSE_COMBINE | FLAG_PARSE_REGISTER;
    private static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();
    private static Method sRouterFinderMethod;

    private final HashMap<String, DataSource> mDataMap = new HashMap<>();
    private InterceptorChain mChainHead;
    private final ArrayList<ApiCallback> mApiCallback = new ArrayList<>();

    private final HashMap<Class<?>, ApiRecord<?>> mApiCache = new HashMap<>();
    private final HashMap<Key, CommandImpl> mCommandCache = new HashMap<>();

    static {
        RxJavaConverter.addSupport();
        ObservableConverter.addSupport();
        LiveDataConverter.addSupport();
    }

    private Cartrofit() {
    }

    private void append(Builder builder) {
        mDataMap.putAll(builder.dataMap);
        if (mDataMap.isEmpty()) {
            throw new IllegalArgumentException("Cartrofit must be setup with data source");
        }
        for (int i = 0; i < builder.interceptors.size(); i++) {
            if (mChainHead == null) {
                mChainHead = new InterceptorChain();
            }
            mChainHead.addInterceptor(builder.interceptors.get(i));
        }
        mApiCallback.addAll(builder.apiCallbacks);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static <T> T from(Class<T> api) {
        return Objects.requireNonNull(sDefault,
                "Call setDefault() before calling fromDefault()").fromInternal(api);
    }

    private static Class<?> findApiClassById(int id) {
        try {
            if (sRouterFinderMethod == null) {
                Class<?> idRouterClass = Class.forName(Cartrofit.class.getPackage().getName()
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

    private class ApiRecord<T> extends ApiBuilder {
        private static final String ID_SUFFIX = "Id";

        Class<T> clazz;
        String dataScope;
        int apiArea;
        T apiObj;
        StickyType stickyType = StickyType.NO_SET;
        DataSource source;

        ArrayList<Key> childrenKey;

        ArrayList<ApiRecord<?>> parentApi;

        HashMap<Integer, Method> selfDependency = new HashMap<>();
        HashMap<Method, Integer> selfDependencyReverse = new HashMap<>();
        ConverterManager converterManager = new ConverterManager();
        InterceptorManager interceptorManager = new InterceptorManager();

        Interceptor tempInterceptor;
        AbsConverterBuilder converterBuilder;

        ApiRecord(Class<T> clazz) {
            this.clazz = clazz;
            Scope carScope = clazz.getAnnotation(Scope.class);
            if (carScope != null) {
                this.dataScope = carScope.value();

                this.source = Objects.requireNonNull(mDataMap.get(dataScope),
                        "Invalid scope:" + dataScope +
                                ". Make sure use a valid scope id registered in Builder().addDataSource()");

                if (carScope.publish() && clazz.isInterface()) {
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
        public void setDefaultAreaId(int areaId) {
            apiArea = areaId;
        }

        @Override
        public void setDefaultStickyType(StickyType stickyType) {
            this.stickyType = stickyType;
        }

        @Override
        public ApiBuilder intercept(Interceptor interceptor) {
            if (tempInterceptor != null) {
                throw new CartrofitException("Call intercept(Interceptor) only once before apply");
            }
            tempInterceptor = interceptor;
            return this;
        }

        @Override
        ApiBuilder convert(AbsConverterBuilder builder) {
            if (converterBuilder != null) {
                throw new CartrofitException("Call convert(Converter) only once before apply");
            }
            converterBuilder = builder;
            return this;
        }

        @Override
        public void apply(Constraint... constraints) {
            if (constraints == null || constraints.length == 0) {
                return;
            }
            if (tempInterceptor != null) {
                for (Constraint constraint : constraints) {
                    interceptorManager.add(constraint, tempInterceptor);
                }
                tempInterceptor = null;
            }
            if (converterBuilder != null) {
                for (Constraint constraint : constraints) {
                    converterManager.add(constraint, converterBuilder);
                }
                converterBuilder = null;
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
                                Interceptor interceptor = elements.get(j);
                                if (interceptor.checkCommand(command)) {
                                    group.addInterceptor(interceptor);
                                }
                            }
                        }
                    }
                }
                return group;
            }
        }

        private final class ConverterManager extends AbstractManager<AbsConverterBuilder, ConverterStore> {
            HashMap<Constraint, ConverterStore> constraintMapper = new HashMap<>();

            @Override
            void add(Constraint constraint, AbsConverterBuilder builder) {
                ConverterStore store = constraintMapper.get(constraint);
                if (store == null) {
                    store = new ConverterStore();
                    constraintMapper.put(constraint, store);
                }
                builder.apply(store);
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
                            } else {
                                current = node;
                            }
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
            ConverterStore store = converterManager.get(command);
            if (store != null) {
                store.addParentToEnd(GLOBAL_CONVERTER);
                return store;
            } else {
                return GLOBAL_CONVERTER;
            }
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

        ArrayList<Key> getChildKey() {
            ArrayList<Key> result = new ArrayList<>();
            if (clazz.isInterface()) {
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    Key childKey = new Key(method, true);
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
            }
            childrenKey = result;
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
                    (apiArea != Scope.DEFAULT_AREA_ID ?
                    ", apiArea=0x" + Integer.toHexString(apiArea) : "") +
                    '}';
        }
    }

    private static Class<?> lookUp(Class<?> clazz, Class<?> lookForTarget) {
        if (clazz == lookForTarget) {
            throw new CartrofitException("Invalid parameter:" + clazz);
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

    static class ConverterStore implements Cloneable {
        ArrayList<ConverterWrapper<?, ?>> converterWrapperList = new ArrayList<>();
        ConverterStore parentStore;

        ConverterStore() {
        }

        ConverterStore copy() {
            try {
                ConverterStore copy = (ConverterStore) clone();
                copy.parentStore = null;
                copy.converterWrapperList = new ArrayList<>(this.converterWrapperList);
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
                if (converter instanceof FunctionalCombinator) {
                    lookingFor = converter.getClass().getInterfaces()[0];
                } else {
                    lookingFor = Converter.class;
                }
            }
            Class<?> implementsBy = lookUp(converter.getClass(), lookingFor);
            if (implementsBy == null) {
                throw new CartrofitException("invalid input converter:" + converter.getClass());
            }
            if (implementsBy.isSynthetic()) {
                throw new CartrofitException("Do not use lambda expression in addConverter()");
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
                        } else if (FunctionalCombinator.class.isAssignableFrom(lookingFor)) {
                            convertFrom = Object[].class;
                            convertTo = getClassFromType(converterDeclaredType[converterDeclaredType.length - 1]);
                        } else {
                            convertFrom = getClassFromType(converterDeclaredType[0]);
                            convertTo = getClassFromType(converterDeclaredType[1]);
                        }
                        break;
                    }
                }
            }

            if (convertFrom != null && convertTo != null) {
                addConverter(convertFrom, convertTo, converter);
            } else {
                throw new CartrofitException("invalid converter class:" + implementsBy
                        + " type:" + Arrays.toString(ifTypes));
            }
        }

        void addConverter(Class<?> convertFrom, Class<?> convertTo, Converter<?, ?> converter) {
            if (checkConverter(convertFrom, convertTo)) {
                throw new CartrofitException("Can not add duplicate converter from:" +
                        toClassString(convertFrom) + " to:" + toClassString(convertTo));
            }
            converterWrapperList.add(new ConverterWrapper<>(convertFrom, convertTo,
                    converter));
            WrappedData wrappedData = converter.getClass()
                    .getDeclaredAnnotation(WrappedData.class);
            if (wrappedData != null) {
                synchronized (WRAPPER_CLASS_MAP) {
                    WRAPPER_CLASS_MAP.put(convertTo, wrappedData.type());
                }
            }
        }

        private boolean checkConverter(Class<?> from, Class<?> to) {
            final ConverterStore parent = this.parentStore;
            this.parentStore = null;
            Converter<?, ?> converter = findWithoutCommand(from, to);
            this.parentStore = parent;
            return converter != null;
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

        private static boolean classEquals(Class<?> a, Class<?> b) {
            if (a.equals(b)) {
                return true;
            }
            boolean aIsPrimitive = a.isPrimitive();
            boolean bIsPrimitive = b.isPrimitive();
            if (aIsPrimitive != bIsPrimitive) {
                if (aIsPrimitive && boxTypeOf(a).equals(b)) {
                    return true;
                }
                if (bIsPrimitive && boxTypeOf(b).equals(a)) {
                    return true;
                }
            }
            return false;
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
            Converter<?, ?> converter = store.findWithoutCommand(from, to);
            if (converter != null || classEquals(from, to)) {
                return converter;
            }
            throw new CartrofitException("Can not resolve converter from:"
                    + toClassString(from) + " to:" + toClassString(to)
                    + " with:" + command);
        }

        private static String toClassString(Class<?> clazz) {
            return clazz.isArray() ? clazz.getComponentType() + "[]" : clazz.toString();
        }
    }

    public static final class Builder {
        private final HashMap<String, DataSource> dataMap = new HashMap<>();
        private final ArrayList<Interceptor> interceptors = new ArrayList<>();
        private final ArrayList<ApiCallback> apiCallbacks = new ArrayList<>();

        private Builder() {
        }

        public Builder addDataSource(DataSource source) {
            Scope sourceScope = Objects.requireNonNull(
                    source.getClass().getDeclaredAnnotation(Scope.class));
            if (sourceScope.publish()) {
                throw new CartrofitException("Can not declare publish on data source:"
                        + source);
            }
            DataSource existedSource = dataMap.put(sourceScope.value(), source);
            if (existedSource != null) {
                throw new CartrofitException("Duplicate data source:" + existedSource);
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

        public void buildAsDefault() {
            if (sDefault == null) {
                sDefault = new Cartrofit();
            }
            sDefault.append(this);
            dataMap.clear();
            interceptors.clear();
            apiCallbacks.clear();
        }
    }

    public static void addGlobalConverter(Converter<?, ?>... converters) {
        for (Converter<?, ?> converter : converters) {
            GLOBAL_CONVERTER.addConverter(converter);
        }
    }

    private <T> T fromInternal(Class<T> api) {
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
                                "Do not declare any default method in Cartrofit interface");
                        }
                        if (args != null && args.length > 1) {
                            throw new UnsupportedOperationException(
                                    "Do not declare any method with multiple parameters");
                        }
                        return getOrCreateCommand(record, new Key(method, false))
                                .invokeWithChain(args != null ? args[0] : null);
                    });
        }
        return record.apiObj;
    }

    private <T> ApiRecord<T> getApi(Class<T> api) {
        return getApi(api, false);
    }

    private <T> ApiRecord<T> getApi(Class<T> api, boolean throwIfNotDeclareScope) {
        synchronized (mApiCache) {
            ApiRecord<T> record = (ApiRecord<T>) mApiCache.get(api);
            if (record == null) {
                if (api.isAnnotationPresent(Scope.class) || !throwIfNotDeclareScope) {
                    record = new ApiRecord<>(api);
                    mApiCache.put(api, record);
                } else {
                    throw new CartrofitException("Do declare CarApi annotation in class:" + api);
                }
            }
            return record;
        }
    }

    private CommandImpl getOrCreateCommand(ApiRecord<?> record, Key key) {
        CommandImpl command = getOrCreateCommand(record, key, FLAG_PARSE_ALL);
        if (command == null) {
            throw new CartrofitException("Can not parse command from:" + key);
        }
        return command;
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
                throw new CartrofitException("Can not find target Id:" + id + " from:" + apiClass);
            }
            return getOrCreateCommandById(getApi(apiClass, true), id, flag, throwIfNotFound);
        }
        CommandImpl command = getOrCreateCommand(getApi(method.getDeclaringClass()),
                new Key(method, false), flag);
        if (throwIfNotFound && command == null) {
            throw new CartrofitException("Can not resolve target Id:" + id
                    + " in specific type from:" + this);
        }
        return command;
    }

    private CommandImpl getOrCreateCommand(ApiRecord<?> record, Key key, int flag) {
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
        key.doQualifyCheck();
        command = createCommand(record, key, flag);
        if (command != null) {
            synchronized (mCommandCache) {
                mCommandCache.put(key, command);
            }
        }
        return command;
    }

    private static class Key {
        private static final Class<? extends Annotation>[] QUALIFY_CHECK =
                new Class[]{Get.class, Set.class, Delegate.class,
                        Combine.class, Inject.class, Register.class};

        private static final Class<? extends Annotation>[] QUALIFY_CALLBACK_CHECK =
                new Class[]{Delegate.class, Track.class, Combine.class};

        private static final Class<? extends Annotation>[] QUALIFY_INJECT_CHECK =
                new Class[]{Get.class, Set.class, Delegate.class, Combine.class, Inject.class};

        Method method;
        final boolean isCallbackEntry;
        int trackReceiveArgIndex = -1;

        Field field;

        Key(Method method, boolean isCallbackEntry) {
            this.method = method;
            this.isCallbackEntry = isCallbackEntry;
        }

        Key(Field field) {
            this.field = field;
            this.isCallbackEntry = false;
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
            Class<? extends Annotation>[] checkMap = method != null ?
                    (isCallbackEntry ? QUALIFY_CALLBACK_CHECK : QUALIFY_CHECK)
                    : QUALIFY_INJECT_CHECK;
            while (checkIndex < checkMap.length) {
                if (isAnnotationPresent(checkMap[checkIndex++])) {
                    if (qualified) {
                        throw new CartrofitException("More than one annotation presented by:" + this);
                    }
                    qualified = true;
                }
            }
            if (isCallbackEntry) {
                Set set = getAnnotation(Set.class);
                boolean setWithReturn;
                if (set != null) {
                    CarValue value = set.value();
                    setWithReturn = CarValue.EMPTY_VALUE.equals(value.string());
                } else {
                    Delegate delegate = getAnnotation(Delegate.class);
                    setWithReturn = delegate != null && delegate._return() != 0;
                }
                boolean declareReturn = method.getReturnType() != void.class;
                if (setWithReturn != declareReturn) {
                    throw new CartrofitException("Invalid declaration:" + method);
                }
                int parameterCount = method.getParameterCount();
                if (parameterCount > 0) {
                    Annotation[][] annotationAll = method.getParameterAnnotations();
                    int countWithInOut = 0;
                    anchor:for (int i = 0; i < annotationAll.length; i++) {
                        for (Annotation annotation : annotationAll[i]) {
                            if (annotation instanceof Out || annotation instanceof In) {
                                countWithInOut++;
                                continue anchor;
                            }
                        }
                        trackReceiveArgIndex = i;
                    }
                    if (parameterCount - countWithInOut > 1) {
                        throw new CartrofitException("Invalid parameter count " + this);
                    }
                }
            } else if (isAnnotationPresent(Inject.class)) {
                if (method != null) {
                    if (method.getReturnType() != void.class) {
                        throw new CartrofitException("Can not return any result by using Inject");
                    }
                    int parameterCount = method.getParameterCount();
                    if (parameterCount != 1) {
                        throw new CartrofitException("Can not declare parameter more or less than one " + this);
                    }
                    Annotation[] annotationOne = method.getParameterAnnotations()[0];
                    int countWithInOut = 0;
                    for (Annotation annotation : annotationOne) {
                        if (annotation instanceof Out || annotation instanceof In) {
                            countWithInOut++;
                        }
                    }
                    if (countWithInOut == 0) {
                        throw new CartrofitException("Invalid parameter declaration " + this);
                    }
                }
            } else if (field != null && Modifier.isFinal(field.getModifiers())
                    && isAnnotationPresent(Set.class)) {
                throw new CartrofitException("Invalid key:" + this + " in command Inject");
            }
            if (isInvalid()) {
                throw new CartrofitException("Invalid key:" + this);
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

        Class<?> getGetClass() {
            if (field != null) {
                return field.getType();
            } else if (method.getReturnType() != void.class) {
                return method.getReturnType();
            } else {
                Class<?>[] classArray = method.getParameterTypes();
                if (classArray.length != 1) {
                    throw new CartrofitException("Invalid parameter:"
                            + Arrays.toString(classArray));
                }
                return classArray[0];
            }
        }

        Class<?> getSetClass() {
            if (field != null) {
                return field.getType();
            } else if (isCallbackEntry) {
                return method.getReturnType();
            } else {
                Class<?>[] classArray = method.getParameterTypes();
                if (classArray.length != 1) {
                    throw new CartrofitException("Invalid parameter:"
                            + Arrays.toString(classArray));
                }
                if (method.getReturnType() != void.class) {
                    throw new CartrofitException("Invalid return type:"
                            + method.getReturnType());
                }
                return classArray[0];
            }
        }

        Class<?> getTrackClass() {
            if (field != null) {
                return field.getType();
            } else if (isCallbackEntry) {
                return method.getParameterTypes()[0];
            } else {
                Class<?> returnType = method.getReturnType();
                if (returnType == void.class) {
                    throw new CartrofitException("Invalid return void type");
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
                    throw new CartrofitException("Invalid return void type");
                }
                return returnType;
            } else {
                Type[] parameterArray = method.getGenericParameterTypes();
                if (parameterArray.length != 1) {
                    throw new CartrofitException("Invalid parameter:"
                            + Arrays.toString(parameterArray));
                }
                return parameterArray[0];
            }
        }

        Class<?> getUserConcernClass() {
            if (isCallbackEntry) {
                Class<?>[] parameterClassArray = method.getParameterTypes();
                return trackReceiveArgIndex > -1 ? parameterClassArray[trackReceiveArgIndex] : null;
            }
            Type originalType = getTrackType();
            if (hasUnresolvableType(originalType)) {
                throw new CartrofitException("Can not parse type:" + originalType + " from:" + this);
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
                    throw new CartrofitException("Can not extract target type:"
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

            if (isCallbackEntry != key.isCallbackEntry) return false;
            if (!Objects.equals(method, key.method)) return false;
            return Objects.equals(field, key.field);
        }

        @Override
        public int hashCode() {
            int result = method != null ? method.hashCode() : 0;
            result = 31 * result + (field != null ? field.hashCode() : 0);
            result = 31 * result + (isCallbackEntry ? 1 : 0);
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
            if (!key.isCallbackEntry && track.restoreSet() != 0) {
                command.setRestoreCommand(getOrCreateCommandById(record, track.restoreSet(),
                        FLAG_PARSE_SET));
            } else {
                setupCallbackEntryCommandIfNeeded(record, command, key);
            }
            command.init(record, track, key);
            if (!command.isStickyOn() && command.restoreCommand != null) {
                throw new CartrofitException("Must declare sticky On if you specify restore command" + key);
            }
            return command;
        }

        UnTrack unTrack = (flag & FLAG_PARSE_UN_TRACK) != 0 ? key.getAnnotation(UnTrack.class) : null;
        if (unTrack != null) {
            CommandUnTrack command = new CommandUnTrack();
            final int targetTrack = unTrack.track();
            command.setTrackCommand((CommandFlow) getOrCreateCommandById(record, targetTrack,
                    FLAG_PARSE_TRACK | FLAG_PARSE_COMBINE | FLAG_PARSE_REGISTER));
            command.init(record, unTrack, key);
            return command;
        }

        Inject inject = (flag & FLAG_PARSE_INJECT) != 0 ? key.getAnnotation(Inject.class) : null;
        if (inject != null) {
            return createInjectCommand(0, record, key);
        }

        Combine combine = (flag & FLAG_PARSE_COMBINE) != 0 ? key.getAnnotation(Combine.class) : null;
        if (combine != null) {
            CommandCombine command = new CommandCombine();
            int[] elements = combine.elements();
            if (elements.length <= 1) {
                throw new CartrofitException("Must declare more than one element on Combine:"
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

        Register callback = (flag & FLAG_PARSE_REGISTER) != 0 ? key.getAnnotation(Register.class) : null;
        if (callback != null) {
            Class<?> targetClass = key.getSetClass();
            if (!targetClass.isInterface()) {
                throw new CartrofitException("Declare CarCallback parameter as an interface:" + targetClass);
            }
            CommandRegister commandRegister = new CommandRegister();
            searchAndCreateChildCommand(getApi(targetClass),
                    command -> {
                        if (command.getType() != CommandType.TRACK) {
                            throw new CartrofitException("Illegal non-track on register callback:" + command);
                        }
                        Method method = command.getMethod();
                        final int parameterCount = method.getParameterCount();
                        ArrayList<CommandInject> outCommandList = new ArrayList<>();
                        for (int i = 0; i < parameterCount; i++) {
                            if (i == command.key.trackReceiveArgIndex) {
                                outCommandList.add(null);
                            } else {
                                outCommandList.add(createInjectCommand(i, record, command.key));
                            }
                        }
                        commandRegister.addChildCommand(command, outCommandList);
                    }, FLAG_PARSE_TRACK | FLAG_PARSE_COMBINE);
            if (commandRegister.childrenCommand.size() == 0) {
                throw new CartrofitException("Failed to resolve callback entry point in " + targetClass);
            }
            return commandRegister;
        }

        Delegate delegate = key.getAnnotation(Delegate.class);
        if (delegate != null) {
            CommandImpl delegateTarget = getOrCreateCommandById(record, delegate.value(),
                    flag, false);
            if (delegateTarget != null) {
                CommandDelegate command = new CommandDelegate();
                command.setTargetCommand(delegateTarget);
                if (!key.isCallbackEntry && delegate.restoreId() != 0) {
                    command.restoreCommand = getOrCreateCommandById(record,
                            delegate.restoreId(), FLAG_PARSE_SET | FLAG_PARSE_TRACK);
                } else if (delegateTarget instanceof CommandFlow) {
                    command.restoreCommand = ((CommandFlow) delegateTarget).restoreCommand;
                    setupCallbackEntryCommandIfNeeded(record, command, key);
                }
                command.init(record, delegate, key);
                return command;
            }
        }
        return null;
    }

    private CommandInject createInjectCommand(int parameterIndex,
                                              ApiRecord<?> parentRecord, Key key) {
        Class<?> targetClass;
        if (key.method != null) {
            targetClass = key.method.getParameterTypes()[parameterIndex];
        } else {
            targetClass = key.field.getType();
        }
        if (targetClass.isPrimitive() || targetClass.isArray() || targetClass == String.class) {
            throw new CartrofitException("Can not use Inject on class type:" + targetClass);
        }

        CommandReflect commandReflect = new CommandReflect();
        CommandInject command = new CommandInject(targetClass, parameterIndex, commandReflect);
        ApiRecord<?> reflectRecord = getApi(targetClass);
        searchAndCreateChildCommand(reflectRecord, commandReflect::addChildCommand,
                FLAG_PARSE_INJECT_CHILDREN);
        if (commandReflect.childrenCommand.size() == 0) {
            throw new CartrofitException("Failed to parse Inject command from type:"
                    + targetClass);
        }
        commandReflect.init(reflectRecord, null, null);
        command.init(parentRecord, null, key);
        return command;
    }

    private void setupCallbackEntryCommandIfNeeded(ApiRecord<?> record, CommandFlow entryCommand, Key key) {
        if (key.isCallbackEntry) {
            CommandImpl returnCommand = null;
            Set set = key.getAnnotation(Set.class);
            if (set != null) {
                returnCommand = new CommandSet();
                returnCommand.init(record, set, key);
            }
            Delegate returnDelegate = returnCommand == null ? key.getAnnotation(Delegate.class) : null;
            if (returnDelegate != null && returnDelegate._return() != 0) {
                CommandImpl delegateTarget = getOrCreateCommandById(record,
                        returnDelegate._return(), FLAG_PARSE_SET);
                CommandDelegate returnDelegateCommand = new CommandDelegate();
                returnDelegateCommand.setTargetCommand(delegateTarget);
                returnCommand = returnDelegateCommand;
            }
            if (returnCommand != null) {
                entryCommand.setReturnCommand(returnCommand);
            }
        }
    }

    private void searchAndCreateChildCommand(ApiRecord<?> record,
                                             Consumer<CommandImpl> commandReceiver, int flag) {
        ArrayList<Key> childKeys = record.getChildKey();
        for (int i = 0; i < childKeys.size(); i++) {
            Key childKey = childKeys.get(i);
            CommandImpl command = getOrCreateCommand(record, childKey, flag);
            if (command != null) {
                commandReceiver.accept(command);
            }
        }
        ArrayList<ApiRecord<?>> parentRecordList = record.getParentApi();
        for (int i = 0; i < parentRecordList.size(); i++) {
            searchAndCreateChildCommand(parentRecordList.get(i), commandReceiver, flag);
        }
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
        throw new CartrofitException("Expected a Class or ParameterizedType," +
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
        String[] category;
        Key key;
        int id;

        final void init(ApiRecord<?> record, Annotation annotation, Key key) {
            this.record = record;
            this.source = record.source;
            if (this.source == null && requireSource()) {
                throw new CartrofitException("Declare Scope in your api class:" + record.clazz);
            }
            this.key = key;

            if (getType() != CommandType.INJECT) {
                this.id = record.loadId(this);
                Category category = key.getAnnotation(Category.class);
                if (category != null) {
                    this.category = category.value();
                }
                this.chain = record.getInterceptorByKey(this);
                this.store = record.getConverterByKey(this);
                if (key.field != null) {
                    key.field.setAccessible(true);
                }
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
            this.category = owner.category;

            this.chain = owner.chain;
            this.store = owner.store;
            this.id = owner.id;
        }

        final void resolveArea(int userDeclaredArea) {
            if (userDeclaredArea != Scope.DEFAULT_AREA_ID) {
                this.area = userDeclaredArea;
            } else {
                if (this.record.apiArea != Scope.DEFAULT_AREA_ID) {
                    this.area = record.apiArea;
                } else {
                    this.area = Scope.GLOBAL_AREA_ID;
                }
            }
        }

        CommandImpl shallowCopy() {
            try {
                return (CommandImpl) delegateTarget().clone();
            } catch (CloneNotSupportedException error) {
                throw new CartrofitException(error);
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
        public int getPropertyId() {
            return delegateTarget().propertyId;
        }

        @Override
        public int getArea() {
            return delegateTarget().area;
        }

        @Override
        public String[] getCategory() {
            return category;
        }

        @Override
        public Class<?> getInputType() {
            return null;
        }

        @Override
        public Class<?> getOutputType() {
            return userDataClass;
        }

        @Override
        public final String toString() {
            return "Cmd " + getType() + " 0x" + Integer.toHexString(hashCode())
                    + " [" + toCommandString() + "]";
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
            return key.toString();
        }

        final Object invokeWithChain(Object parameter) {
            if (chain != null) {
                return chain.doProcess(this, parameter);
            } else {
                return invoke(parameter);
            }
        }
    }

    private static class CommandReflect extends CommandGroup {

        CommandInject commandInject;

        void setCommandInject(CommandInject commandInject) {
            this.commandInject = commandInject;
        }

        static boolean isGet(CommandType type) {
            switch (type) {
                case GET:
                case TRACK:
                case COMBINE:
                    return true;
            }
            return false;
        }

        @Override
        public Object invoke(Object parameter) {
            final InjectInfo info = (InjectInfo) parameter;
            final Object target = info.target;

            if (target instanceof InjectReceiver) {
                if (((InjectReceiver) target).onBeforeInject(commandInject)) {
                    return null;
                }
            }

            for (int i = 0; i < childrenCommand.size(); i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                CommandType childType = childCommand.getType();
                if (childType == CommandType.INJECT) {
                    childCommand.invoke(parameter);
                } else {
                    boolean isGetCommand = isGet(childType);
                    try {
                        if (info.get && isGetCommand) {
                            childCommand.getField().set(target,
                                    childCommand.invokeWithChain(null));
                        } else if (info.set && !isGetCommand) {
                            Object setValue = childCommand.getField().get(target);
                            childCommand.invokeWithChain(setValue);
                        }
                    } catch (IllegalAccessException impossible) {
                        throw new RuntimeException(impossible);
                    }
                }
            }

            if (target instanceof InjectReceiver) {
                ((InjectReceiver) target).onAfterInject(commandInject);
            }

            return null;
        }

        @Override
        public CommandType getType() {
            return CommandType.INJECT;
        }
    }

    private static class InjectInfo {
        boolean get;
        boolean set;
        Object target;

        InjectInfo copy() {
            InjectInfo copy = new InjectInfo();
            copy.get = this.get;
            copy.set = this.set;
            return copy;
        }

        @Override
        public String toString() {
            return "InjectInfo{" +
                    "get=" + get +
                    ", set=" + set +
                    ", target=" + target +
                    '}';
        }
    }

    private static class CommandInject extends CommandImpl {

        CommandReflect commandReflect;
        private InjectInfo dispatchInfo;
        private final Class<?> injectClass;
        private final int parameterIndex;

        CommandInject(Class<?> injectClass, int parameterIndex, CommandReflect commandReflect) {
            this.injectClass = injectClass;
            this.parameterIndex = parameterIndex;
            this.commandReflect = commandReflect;
            commandReflect.setCommandInject(this);
        }

        @Override
        boolean requireSource() {
            return false;
        }

        @Override
        void onInit(Annotation annotation) {
            if (key.method != null || key.isCallbackEntry) {
                dispatchInfo = new InjectInfo();
                Annotation[] annotations = key.method.getParameterAnnotations()[parameterIndex];
                for (Annotation parameterAnno : annotations) {
                    if (key.isCallbackEntry) {
                        dispatchInfo.get |= parameterAnno instanceof In;
                        dispatchInfo.set |= parameterAnno instanceof Out;
                    } else {
                        dispatchInfo.set |= parameterAnno instanceof In;
                        dispatchInfo.get |= parameterAnno instanceof Out;
                    }
                }
            }
        }

        void suppressGetAndExecute(Object object) {
            final boolean oldGetEnable = dispatchInfo.get;
            dispatchInfo.get = false;
            if (shouldExecuteReflectOperation(dispatchInfo)) {
                dispatchInfo.target = object;
                commandReflect.invoke(dispatchInfo);
                dispatchInfo.target = null;
            }
            dispatchInfo.get = oldGetEnable;
        }

        void suppressSetAndExecute(Object object) {
            final boolean oldSetEnable = dispatchInfo.set;
            dispatchInfo.set = false;
            if (shouldExecuteReflectOperation(dispatchInfo)) {
                dispatchInfo.target = object;
                commandReflect.invoke(dispatchInfo);
                dispatchInfo.target = null;
            }
            dispatchInfo.set = oldSetEnable;
        }

        static boolean shouldExecuteReflectOperation(InjectInfo info) {
            return info.set || info.get;
        }

        @Override
        public Class<?> getInputType() {
            return injectClass;
        }

        @Override
        public Object invoke(Object parameter) {
            if (dispatchInfo != null) {
                if (shouldExecuteReflectOperation(dispatchInfo)) {
                    dispatchInfo.target = parameter;
                    commandReflect.invoke(dispatchInfo);
                    dispatchInfo.target = null;
                }
            } else if (parameter instanceof InjectInfo) {
                InjectInfo dispatchedInfo = (InjectInfo) parameter;
                if (shouldExecuteReflectOperation(dispatchedInfo)) {
                    InjectInfo info = dispatchedInfo.copy();
                    try {
                        info.target = key.field.get(dispatchedInfo.target);
                    } catch (IllegalAccessException impossible) {
                        throw new RuntimeException(impossible);
                    }
                    if (dispatchedInfo.set && info.target == null) {
                        throw new NullPointerException("Can not resolve target:" + key);
                    }
                    commandReflect.invoke(info);
                    info.target = null;
                }
            } else {
                throw new RuntimeException("impossible situation parameter:" + parameter
                        + " from:" + this);
            }
            return null;
        }

        @Override
        public CommandType getType() {
            return CommandType.INJECT;
        }

        @Override
        String toCommandString() {
            String stable = injectClass.getSimpleName();
            return stable + " " + super.toCommandString();
        }
    }

    private static abstract class CommandGroup extends CommandImpl {
        ArrayList<CommandImpl> childrenCommand = new ArrayList<>();

        void addChildCommand(CommandImpl command) {
            childrenCommand.add(command);
        }

        @Override
        boolean requireSource() {
            return false;
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
            super.onInit(annotation);
            Combine combine = (Combine) annotation;
            resolveStickyType(combine.sticky());
            if (stickyType == StickyType.NO_SET || stickyType == StickyType.OFF) {
                throw new CartrofitException("Must declare stickyType ON for combine command");
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
                            throw new CartrofitException("Duplicate combine element:" + this);
                        }
                    }
                }
            }

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
                    throw new CartrofitException("Must indicate a converter with Object[] type input");
                }
            } else {
                resultConverter = (Converter<Object, ?>) ConverterStore.find(this, Object[].class,
                        key.getTrackClass(), store);
                if (resultConverter == null) {
                    throw new CartrofitException("Must indicate a converter with Object[] type input");
                }
            }
        }

        @Override
        Object doInvoke() {
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
                            data -> ((FunctionalCombinator) mapConverter)
                                    .apply(data.effectIndex, data.trackingObj));
                } else {
                    flow = new MediatorFlow<>(combineFlow,
                            data -> mapConverter.convert(data.trackingObj));
                }
                if (isStickyOn()) {
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
        public CommandType getType() {
            return CommandType.COMBINE;
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
                throw new CartrofitException(e);
            }
            Converter<?, ?> converter = ConverterStore.find(this,
                    userArgClass, carArgClass, store);
            userDataClass = userArgClass;
            if (converter != null) {
                argConverter = (Converter<Object, ?>) converter;
            }
        }

        Object collectArgs(Object parameter) {
            if (buildInValue != null) {
                return buildInValue.extractValue(source.extractValueType(propertyId));
            }
            return argConverter != null && parameter != null ?
                    argConverter.convert(parameter) : parameter;
        }

        @Override
        public Object invoke(Object parameter) {
            source.set(propertyId, area, collectArgs(parameter));
            return null;
        }

        @Override
        public CommandType getType() {
            return CommandType.SET;
        }

        @Override
        public Class<?> getInputType() {
            return key.getSetClass();
        }

        @Override
        String toCommandString() {
            String stable = "id:0x" + Integer.toHexString(getPropertyId())
                    + (area != Scope.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "");
            return stable + super.toCommandString();
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
                throw new CartrofitException("Can not use type ALL mode in Get operation");
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
                throw new CartrofitException(e);
            }
            Converter<?, ?> converter = ConverterStore.find(this, carReturnClass,
                    userReturnClass, store);
            resultConverter = (Converter<Object, ?>) converter;
            userDataClass = userReturnClass;
        }

        @Override
        public Object invoke(Object parameter) {
            Object obj = source.get(propertyId, area, type);
            return resultConverter != null ? resultConverter.convert(obj) : obj;
        }

        @Override
        public CommandType getType() {
            return CommandType.GET;
        }

        @Override
        String toCommandString() {
            String stable = "id:0x" + Integer.toHexString(getPropertyId())
                    + (area != Scope.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "");
            if (type != CarType.VALUE) {
                stable += " valueType:" + type;
            }
            return stable + super.toCommandString();
        }
    }

    private interface UnTrackable extends Command {
        void untrack(Object obj);
    }

    private static abstract class CommandFlow extends CommandImpl implements UnTrackable {
        private static final Timer sTimeOutTimer = new Timer("timeout-tracker");
        private static final long TIMEOUT_DELAY = 1500;

        Converter<Object, ?> resultConverter;
        Converter<Object, ?> mapConverter;
        boolean mapFlowSuppressed;

        StickyType stickyType;

        private boolean registerTrack;
        ArrayList<CommandImpl> childrenCommand = new ArrayList<>();

        CommandImpl restoreCommand;
        CommandImpl returnCommand;

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
                    throw new CartrofitException("Invalid flow restore:" + restoreCommand
                            + " on flow:" + this);
                }
                this.restoreCommand = restoreCommand;
            } else {
                if (!restoreCommand.isReturnFlow()) {
                    throw new CartrofitException("Invalid non-flow restore:" + restoreCommand
                            + " on non-Flow:" + this);
                }
                ((CommandFlow) restoreCommand).setupRestoreInterceptor(this);
            }
        }

        void setupRestoreInterceptor(CommandImpl restoreCommand) {
            if (stickyType == StickyType.NO_SET || stickyType == StickyType.OFF) {
                throw new CartrofitException("Sticky type must be ON if restore command is set");
            }
            restoreCommand.addInterceptor((command, parameter) -> {
                if (monitorSetResponseRegistered.get()) {
                    synchronized (sTimeOutTimer) {
                        if (task != null) {
                            task.cancel();
                        }
                        sTimeOutTimer.schedule(task = new TimerTask() {
                            @Override
                            public void run() {
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
                        commandReceive.invokeWithChain(stickyFlow.get());
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

        // used for CommandCombine
        void addChildCommand(CommandImpl command) {
            childrenCommand.add(command);
        }

        void setReturnCommand(CommandImpl command) {
            returnCommand = command;
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

        Object loadInitialData() {
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
            if (key.isCallbackEntry) {
                registerTrack = true;
                mapFlowSuppressed = true;
            } else if (key.method != null && key.method.getReturnType() == void.class) {
                Class<?>[] classArray = key.method.getParameterTypes();
                if (classArray.length == 1) {
                    Class<?> parameterClass = classArray[0];
                    if (Consumer.class.isAssignableFrom(parameterClass)) {
                        registerTrack = true;
                        mapFlowSuppressed = true;
                    } else {
                        throw new CartrofitException("invalid track parameter:" + this);
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
        public Object invoke(Object parameter) {
            if (isReturnFlow() && registerTrack) {
                if (trackingFlow == null) {
                    trackingFlow = (Flow<Object>) doInvoke();
                }
                Consumer<Object> consumer = (Consumer<Object>) parameter;
                trackingFlow.addObserver(consumer);
                return null;
            } else {
                return doInvoke();
            }
        }

        @Override
        public void untrack(Object callback) {
            if (trackingFlow != null) {
                trackingFlow.removeObserver((Consumer<Object>) callback);
            }
        }

        abstract Object doInvoke();

        @Override
        String toCommandString() {
            String fromSuper = super.toCommandString();
            if (stickyType != StickyType.NO_SET) {
                fromSuper += " sticky:" + stickyType;
            }
            return fromSuper;
        }

        boolean isStickyOn() {
            return stickyType == StickyType.ON || stickyType == StickyType.ON_NO_CACHE;
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
            super.onInit(annotation);
            Track track = (Track) annotation;
            propertyId = track.id();
            type = track.type();
            this.annotation = annotation;
            if (type == CarType.CONFIG) {
                throw new CartrofitException("Can not use type CONFIG mode in Track operation");
            }

            resolveArea(track.area());
            resolveStickyType(track.sticky());

            resolveConverter();
        }

        @Override
        public boolean isReturnFlow() {
            return true;
        }

        private void resolveConverter() {
            if (!mapFlowSuppressed) {
                Converter<?, ?> converter = ConverterStore.find(this, Flow.class,
                        key.getTrackClass(), store);
                resultConverter = (Converter<Object, ?>) converter;
            }

            if (type != CarType.NONE) {
                Class<?> carType;
                if (type == CarType.AVAILABILITY) {
                    carType = boolean.class;
                } else {
                    try {
                        carType = source.extractValueType(propertyId);
                    } catch (Exception e) {
                        throw new CartrofitException(e);
                    }
                }
                mapConverter = findMapConverter(carType);
            }
        }

        @Override
        Object doInvoke() {
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
            if (type != CarType.NONE && isStickyOn()) {
                result = new StickyFlowImpl<>(result, stickyType == StickyType.ON,
                        getCommandStickyGet());
                ((StickyFlowImpl<?>) result).addCommandReceiver(createCommandReceive());
            } else if (type == CarType.NONE) {
                result = new EmptyFlowWrapper((Flow<Void>) result, createCommandReceive());
            } else {
                result = new FlowWrapper<>(result, createCommandReceive());
            }
            if (mapFlowSuppressed) {
                return result;
            }
            return resultConverter != null ? resultConverter.convert(result) : result;
        }

        @Override
        Object loadInitialData() {
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
        public CommandType getType() {
            return CommandType.TRACK;
        }

        @Override
        String toCommandString() {
            String stable = "id:0x" + Integer.toHexString(getPropertyId())
                    + (area != Scope.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "");
            if (type != CarType.VALUE) {
                stable += " valueType:" + type;
            }
            return stable + super.toCommandString();
        }
    }

    private static class CommandRegister extends CommandGroup implements UnTrackable {

        private final HashMap<Object, RegisterCallbackWrapper> callbackWrapperMapper = new HashMap<>();
        private final HashMap<CommandImpl, ArrayList<CommandInject>> outParameterMap = new HashMap<>();

        @Override
        void onInit(Annotation annotation) {
            // ignore
        }

        void addChildCommand(CommandImpl command, ArrayList<CommandInject> outList) {
            addChildCommand(command);
            outParameterMap.put(command, outList);
        }

        @Override
        public Object invoke(Object callback) {
            if (callbackWrapperMapper.containsKey(
                    Objects.requireNonNull(callback, "callback can not be null"))) {
                throw new CartrofitException("callback:" + callback + " is already registered");
            }
            RegisterCallbackWrapper wrapper = new RegisterCallbackWrapper(callback);
            callbackWrapperMapper.put(callback, wrapper);
            wrapper.register();
            return null;
        }

        @Override
        public CommandType getType() {
            return CommandType.CALLBACK;
        }

        @Override
        public void untrack(Object callback) {
            RegisterCallbackWrapper wrapper = callbackWrapperMapper.remove(callback);
            if (wrapper != null) {
                wrapper.unregister();
            }
        }

        private class RegisterCallbackWrapper {
            Object callbackObj;
            ArrayList<InnerObserver> commandObserverList = new ArrayList<>();

            RegisterCallbackWrapper(Object callbackObj) {
                this.callbackObj = callbackObj;
            }

            void register() {
                if (commandObserverList.size() > 0) {
                    throw new CartrofitException("impossible situation");
                }
                for (int i = 0; i < childrenCommand.size(); i++) {
                    CommandFlow entry = (CommandFlow) childrenCommand.get(i);
                    InnerObserver observer = new InnerObserver(entry, callbackObj,
                            outParameterMap.get(entry));
                    commandObserverList.add(observer);
                    entry.invokeWithChain(observer);
                }
            }

            void unregister() {
                for (int i = 0; i < commandObserverList.size(); i++) {
                    CommandFlow entry = (CommandFlow) childrenCommand.get(i);
                    InnerObserver observer = commandObserverList.get(i);
                    if (observer != null) {
                        entry.trackingFlow.removeObserver(observer);
                    }
                }
                commandObserverList.clear();
            }
        }

        private static class InnerObserver implements Consumer<Object> {

            CommandFlow commandFlow;
            Object callbackObj;
            Method method;
            ArrayList<CommandInject> outParameterList;
            boolean dispatchProcessing;

            InnerObserver(CommandFlow commandFlow, Object callbackObj,
                          ArrayList<CommandInject> outParameterList) {
                this.commandFlow = commandFlow;
                this.callbackObj = callbackObj;
                this.method = commandFlow.key.method;
                this.outParameterList = outParameterList;
            }

            @Override
            public void accept(Object o) {
                if (dispatchProcessing) {
                    throw new IllegalStateException("Recursive invocation from:" + commandFlow);
                }
                Object result;
                dispatchProcessing = true;
                try {
                    final int parameterCount = outParameterList.size();
                    Object[] parameters = new Object[parameterCount];
                    for (int i = 0; i < parameterCount; i++) {
                        CommandInject inject = outParameterList.get(i);
                        if (inject != null) {
                            try {
                                parameters[i] = inject.getInputType().newInstance();
                            } catch (InstantiationException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            parameters[i] = o;
                        }
                    }

                    for (int i = 0; i < parameterCount; i++) {
                        CommandInject inject = outParameterList.get(i);
                        if (inject != null) {
                            inject.suppressSetAndExecute(parameters[i]);
                        }
                    }

                    result = method.invoke(callbackObj, parameters);

                    for (int i = 0; i < parameterCount; i++) {
                        CommandInject inject = outParameterList.get(i);
                        if (inject != null) {
                            inject.suppressGetAndExecute(parameters[i]);
                        }
                    }

                    if (commandFlow.returnCommand != null) {
                        commandFlow.returnCommand.invokeWithChain(result);
                    }
                } catch (InvocationTargetException invokeExp) {
                    if (invokeExp.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) invokeExp.getCause();
                    } else {
                        throw new RuntimeException("Callback invoke error", invokeExp.getCause());
                    }
                } catch (IllegalAccessException illegalAccessException) {
                    throw new RuntimeException("Impossible", illegalAccessException);
                } finally {
                    dispatchProcessing = false;
                }
            }
        }
    }

    private static class CommandUnTrack extends CommandImpl {
        UnTrackable trackCommand;

        @Override
        void onInit(Annotation annotation) {
            if (key.method != null && key.method.getReturnType() == void.class) {
                Class<?>[] classArray = key.method.getParameterTypes();
                if (classArray.length == 1) {
                    if (classArray[0] == trackCommand.getMethod().getParameterTypes()[0]) {
                        return;
                    }
                }
            }
            throw new CartrofitException("invalid unTrack:" + this);
        }

        void setTrackCommand(UnTrackable trackCommand) {
            this.trackCommand = trackCommand;
        }

        @Override
        public Object invoke(Object callback) {
            trackCommand.untrack(callback);
            return null;
        }

        @Override
        public CommandType getType() {
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
        public Object invoke(Object parameter) {
            flowWrapper.superHandleResult(this, parameter);
            return null;
        }

        @Override
        CommandImpl delegateTarget() {
            return commandFlow;
        }

        @Override
        public CommandType getType() {
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
        public Object invoke(Object parameter) {
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
        public CommandType getType() {
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
            super.onInit(annotation);
            Delegate delegate = (Delegate) annotation;
            resolveStickyType(delegate.sticky());
            carType = delegate.type();
            commandSet = getType() == CommandType.SET;

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
                        key.isCallbackEntry ? targetCommand.userDataClass : Flow.class,
                        key.getTrackClass(), store);
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
        public Object invoke(Object parameter) {
            if (commandSet) {
                return targetCommand.invokeWithChain(argConverter != null ?
                        argConverter.convert(parameter) : parameter);
            }
            return super.invoke(parameter);
        }

        @Override
        public Class<?> getInputType() {
            return commandSet ? key.getSetClass() : null;
        }

        @Override
        Object doInvoke() {
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
            if (result == null) {
                return null;
            }
            if (mapFlowSuppressed) {
                return result;
            }
            return resultConverter != null ? resultConverter.convert(result) : result;
        }

        @Override
        public CommandType getType() {
            return targetCommand.getType();
        }

        @Override
        CommandImpl delegateTarget() {
            return targetCommand.delegateTarget();
        }

        @Override
        String toCommandString() {
            return super.toCommandString() + " Delegate{" + targetCommand.toCommandString() + "}";
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

    private static class EmptyFlowWrapper extends FlowWrapper<Void> implements EmptyFlow {

        private final HashMap<Runnable, InnerObserver> mObserverMap = new HashMap<>();

        private EmptyFlowWrapper(Flow<Void> base, CommandReceive command) {
            super(base, command);
        }

        @Override
        public void addEmptyObserver(Runnable runnable) {
            if (runnable != null && !mObserverMap.containsKey(runnable)) {
                InnerObserver observer = new InnerObserver(runnable);
                mObserverMap.put(runnable, observer);
                addObserver(observer);
            }
        }

        @Override
        public void removeEmptyObserver(Runnable runnable) {
            InnerObserver observer = mObserverMap.remove(runnable);
            if (observer != null) {
                removeObserver(observer);
            }
        }

        private static class InnerObserver implements Consumer<Void> {
            private Runnable mAction;

            InnerObserver(Runnable action) {
                this.mAction = action;
            }

            @Override
            public void accept(Void aVoid) {
                if (mAction != null) {
                    mAction.run();
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
                receiverList.get(0).invokeWithChain(t);
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
                nextReceiver.invokeWithChain(t);
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
            return (T) headStickyGetCommand.invokeWithChain(null);
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
            if (ConverterStore.classEquals(fromClass, from) && ConverterStore.classEquals(toClass, to)) {
                return oneWayConverter;
            } else if (twoWayConverter != null && toClass.equals(from) && fromClass.equals(to)) {
                return this;
            }
            return null;
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
                throw new CartrofitException("invalid type:" + clazz);
            }
        }
    }

    public static class CartrofitException extends RuntimeException {
        public CartrofitException(String msg) {
            super(msg);
        }

        CartrofitException(String msg, Throwable cause) {
            super(msg, cause);
        }

        CartrofitException(Throwable cause) {
            super(cause);
        }
    }
}
