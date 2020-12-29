package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;
import android.os.Build;

import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.Category;
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
import com.liyangbin.cartrofit.annotation.Sticky;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.call.BuildInCallAdapter;
import com.liyangbin.cartrofit.call.Call;
import com.liyangbin.cartrofit.call.Interceptor;
import com.liyangbin.cartrofit.funtion.Consumer;
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

    private static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();
    private static final Router ID_ROUTER = new Router();

    private final ArrayList<Interceptor> mGlobalInterceptorList = new ArrayList<>();
    private final HashMap<Class<?>, ApiRecord<?>> mApiCache = new HashMap<>();
    private final HashMap<Class<?>, FlowConverter<?>> mFlowConverterMap = new HashMap<>();
    private final HashMap<Key, Call> mCallCache = new HashMap<>();
    private final ArrayList<CallAdapter> mCallAdapterList = new ArrayList<>();
    private final HashMap<String, ArrayList<Interceptor>> mInterceptorByCategory = new HashMap<>();
    private final CallInflater mInflater = new CallInflater() {
        @Override
        public Call inflateByIdIfThrow(Key key, int id, int category) {
            return getOrCreateCallById(key.record, id, category, true);
        }

        @Override
        public Call inflateById(Key key, int id, int category) {
            return getOrCreateCallById(key.record, id, category, false);
        }

        @Override
        public Call inflate(Key key, int category) {
            return getOrCreateCall(key.record, key, category);
        }

        @Override
        public void inflateCallback(Class<?> callbackClass, int category,
                                    Consumer<Call> resultReceiver) {
            ApiRecord<?> record = getApi(callbackClass);
            ArrayList<Key> childKeys = record.getChildKey();
            for (int i = 0; i < childKeys.size(); i++) {
                Key childKey = childKeys.get(i);
                Call call = getOrCreateCall(record, childKey, category);
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
        mGlobalInterceptorList.addAll(builder.interceptors);
        HashMap<String, ArrayList<Interceptor>> interceptorFromBuilder = builder.interceptorByCategory;
        if (interceptorFromBuilder.size() > 0) {
            for (Map.Entry<String, ArrayList<Interceptor>> chainEntry : interceptorFromBuilder.entrySet()) {
                ArrayList<Interceptor> newChain = chainEntry.getValue();
                ArrayList<Interceptor> oldChain = mInterceptorByCategory.put(chainEntry.getKey(), newChain);
                if (oldChain != null) {
                    newChain.addAll(0, oldChain);
                }
            }
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

    private static int getParameterCount(Method method) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return method.getParameterTypes().length;
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

        ArrayList<Key> childrenKey;

        HashMap<Integer, Method> selfDependency = new HashMap<>();
        HashMap<Method, Integer> selfDependencyReverse = new HashMap<>();

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

        Call createAdapterCall(Key key, int category) {
            for (int i = providerList.size() - 1; i >= 0; i--) {
                Union3<CallAdapter, Object, ConverterFactory> union3 = providerList.get(i);
                Call call = union3.value1.onCreateCall(union3.value2, key, category);
                if (call != null) {
                    call.init(key, union3.value3);

                    for (int j = 0; j < mGlobalInterceptorList.size(); j++) {
                        call.addInterceptor(mGlobalInterceptorList.get(j), false);
                    }
                    Category categoryAnnotation = key.getAnnotation(Category.class);
                    if (categoryAnnotation != null) {
                        for (String categoryByUser : categoryAnnotation.value()) {
                            ArrayList<Interceptor> interceptorList = mInterceptorByCategory
                                    .get(categoryByUser);
                            if (interceptorList != null) {
                                for (int j = 0; j < interceptorList.size(); j++) {
                                    call.addInterceptor(interceptorList.get(j), false);
                                }
                            }
                        }
                    }

                    boolean hasSet = call.hasCategory(CallAdapter.CATEGORY_SET);
                    boolean hasTrack = call.hasCategory(CallAdapter.CATEGORY_TRACK);
                    if (hasSet ^ hasTrack) {
                        Restore restore = key.getAnnotation(Restore.class);
                        if (restore != null) {
                            call.setRestoreTarget(getOrCreateCall(key.record, key,
                                    hasSet ? CallAdapter.CATEGORY_TRACK : CallAdapter.CATEGORY_SET));
                        }
                    }

                    if (hasTrack && key.isAnnotationPresent(Sticky.class)) {
                        call.enableStickyTrack();
                    }

                    return call;
                }
            }
            return null;
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
        private final HashMap<String, ArrayList<Interceptor>> interceptorByCategory = new HashMap<>();
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

        public Builder addInterceptor(String category, Interceptor interceptor) {
            Objects.requireNonNull(interceptor);
            ArrayList<Interceptor> interceptorList = interceptorByCategory.get(category);
            if (interceptorList == null) {
                interceptorList = new ArrayList<>();
                interceptorByCategory.put(category, interceptorList);
            }
            interceptorList.add(interceptor);
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
            interceptorByCategory.clear();
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
        Call inflateByIdIfThrow(Key key, int id, int category);
        Call inflateById(Key key, int id, int category);
        Call inflate(Key key, int category);
        void inflateCallback(Class<?> callbackClass, int flag, Consumer<Call> resultReceiver);
    }

    private Call getOrCreateCall(ApiRecord<?> record, Key key) {
        Call command = getOrCreateCall(record, key, CallAdapter.CATEGORY_DEFAULT);
        if (command == null) {
            throw new CartrofitGrammarException("Can not parse command from:" + key);
        }
        return command;
    }

    private Call getOrCreateCallById(ApiRecord<?> record, int id, int flag,
                                               boolean throwIfNotFound) {
        Method method = record.selfDependency.get(id);
        if (method == null) {
            Class<?> apiClass = ID_ROUTER.findApiClassById(id);
            if (apiClass == record.clazz || apiClass == null) {
                throw new CartrofitGrammarException("Can not find target Id:" + id + " from:" + apiClass);
            }
            return getOrCreateCallById(getApi(apiClass, true), id, flag, throwIfNotFound);
        }
        Call command = getOrCreateCall(record, new Key(record, method, false), flag);
        if (throwIfNotFound && command == null) {
            throw new CartrofitGrammarException("Can not resolve target Id:" + id
                    + " in specific type from:" + this);
        }
        return command;
    }

    private Call getOrCreateCall(ApiRecord<?> record, Key key, int flag) {
        Call call;
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
        private int mId;

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

        public int getId() {
            if (mId != 0) {
                return mId;
            }
            if (method == null) {
                return 0;
            }
            return record.selfDependencyReverse.getOrDefault(method, 0);
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

        public Class<?> getSetClass() {
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

//    private static class ConverterWrapper<CarData, AppData> implements Converter<AppData, CarData> {
//        Class<?> fromClass;
//        Class<?> toClass;
//
//        TwoWayConverter<CarData, AppData> twoWayConverter;
//        Converter<Object, ?> oneWayConverter;
//
//        ConverterWrapper(Class<?> from, Class<?> to, Converter<?, ?> converter) {
//            fromClass = from;
//            toClass = to;
//            if (converter instanceof TwoWayConverter) {
//                twoWayConverter = (TwoWayConverter<CarData, AppData>) converter;
//                oneWayConverter = (Converter<Object, ?>) twoWayConverter;
//            } else {
//                oneWayConverter = (Converter<Object, ?>) converter;
//            }
//            if (oneWayConverter == null) {
//                throw new NullPointerException("converter can not be null");
//            }
//        }
//
//        Converter<?, ?> asConverter(Class<?> from, Class<?> to) {
//            if (classEquals(fromClass, from) && classEquals(toClass, to)) {
//                return oneWayConverter;
//            } else if (twoWayConverter != null && toClass.equals(from) && fromClass.equals(to)) {
//                return this;
//            }
//            return null;
//        }
//
//        @Override
//        public CarData convert(AppData value) {
//            return Objects.requireNonNull(twoWayConverter).fromApp2Car(value);
//        }
//    }
}
