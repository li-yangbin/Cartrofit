package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.Combine;
import com.liyangbin.cartrofit.annotation.ConsiderSuper;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.GenerateId;
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
import java.lang.reflect.Constructor;
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
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public final class Cartrofit {

    private static final ConverterStore GLOBAL_CONVERTER = new ConverterStore();
    private static Cartrofit sDefault;

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
    private static final HashMap<Class<? extends ApiCallback>, ApiCallback> ON_CREATE_OBJ_CACHE = new HashMap<>();
    private static final Router ID_ROUTER = new Router();

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
                "Call setDefault() before calling from()").fromInternal(api);
    }

    public static final class DummyOnCreate implements ApiCallback {
        @Override
        public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
            throw new IllegalStateException("impossible call");
        }
    }

    private static Scope findScopeByClass(Class<?> clazz) {
        Scope scope = clazz.getDeclaredAnnotation(Scope.class);
        if (scope != null) {
            return scope;
        }
        Class<?> enclosingClass = clazz.getEnclosingClass();
        while (enclosingClass != null) {
            scope = enclosingClass.getDeclaredAnnotation(Scope.class);
            if (scope != null) {
                return scope;
            }
            enclosingClass = enclosingClass.getEnclosingClass();
        }
        return null;
    }

    private static class Router {
        private static final String ROUTER_BASE_NAME = "com.liyangbin.cartrofit.IdRouter";
        private static final String METHOD_NAME = "findApiClassById";

        ArrayList<Method> findMethodList;

        private Class<?> findApiClassById(int id) {
            try {
                collectChildRouter();
                for (int i = 0; i < findMethodList.size(); i++) {
                    Class<?> clazz = (Class<?>) findMethodList.get(i).invoke(null, id);
                    if (clazz != null) {
                        return clazz;
                    }
                }
                return null;
            } catch (ReflectiveOperationException impossible) {
                throw new RuntimeException(impossible);
            }
        }

        private void collectChildRouter() throws NoSuchMethodException {
            if (findMethodList == null) {
                findMethodList = new ArrayList<>();
                int index = 1;
                while (true) {
                    try {
                        Class<?> idRouterClass = Class.forName(ROUTER_BASE_NAME + index);
                        Method method = idRouterClass.getMethod(METHOD_NAME, int.class);
                        findMethodList.add(method);
                        index++;
                    } catch (ClassNotFoundException ignore) {
                        break;
                    }
                }
            }
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

    class ApiRecord<T> extends ApiBuilder {
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

            if (clazz.isAnnotationPresent(GenerateId.class)) {
                try {
                    Class<?> selfScopeClass = Class.forName(clazz.getName() + ID_SUFFIX);
                    importDependency(selfScopeClass);
                } catch (ClassNotFoundException impossible) {
                    throw new IllegalStateException("impossible", impossible);
                }
            }

            Scope carScope = findScopeByClass(clazz);
            if (carScope != null) {
                this.dataScope = carScope.value();

                this.source = Objects.requireNonNull(mDataMap.get(dataScope),
                        "Invalid scope:" + dataScope +
                                ". Make sure use a valid scope id registered in Builder().addDataSource()");

                Class<? extends ApiCallback> createHelper = carScope.onCreate();
                if (!createHelper.equals(DummyOnCreate.class)) {
                    ApiCallback singleton = ON_CREATE_OBJ_CACHE.get(createHelper);
                    if (singleton == null) {
                        try {
                            Constructor<? extends ApiCallback> constructor
                                    = createHelper.getDeclaredConstructor();
                            constructor.setAccessible(true);
                            singleton = constructor.newInstance();
                            ON_CREATE_OBJ_CACHE.put(createHelper, singleton);
                        } catch (ReflectiveOperationException illegal) {
                            throw new IllegalArgumentException(illegal);
                        }
                    }
                    singleton.onApiCreate(clazz, this);
                }

                for (int i = 0; i < mApiCallback.size(); i++) {
                    mApiCallback.get(i).onApiCreate(clazz, this);
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
                throw new CartrofitGrammarException("Call intercept(Interceptor) only once before apply");
            }
            tempInterceptor = interceptor;
            return this;
        }

        @Override
        ApiBuilder convert(AbsConverterBuilder builder) {
            if (converterBuilder != null) {
                throw new CartrofitGrammarException("Call convert(Converter) only once before apply");
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
            void add(Constraint constraint, Interceptor element) {
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
            if (command.delegateTarget() == command && mChainHead != null) {
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
                throw new CartrofitGrammarException("invalid input converter:" + converter.getClass());
            }
            if (implementsBy.isSynthetic()) {
                throw new CartrofitGrammarException("Do not use lambda expression in addConverter()");
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
                throw new CartrofitGrammarException("invalid converter class:" + implementsBy
                        + " type:" + Arrays.toString(ifTypes));
            }
        }

        void addConverter(Class<?> convertFrom, Class<?> convertTo, Converter<?, ?> converter) {
            if (checkConverter(convertFrom, convertTo)) {
                throw new CartrofitGrammarException("Can not add duplicate converter from:" +
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

        Converter<?, ?> find(CommandImpl command, Class<?> from, Class<?> to) {
            Converter<?, ?> converter = findWithoutCommand(from, to);
            if (converter != null || classEquals(from, to)) {
                return converter;
            }
            throw new CartrofitGrammarException("Can not resolve converter from:"
                    + toClassString(from) + " to:" + toClassString(to)
                    + " by:" + command);
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
            Scope sourceScope = findScopeByClass(source.getClass());
            if (sourceScope == null) {
                throw new CartrofitGrammarException("Declare data scope in :" + source.getClass());
            }
            DataSource existedSource = dataMap.put(sourceScope.value(), source);
            if (existedSource != null) {
                throw new CartrofitGrammarException("Duplicate data source:" + existedSource);
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
                    throw new CartrofitGrammarException("Do declare CarApi annotation in class:" + api);
                }
            }
            return record;
        }
    }

    private CommandImpl getOrCreateCommand(ApiRecord<?> record, Key key) {
        CommandImpl command = getOrCreateCommand(record, key, FLAG_PARSE_ALL);
        if (command == null) {
            throw new CartrofitGrammarException("Can not parse command from:" + key);
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
            Class<?> apiClass = ID_ROUTER.findApiClassById(id);
            if (apiClass == record.clazz || apiClass == null) {
                throw new CartrofitGrammarException("Can not find target Id:" + id + " from:" + apiClass);
            }
            return getOrCreateCommandById(getApi(apiClass, true), id, flag, throwIfNotFound);
        }
        CommandImpl command = getOrCreateCommand(getApi(method.getDeclaringClass()),
                new Key(method, false), flag);
        if (throwIfNotFound && command == null) {
            throw new CartrofitGrammarException("Can not resolve target Id:" + id
                    + " in specific type from:" + this);
        }
        return command;
    }

    private CommandImpl getOrCreateCommand(ApiRecord<?> record, Key key, int flag) {
        if (record == null) {
            return null;
        }
        CommandImpl command;
        synchronized (mApiCache) {
            command = mCommandCache.get(key);
            if (command != null) {
                return command;
            }
            key.doQualifyCheck();
            command = createCommand(record, key, flag);
            if (command != null) {
                mCommandCache.put(key, command);
            }
        }
        return command;
    }

    static class Key {
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
                        throw new CartrofitGrammarException("More than one annotation presented by:" + this);
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
                    throw new CartrofitGrammarException("Invalid declaration:" + method);
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
                        throw new CartrofitGrammarException("Invalid parameter count " + this);
                    }
                }
            } else if (isAnnotationPresent(Inject.class)) {
                if (method != null) {
                    if (method.getReturnType() != void.class) {
                        throw new CartrofitGrammarException("Can not return any result by using Inject");
                    }
                    int parameterCount = method.getParameterCount();
                    if (parameterCount != 1) {
                        throw new CartrofitGrammarException("Can not declare parameter more or less than one " + this);
                    }
                    Annotation[] annotationOne = method.getParameterAnnotations()[0];
                    int countWithInOut = 0;
                    for (Annotation annotation : annotationOne) {
                        if (annotation instanceof Out || annotation instanceof In) {
                            countWithInOut++;
                        }
                    }
                    if (countWithInOut == 0) {
                        throw new CartrofitGrammarException("Invalid parameter declaration " + this);
                    }
                }
            } else if (field != null && Modifier.isFinal(field.getModifiers())
                    && isAnnotationPresent(Set.class)) {
                throw new CartrofitGrammarException("Invalid key:" + this + " in command Inject");
            }
            if (isInvalid()) {
                throw new CartrofitGrammarException("Invalid key:" + this);
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
                    throw new CartrofitGrammarException("Invalid parameter:"
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
                    throw new CartrofitGrammarException("Invalid parameter:"
                            + Arrays.toString(classArray));
                }
                if (method.getReturnType() != void.class) {
                    throw new CartrofitGrammarException("Invalid return type:"
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
                    throw new CartrofitGrammarException("Invalid return void type");
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
                    throw new CartrofitGrammarException("Invalid return void type");
                }
                return returnType;
            } else {
                Type[] parameterArray = method.getGenericParameterTypes();
                if (parameterArray.length != 1) {
                    throw new CartrofitGrammarException("Invalid parameter:"
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
                throw new CartrofitGrammarException("Can not parse type:" + originalType + " from:" + this);
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
                    throw new CartrofitGrammarException("Can not extract target type:"
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
                throw new CartrofitGrammarException("Must declare sticky On if you specify restore command" + key);
            }
            return command;
        }

        UnTrack unTrack = (flag & FLAG_PARSE_UN_TRACK) != 0 ? key.getAnnotation(UnTrack.class) : null;
        if (unTrack != null) {
            CommandUnTrack command = new CommandUnTrack();
            final int targetTrack = unTrack.value();
            command.setTrackCommand((UnTrackable) getOrCreateCommandById(record, targetTrack,
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
                throw new CartrofitGrammarException("Must declare more than one element on Combine:"
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

        Register register = (flag & FLAG_PARSE_REGISTER) != 0 ? key.getAnnotation(Register.class) : null;
        if (register != null) {
            Class<?> targetClass = key.getSetClass();
            if (!targetClass.isInterface()) {
                throw new CartrofitGrammarException("Declare CarCallback parameter as an interface:" + targetClass);
            }
            CommandRegister commandRegister = new CommandRegister();
            searchAndCreateChildCommand(getApi(targetClass),
                    command -> {
                        if (command.getType() != CommandType.TRACK) {
                            throw new CartrofitGrammarException("Illegal non-track on register callback:" + command);
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
                throw new CartrofitGrammarException("Failed to resolve callback entry point in " + targetClass);
            }
            commandRegister.init(record, register, key);
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
            throw new CartrofitGrammarException("Can not use Inject on class type:" + targetClass);
        }

        CommandReflect commandReflect = new CommandReflect();
        CommandInject command = new CommandInject(targetClass, parameterIndex, commandReflect);
        ApiRecord<?> reflectRecord = getApi(targetClass);
        searchAndCreateChildCommand(reflectRecord, commandReflect::addChildCommand,
                FLAG_PARSE_INJECT_CHILDREN);
        if (commandReflect.childrenCommand.size() == 0) {
            throw new CartrofitGrammarException("Failed to parse Inject command from type:"
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
        throw new CartrofitGrammarException("Expected a Class or ParameterizedType," +
                " but <" + type + "> is of type " + className);
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
}
