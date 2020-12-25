package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;
import android.os.Build;

import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.Combine;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Inject;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Restore;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.funtion.Consumer;
import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.FunctionalConverter;
import com.liyangbin.cartrofit.funtion.TwoWayConverter;
import com.liyangbin.cartrofit.funtion.Union;
import com.liyangbin.cartrofit.funtion.Union3;

import java.lang.annotation.Annotation;
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

@SuppressWarnings("unchecked")
public final class Cartrofit {

    private static final ArrayList<FlowConverter<?>> GLOBAL_CONVERTER = new ArrayList<>();
    private static Cartrofit sDefault;

    private static final int FLAG_FIRST_BIT = 1;

    private static final int FLAG_PARSE_SET = FLAG_FIRST_BIT;
    private static final int FLAG_PARSE_GET = FLAG_FIRST_BIT << 1;
    private static final int FLAG_PARSE_TRACK = FLAG_FIRST_BIT << 2;
    private static final int FLAG_PARSE_UN_REGISTER = FLAG_FIRST_BIT << 3;
    private static final int FLAG_PARSE_INJECT = FLAG_FIRST_BIT << 4;
    private static final int FLAG_PARSE_COMBINE = FLAG_FIRST_BIT << 5;
    private static final int FLAG_PARSE_REGISTER = FLAG_FIRST_BIT << 6;
    private static final int FLAG_PARSE_INJECT_CHILDREN = FLAG_PARSE_SET | FLAG_PARSE_GET
            | FLAG_PARSE_TRACK | FLAG_PARSE_INJECT | FLAG_PARSE_COMBINE;
    private static final int FLAG_PARSE_ALL = FLAG_PARSE_SET | FLAG_PARSE_GET | FLAG_PARSE_TRACK
            | FLAG_PARSE_UN_REGISTER | FLAG_PARSE_INJECT | FLAG_PARSE_COMBINE | FLAG_PARSE_REGISTER;

    private static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();
    private static final Router ID_ROUTER = new Router();

    private InterceptorChain mChainHead;

    private final HashMap<Class<?>, ApiRecord<?>> mApiCache = new HashMap<>();
    private final HashMap<Class<?>, FlowConverter<?>> mFlowConverterMap = new HashMap<>();
    private final HashMap<Key, CallAdapter.Call> mCallCache = new HashMap<>();
    private final ArrayList<CallAdapter> mCallAdapterList = new ArrayList<>();
    private final CallInflater mInflater = new CallInflater() {
        @Override
        public CallAdapter.Call inflateByIdIfThrow(Key key, int id, int category) {
            return getOrCreateCallById(key.record, id, category, true);
        }

        @Override
        public CallAdapter.Call inflateById(Key key, int id, int category) {
            return getOrCreateCallById(key.record, id, category, false);
        }

        @Override
        public CallAdapter.Call inflate(Key key, int category) {
            return getOrCreateCall(key.record, key, category);
        }

        @Override
        public void inflateCallback(Class<?> callbackClass, int category,
                                    Consumer<CallAdapter.Call> resultReceiver) {
            ApiRecord<?> record = getApi(callbackClass);
            ArrayList<Key> childKeys = record.getChildKey();
            for (int i = 0; i < childKeys.size(); i++) {
                Key childKey = childKeys.get(i);
                CallAdapter.Call call = getOrCreateCall(record, childKey, category);
                if (call != null) {
                    resultReceiver.accept(call);
                }
            }
        }
    };

    static {
        RxJavaConverter.addSupport();
        ObservableConverter.addSupport();
        LiveDataConverter.addSupport();
    }

    private Cartrofit() {
        for (int i = 0; i < GLOBAL_CONVERTER.size(); i++) {
            FlowConverter<?> flowConverter = GLOBAL_CONVERTER.get(i);
            Class<?> targetClass = findFlowConverterTarget(flowConverter);
            mFlowConverterMap.put(targetClass, flowConverter);
        }
        mCallAdapterList.add(new BuildInCallAdapter());
    }

    private void append(Builder builder) {
        for (int i = 0; i < builder.dataAdapterList.size(); i++) {
            CallAdapter adapter = builder.dataAdapterList.get(i);
            adapter.setCallInflater(mInflater);
            mCallAdapterList.add(adapter);
        }
        for (int i = 0; i < builder.flowConverters.size(); i++) {
            FlowConverter<?> flowConverter = builder.flowConverters.get(i);
            Class<?> targetClass = findFlowConverterTarget(flowConverter);
            mFlowConverterMap.put(targetClass, flowConverter);
        }
        for (int i = 0; i < builder.interceptors.size(); i++) {
            if (mChainHead == null) {
                mChainHead = new InterceptorChain();
            }
            mChainHead.addInterceptor(builder.interceptors.get(i));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static <T> T from(Class<T> api) {
        return Objects.requireNonNull(sDefault,
                "Call setDefault() before calling from()").fromInternal(api);
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

    static int getParameterCount(Method method) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Class<?>[] classArray = method.getParameterTypes();
            return classArray != null ? classArray.length : 0;
        } else {
            return method.getParameterCount();
        }
    }

    class ApiRecord<T> {
        private static final String ID_SUFFIX = "Id";

        Class<T> clazz;
        String dataScope;
        int apiArea;
        T apiObj;
        StickyType stickyType = StickyType.NO_SET;
        DataSource source;

        ArrayList<Key> childrenKey;

//        ArrayList<ApiRecord<?>> parentApi;

        HashMap<Integer, Method> selfDependency = new HashMap<>();
        HashMap<Method, Integer> selfDependencyReverse = new HashMap<>();
//        ConverterManager converterManager = new ConverterManager();
        InterceptorManager interceptorManager = new InterceptorManager();

        Interceptor tempInterceptor;
        ConverterFactory scopeFactory;
//        ConvertSolution converterBuilder;

//        CallAdapter adapter;
//        Object scope;
        ArrayList<Union3<CallAdapter, Object, ConverterFactory>> providerList;

        ApiRecord(ArrayList<Union3<CallAdapter, Object, ConverterFactory>> providerList, Class<T> clazz) {
            this.clazz = clazz;
            this.providerList = providerList;

            if (clazz.isAnnotationPresent(GenerateId.class)) {
                try {
                    Class<?> selfScopeClass = Class.forName(clazz.getName() + ID_SUFFIX);
                    importDependency(selfScopeClass);
                } catch (ClassNotFoundException impossible) {
                    throw new IllegalStateException("impossible", impossible);
                }
            }
        }

        CallAdapter.Call createAdapterCall(Key key, int category) {
            for (int i = providerList.size() - 1; i >= 0; i--) {
                Union3<CallAdapter, Object, ConverterFactory> union3 = providerList.get(i);
                CallAdapter.Call call = union3.value1.onCreateCall(union3.value2, key, category);
                if (call != null) {
                    call.init(key, union3.value3);

                    boolean hasSet = call.hasCategory(CallAdapter.CATEGORY_SET);
                    boolean hasTrack = call.hasCategory(CallAdapter.CATEGORY_TRACK);
                    if (hasSet ^ hasTrack) {
                        Restore restore = key.getAnnotation(Restore.class);
                        if (restore != null) {
                            call.setRestoreTarget(getOrCreateCall(key.record, key,
                                    hasSet ? CallAdapter.CATEGORY_TRACK : CallAdapter.CATEGORY_SET));
                        }
                    }

                    return call;
                }
            }
            return null;
        }

        int loadId(CallAdapter.Call command) {
            if (command.key.method == null) {
                return 0;
            }
            return selfDependencyReverse.getOrDefault(command.key.method, 0);
        }

//        @Override
//        public void setDefaultAreaId(int areaId) {
//            apiArea = areaId;
//        }
//
//        @Override
//        public void setDefaultStickyType(StickyType stickyType) {
//            this.stickyType = stickyType;
//        }
//
//        @Override
//        public ConverterBuilder intercept(Interceptor interceptor) {
//            if (tempInterceptor != null) {
//                throw new CartrofitGrammarException("Call intercept(Interceptor) only once before apply");
//            }
//            tempInterceptor = interceptor;
//            return this;
//        }
//
//        @Override
//        ConverterBuilder convert(ConvertSolution builder) {
//            if (converterBuilder != null) {
//                throw new CartrofitGrammarException("Call convert(Converter) only once before apply");
//            }
//            converterBuilder = builder;
//            return this;
//        }

//        @Override
//        public void apply(Constraint... constraints) {
//            if (constraints == null || constraints.length == 0) {
//                return;
//            }
//            if (tempInterceptor != null) {
//                for (Constraint constraint : constraints) {
//                    interceptorManager.add(constraint, tempInterceptor);
//                }
//                tempInterceptor = null;
//            }
//            if (converterBuilder != null) {
//                for (Constraint constraint : constraints) {
//                    converterManager.add(constraint, converterBuilder);
//                }
//                converterBuilder = null;
//            }
//        }

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
            abstract GROUP get(CallAdapter.Call command);
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
            InterceptorChain get(CallAdapter.Call command) {
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

//        private final class ConverterManager extends AbstractManager<ConvertSolution, ConverterStore> {
//            HashMap<Constraint, ConverterStore> constraintMapper = new HashMap<>();
//
//            @Override
//            void add(Constraint constraint, ConvertSolution builder) {
//                ConverterStore store = constraintMapper.get(constraint);
//                if (store == null) {
//                    store = new ConverterStore();
//                    constraintMapper.put(constraint, store);
//                }
//                builder.apply(store);
//                addConstraint(constraint);
//            }
//
//            @Override
//            ConverterStore get(CallAdapter.Call command) {
//                ConverterStore current = null;
//                for (int i = constraintList.size() - 1; i >= 0; i--) {
//                    Constraint constraint = constraintList.get(i);
//                    if (constraint.check(command)) {
//                        ConverterStore node = constraintMapper.get(constraint);
//                        if (node != null) {
//                            node = node.copy();
//                            if (current != null) {
//                                current.addParentToEnd(node);
//                            } else {
//                                current = node;
//                            }
//                        }
//                    }
//                }
//                return current;
//            }
//        }

        InterceptorChain getInterceptorByKey(CallAdapter.Call command) {
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

//        ConverterStore getConverterByKey(CallAdapter.Call command) {
//            ConverterStore store = converterManager.get(command);
//            if (store != null) {
//                store.addParentToEnd(GLOBAL_CONVERTER);
//                return store;
//            } else {
//                return GLOBAL_CONVERTER;
//            }
//        }

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
                    Key childKey = new Key(this, method, true);
                    if (!childKey.isInvalid()) {
                        result.add(childKey);
                    }
                }
            } else {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    Key childKey = new Key(this, field);
                    if (!childKey.isInvalid()) {
                        result.add(childKey);
                    }
                }
            }
            childrenKey = result;
            return result;
        }

//        ArrayList<ApiRecord<?>> getParentApi() {
//            if (parentApi != null) {
//                return parentApi;
//            }
//            ArrayList<ApiRecord<?>> result = new ArrayList<>();
//            if (!clazz.isInterface()) {
//                if (clazz.isAnnotationPresent(ConsiderSuper.class)) {
//                    Class<?> superClass = clazz.getSuperclass();
//                    if (superClass != null && superClass != Object.class) {
//                        ApiRecord<?> record = getApi(clazz);
//                        result.add(record);
//                    }
//                }
//            }
//            parentApi = result;
//            return result;
//        }

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

    FlowConverter<?> findFlowConverter(Class<?> target) {
        return mFlowConverterMap.get(target);
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
                    return getClassFromType(converterDeclaredType[0]);
                }
            }
        }

        throw new CartrofitGrammarException("invalid converter class:" + implementsBy
                + " type:" + Arrays.toString(ifTypes));
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
                if (converter instanceof FunctionalConverter) {
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
                        } else if (FunctionalConverter.class.isAssignableFrom(lookingFor)) {
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

        Converter<?, ?> find(CallAdapter.Call command, Class<?> from, Class<?> to) {
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

    static boolean classEquals(Class<?> a, Class<?> b) {
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

    public static final class Builder {

        private final ArrayList<CallAdapter> dataAdapterList = new ArrayList<>();
        private final ArrayList<Interceptor> interceptors = new ArrayList<>();
        private final ArrayList<FlowConverter<?>> flowConverters = new ArrayList<>();

        private Builder() {
        }

        public Builder addCallAdapter(CallAdapter callAdapter) {
            dataAdapterList.add(callAdapter);
            return this;
        }

        public Builder addInterceptor(Interceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        public <T> Builder addFlowConverter(FlowConverter<T> converter) {
            flowConverters.add(converter);
            return this;
        }

        public void buildAsDefault() {
            if (sDefault == null) {
                sDefault = new Cartrofit();
            }
            sDefault.append(this);
            interceptors.clear();
            flowConverters.clear();
        }
    }

    static void addGlobalConverter(FlowConverter<?>... converters) {
        GLOBAL_CONVERTER.addAll(Arrays.asList(converters));
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
                        final Union<?> parameter = Union.of(args);
                        try {
                            return getOrCreateCall(record, new Key(record, method, false))
                                    .invoke(parameter);
                        } finally {
                            parameter.recycle();
                        }
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

                ArrayList<Union3<CallAdapter, Object, ConverterFactory>> providerList = new ArrayList<>();
                ConverterFactory scopeFactory = new ConverterFactory(this);
                for (int i = 0; i < mCallAdapterList.size(); i++) {
                    CallAdapter adapter = mCallAdapterList.get(i);
                    Object scope = adapter.extractScope(api, scopeFactory);
                    if (scope != null) {
                        providerList.add(Union.of(adapter, scope, scopeFactory));
                        scopeFactory = new ConverterFactory(this);
                    }
                }
                if (providerList.size() > 0) {
                    record = new ApiRecord<>(providerList, api);
                    mApiCache.put(api, record);
                }
                if (record == null && throwIfNotDeclareScope) {
                    throw new CartrofitGrammarException("Do declare CarApi annotation in class:" + api);
                }
            }
            return record;
        }
    }

    public interface CallInflater {
        CallAdapter.Call inflateByIdIfThrow(Key key, int id, int category);
        CallAdapter.Call inflateById(Key key, int id, int category);
        CallAdapter.Call inflate(Key key, int category);
        void inflateCallback(Class<?> callbackClass, int flag, Consumer<CallAdapter.Call> resultReceiver);
    }

    private CallAdapter.Call getOrCreateCall(ApiRecord<?> record, Key key) {
        CallAdapter.Call command = getOrCreateCall(record, key, FLAG_PARSE_ALL);
        if (command == null) {
            throw new CartrofitGrammarException("Can not parse command from:" + key);
        }
        return command;
    }

//    CallAdapter.Call getOrCreateCallById(ApiRecord<?> record, int id, int flag) {
//        return getOrCreateCallById(record, id, flag, true);
//    }

    CallAdapter.Call getOrCreateCallByIdIfThrow(Key key, int id, int flag) {
        return getOrCreateCallById(key.record, id, flag, true);
    }

    CallAdapter.Call getOrCreateCallById(Key key, int id, int flag) {
        return getOrCreateCallById(key.record, id, flag, false);
    }

    private CallAdapter.Call getOrCreateCallById(ApiRecord<?> record, int id, int flag,
                                               boolean throwIfNotFound) {
        Method method = record.selfDependency.get(id);
        if (method == null) {
            Class<?> apiClass = ID_ROUTER.findApiClassById(id);
            if (apiClass == record.clazz || apiClass == null) {
                throw new CartrofitGrammarException("Can not find target Id:" + id + " from:" + apiClass);
            }
            return getOrCreateCallById(getApi(apiClass, true), id, flag, throwIfNotFound);
        }
        CallAdapter.Call command = getOrCreateCall(record, new Key(record, method, false), flag);
        if (throwIfNotFound && command == null) {
            throw new CartrofitGrammarException("Can not resolve target Id:" + id
                    + " in specific type from:" + this);
        }
        return command;
    }

    private CallAdapter.Call getOrCreateCall(ApiRecord<?> record, Key key, int flag) {
        CallAdapter.Call call;
        synchronized (mApiCache) {
            call = mCallCache.get(key);
            if (call != null) {
                return call;
            }
            key.doQualifyCheck();

            call = record.createAdapterCall(key, flag);
            if (call != null) {
                mCallCache.put(key, call);
            }
        }
        return call;
    }

    public static class Key {
        private static final Class<? extends Annotation>[] QUALIFY_CHECK =
                new Class[]{Get.class, Set.class, Delegate.class,
                        Combine.class, Inject.class, Register.class};

        private static final Class<? extends Annotation>[] QUALIFY_CALLBACK_CHECK =
                new Class[]{Delegate.class, Track.class, Combine.class};

        private static final Class<? extends Annotation>[] QUALIFY_INJECT_CHECK =
                new Class[]{Get.class, Set.class, Delegate.class, Combine.class, Inject.class};

        public final Method method;
        public final boolean isCallbackEntry;
        int trackReceiveArgIndex = -1;

        public final Field field;

        ApiRecord<?> record;

        Key(ApiRecord<?> record, Method method, boolean isCallbackEntry) {
            this.record = record;
            this.method = method;
            this.field = null;
            this.isCallbackEntry = isCallbackEntry;
        }

        Key(ApiRecord<?> record, Field field) {
            this.record = record;
            this.method = null;
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
                int parameterCount = getParameterCount(method);
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
                    int parameterCount = getParameterCount(method);
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
            }  else if (isAnnotationPresent(Track.class)) {
                if (method != null && getParameterCount(method) > 0) {
                    throw new CartrofitGrammarException("Invalid track declaration " + this);
                }
            } else if (field != null && Modifier.isFinal(field.getModifiers())
                    && isAnnotationPresent(Set.class)) {
                throw new CartrofitGrammarException("Invalid key:" + this + " in command Inject");
            }
            if (isInvalid()) {
                throw new CartrofitGrammarException("Invalid key:" + this);
            }
        }

        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return method != null ? method.getDeclaredAnnotation(annotationClass)
                    : field.getDeclaredAnnotation(annotationClass);
        }

        public <T extends Annotation> boolean isAnnotationPresent(Class<T> annotationClass) {
            return method != null ? method.isAnnotationPresent(annotationClass)
                    : field.isAnnotationPresent(annotationClass);
        }

        public Class<?>[] getParameterType() {
            return method != null ? method.getParameterTypes() : new Class<?>[] {field.getType()};
        }

        public Class<?> getReturnType() {
            return method != null ? method.getReturnType() : field.getType();
        }

        public boolean isAnnotationPresent(int parameterIndex, Class<? extends Annotation> annotationClass) {
            return getAnnotation(parameterIndex, annotationClass) != null;
        }

        public Annotation getAnnotation(int parameterIndex, Class<? extends Annotation> annotationClass) {
            if (method != null) {
                Annotation[][] annotations = method.getParameterAnnotations();
                if (parameterIndex >= 0 && parameterIndex < annotations.length) {
                    for (Annotation annotation : annotations[parameterIndex]) {
                        if (annotationClass.isInstance(annotation)) {
                            return annotation;
                        }
                    }
                }
            } else if (parameterIndex == 0) {
                return field.getDeclaredAnnotation(annotationClass);
            }
            return null;
        }

        private Class<? extends Annotation>[] getInputAnnotationType() {
//            if (method != null) {
//                Annotation[][] annotationMatrix = method.getParameterAnnotations();
//            }
//            return method != null ? method.getParameterTypes() : new Class<?>[] {field.getType()};
            return null;
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
            if (classEquals(fromClass, from) && classEquals(toClass, to)) {
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
