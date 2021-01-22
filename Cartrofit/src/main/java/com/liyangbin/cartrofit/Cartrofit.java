package com.liyangbin.cartrofit;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.liyangbin.cartrofit.annotation.Bind;
import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Token;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.call.BuildInCallAdapter;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.funtion.Union;

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

    private static final HashMap<Class<?>, FlowConverter<?>> FLOW_CONVERTER_MAP = new HashMap<>();
    private static Cartrofit sDefault;

    private static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();
    private static final Router ID_ROUTER = new Router();

    private final ArrayList<Interceptor> mGlobalInterceptorList = new ArrayList<>();
    private final HashMap<Class<?>, ApiRecord<?>> mApiCache = new HashMap<>();
    private final HashMap<Key, Call> mCallCache = new HashMap<>();
    private final ArrayList<CallAdapter> mCallAdapterList = new ArrayList<>();
    private final HashMap<String, ArrayList<Interceptor>> mInterceptorByCategory = new HashMap<>();
    private final CallAdapter mBuildInCallAdapter = new BuildInCallAdapter();

    static {
        RxJavaConverter.addSupport();
        ObservableConverter.addSupport();
        LiveDataConverter.addSupport();
    }

    private Cartrofit() {
        mBuildInCallAdapter.init(this);
    }

    private void append(Builder builder) {
        for (int i = 0; i < builder.dataAdapterList.size(); i++) {
            CallAdapter adapter = builder.dataAdapterList.get(i);
            adapter.init(this);
            mCallAdapterList.add(adapter);
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
        HashMap<Integer, ArrayList<CallAdapter.CallSolution<?>>> grammarRuleMap = new HashMap<>();
        HashMap<Method, Integer> selfDependencyReverse = new HashMap<>();

        CallAdapter callAdapter;
        Object scopeObj;

        ApiRecord(CallAdapter adapter, Object scopeObj, Class<T> clazz) {
            this.callAdapter = adapter;
            this.scopeObj = scopeObj;
            this.clazz = clazz;

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
            ArrayList<CallAdapter.CallSolution<?>> grammarRule = grammarRuleMap.get(category);
            if (grammarRule == null) {
                grammarRule = new ArrayList<>();
                callAdapter.collectGrammarRules(category, grammarRule);
                mBuildInCallAdapter.collectGrammarRules(category, grammarRule);
                grammarRuleMap.put(category, grammarRule);
            }

            key.checkGrammar(grammarRule);

            Call call = callAdapter.createCall(key, category);
            if (call == null) {
                call = mBuildInCallAdapter.createCall(key, category);
            }
            if (call != null) {
//                if (inflateContextFactory == null) {
//                    inflateContextFactory = new ConverterFactory(scopeFactory,
//                            call.getParameterContext());
//                }
                call.setKey(key, callAdapter);
//                call.init(key, inflateContextFactory);

                for (int i = 0; i < mGlobalInterceptorList.size(); i++) {
                    call.addInterceptor(mGlobalInterceptorList.get(i), false);
                }

                Token tokenAnnotation = key.getAnnotation(Token.class);
                if (tokenAnnotation != null) {
                    for (String categoryByUser : tokenAnnotation.value()) {
                        ArrayList<Interceptor> interceptorList = mInterceptorByCategory
                                .get(categoryByUser);
                        if (interceptorList != null) {
                            for (int i = 0; i < interceptorList.size(); i++) {
                                call.addInterceptor(interceptorList.get(i), false);
                            }
                        }
                    }
                }

                // TODO: restore
//                boolean hasSet = call.hasCategory(CallAdapter.CATEGORY_SET);
//                boolean hasTrack = call.hasCategory(CallAdapter.CATEGORY_TRACK);
//                if (hasSet ^ hasTrack) {
//                    Restore restore = key.getAnnotation(Restore.class);
//                    if (restore != null) {
//                        call.setRestoreTarget(getOrCreateCall(key.record, key,
//                                hasSet ? CallAdapter.CATEGORY_TRACK : CallAdapter.CATEGORY_SET));
//                    }
//                }

//                if (hasTrack && key.isAnnotationPresent(Sticky.class)) {
//                    call.enableStickyTrack();
//                }

                Call wrappedCall = ((BuildInCallAdapter) mBuildInCallAdapter).wrapNormalTrack2RegisterIfNeeded(call);
                if (wrappedCall != null) {
                    wrappedCall.setKey(key, callAdapter);
//                    wrappedCall.init(key, inflateContextFactory);
                    return wrappedCall;
                }
            }
            return call;
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

    public static FlowConverter<?> findFlowConverter(Class<?> target) {
        return FLOW_CONVERTER_MAP.get(target);
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

        public void buildAsDefault() {
            if (sDefault == null) {
                sDefault = new Cartrofit();
            }
            sDefault.append(this);
            interceptors.clear();
            interceptorByCategory.clear();
        }
    }

    public static void addGlobalConverter(FlowConverter<?>... converters) {
        for (FlowConverter<?> converter : converters) {
            FLOW_CONVERTER_MAP.put(findFlowConverterTarget(converter), converter);
        }
    }

    private <T> T fromInternal(Class<T> api) {
        synchronized (mApiCache) {
            ApiRecord<T> record = getApi(api, true);
            if (record.apiObj != null) {
                return record.apiObj;
            }
            record.apiObj = (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(record, args);
                        }
                        if (method.isDefault()) {
                            throw new UnsupportedOperationException(
                                "Do not declare any default method in Cartrofit interface");
                        }
                        Call call;
                        synchronized (mApiCache) {
                            call = getOrCreateCall(record, new Key(record, method, false));
                        }
                        return call.invoke(Union.of(args));
                    });
            return record.apiObj;
        }
    }

    /*public static Call findCallById(int id) {
        final Cartrofit cartrofit = Objects.requireNonNull(sDefault);
        synchronized (cartrofit.mApiCache) {
            Class<?> apiClass = ID_ROUTER.findApiClassById(id);
            if (apiClass == null) {
                throw new CartrofitGrammarException("Can not find target Id:" + id);
            }
            ApiRecord<?> record = cartrofit.getApi(apiClass, true);
            return cartrofit.getOrCreateCallById(record, id, CallAdapter.CATEGORY_DEFAULT, false);
        }
    }

    public static Call findCallByMethod(Class<?> apiClass, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        final Cartrofit cartrofit = Objects.requireNonNull(sDefault);
        synchronized (cartrofit.mApiCache) {
            ApiRecord<?> record = cartrofit.getApi(apiClass, true);
            Method method = record.clazz.getDeclaredMethod(methodName, parameterTypes);
            return cartrofit.getOrCreateCall(record, new Key(record, method, false));
        }
    }*/

    <T> ApiRecord<T> getApi(Class<T> api) {
        return getApi(api, false);
    }

    <T> ApiRecord<T> getApi(Class<T> api, boolean throwIfNotDeclareScope) {
        ApiRecord<T> record = (ApiRecord<T>) mApiCache.get(api);
        if (record == null) {
            for (int i = 0; i < mCallAdapterList.size(); i++) {
                CallAdapter adapter = mCallAdapterList.get(i);
                Object scope = adapter.extractScope(api);
                if (scope != null) {
                    record = new ApiRecord<>(adapter, scope, api);
                    mApiCache.put(api, record);
                    return record;
                }
            }
            if (throwIfNotDeclareScope) {
                throw new CartrofitGrammarException("Do declare CarApi annotation in class:" + api);
            }
        }
        return record;
    }

    private Call getOrCreateCall(ApiRecord<?> record, Key key) {
        Call call = mCallCache.get(key);
        if (call == null) {
            call = record.createAdapterCall(key, CallAdapter.CATEGORY_DEFAULT);
            if (call == null) {
                throw new CartrofitGrammarException("Can not parse call from:" + key);
            }
            mCallCache.put(key, call);
            call.dispatchInit(call.getParameterContext());
        }
        return call;
    }

    Call getOrCreateCallById(Key key, ApiRecord<?> record, int id, int flag, boolean fromDelegate) {
        Method method = record.selfDependency.get(id);
        if (method == null) {
            Class<?> apiClass = ID_ROUTER.findApiClassById(id);
            if (apiClass == record.clazz || apiClass == null) {
                throw new CartrofitGrammarException("Can not find target Id:" + id + " from:" + record.clazz);
            }
            return getOrCreateCallById(key, getApi(apiClass, true), id, flag, fromDelegate);
        }
        Key targetKey = new Key(record, method, false);
        Call call;
        if (fromDelegate) {
            key.setDelegateKey(targetKey);
            call = getOrCreateCall(record, key, flag, false);
        } else {
            call = getOrCreateCall(record, targetKey, flag, true);
        }
        if (call == null) {
            throw new CartrofitGrammarException("Can not resolve target Id:" + id
                    + " in specific type from:" + this);
        }
        return call;
    }

    Call getOrCreateCall(ApiRecord<?> record, Key key, int category, boolean fromCache) {
        Call call;
        if (fromCache) {
            call = mCallCache.get(key);
            if (call == null) {
                call = record.createAdapterCall(key, category);
                mCallCache.put(key, call);
            }
        } else {
            call = record.createAdapterCall(key, category);
        }
        return call;
    }

    public interface Parameter {
        boolean isAnnotationPresent(Class<? extends Annotation> clazz);
        <A extends Annotation> A getAnnotation(Class<A> clazz);
        Annotation[] getAnnotations();
        boolean hasNoAnnotation();
        Class<?> getType();
        Type getGenericType();
        int getDeclaredIndex();
        Key getDeclaredKey();
        ParameterGroup getParameterGroup();

        @RequiresApi(Build.VERSION_CODES.O)
        String getName();
    }

    public interface ParameterGroup {
        String token();
        int getParameterCount();
        Parameter getParameterAt(int index);
        Key getDeclaredKey();
    }

    public static class Key implements ParameterGroup {
        public final Method method;
        public final boolean isCallbackEntry;

        public final Field field;

        ApiRecord<?> record;
        private int mId = -1;

        private Annotation[] annotations;
        private int parameterCount = -1;
        private int parameterGroupCount = -1;
        private Parameter returnParameter;
        private Parameter[] parameters;
        private ParameterGroup[] parameterGroups;
        private Key delegateKey;

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

        void setDelegateKey(Key delegateKey) {
            this.delegateKey = delegateKey;
        }

        private static <A extends Annotation> A find(Annotation[] annotations, Class<A> expectClass) {
            for (Annotation annotation : annotations) {
                if (expectClass.isInstance(annotation)) {
                    return (A) annotation;
                }
            }
            return null;
        }

        public int getId() {
            if (mId != -1) {
                return mId;
            }
            if (method == null) {
                return 0;
            }
            return mId = record.selfDependencyReverse.getOrDefault(method, 0);
        }

        public Parameter findParameterByAnnotation(Class<? extends Annotation> clazz) {
            for (int i = 0; i < getParameterCount(); i++) {
                Parameter parameter = getParameterAt(i);
                if (parameter.isAnnotationPresent(clazz)) {
                    return parameter;
                }
            }
            return null;
        }

        @Override
        public String token() {
            return "";
        }

        public Parameter getReturnAsParameter() {
            if (returnParameter != null) {
                return returnParameter;
            }
            if (method == null) {
                return null;
            }
            Class<?> declaredReturnType = method.getReturnType();
            if (declaredReturnType == void.class) {
                return null;
            }
            if (FLOW_CONVERTER_MAP.containsKey(declaredReturnType)
                    || Flow.class.isAssignableFrom(declaredReturnType)) {
                Class<?> userTargetType;
                WrappedData dataAnnotation = declaredReturnType.getAnnotation(WrappedData.class);
                if (dataAnnotation != null) {
                    userTargetType = dataAnnotation.type();
                } else {
                    userTargetType = WRAPPER_CLASS_MAP.get(declaredReturnType);
                }
                if (userTargetType != null) {
                    returnParameter = new ReturnAsParameter(userTargetType, userTargetType);
                } else {
                    Type declaredGenericType = method.getGenericReturnType();
                    if (declaredGenericType instanceof ParameterizedType) {
                        Type[] typeArray = ((ParameterizedType) declaredGenericType).getActualTypeArguments();
                        Type typeInFlow = typeArray.length > 0 ? typeArray[0] : null;
                        returnParameter = new ReturnAsParameter(getClassFromType(typeInFlow), typeInFlow);
                    } else {
                        throw new CartrofitGrammarException("Unsupported type:" + declaredGenericType);
                    }
                }
            } else {
                returnParameter = new ReturnAsParameter(declaredReturnType,
                        method.getGenericReturnType());
            }
            return returnParameter;
        }

        private class ReturnAsParameter implements Parameter, ParameterGroup {

            final Class<?> type;
            final Type genericType;

            ReturnAsParameter(Class<?> type, Type genericType) {
                this.type = type;
                this.genericType = genericType;
            }

            @Override
            public boolean isAnnotationPresent(Class<? extends Annotation> clazz) {
                return Key.this.isAnnotationPresent(clazz);
            }

            @Override
            public <A extends Annotation> A getAnnotation(Class<A> clazz) {
                return Key.this.getAnnotation(clazz);
            }

            @Override
            public Annotation[] getAnnotations() {
                return Key.this.getAnnotations();
            }

            @Override
            public boolean hasNoAnnotation() {
                return false;
            }

            @Override
            public Class<?> getType() {
                return type;
            }

            @Override
            public Type getGenericType() {
                return genericType;
            }

            @Override
            public int getDeclaredIndex() {
                return 0;
            }

            @Override
            public String token() {
                return "";
            }

            @Override
            public int getParameterCount() {
                return 1;
            }

            @Override
            public Parameter getParameterAt(int index) {
                return this;
            }

            @Override
            public Key getDeclaredKey() {
                return Key.this;
            }

            @Override
            public ParameterGroup getParameterGroup() {
                return this;
            }

            @Override
            public String getName() {
                return "";
            }
        }

        @Override
        public int getParameterCount() {
            if (parameterCount != -1) {
                return parameterCount;
            }
            if (method == null) {
                return 0;
            }
            return parameterCount = Cartrofit.getParameterCount(method);
        }

        @Override
        public Parameter getParameterAt(int index) {
            if (method == null) {
                return null;
            }
            if (parameters == null) {
                final int count = Cartrofit.getParameterCount(method);
                parameters = new Parameter[count];
                Class<?>[] parameterClass = method.getParameterTypes();
                Type[] parameterType = method.getGenericParameterTypes();
                Annotation[][] annotationMatrix = method.getParameterAnnotations();
                for (int i = 0; i < count; i++) {
                    parameters[i] = new ParameterImpl(method, parameterClass,
                            parameterType, annotationMatrix, i);
                }
            }
            if (index < 0 || index >= parameters.length) {
                return null;
            }
            return parameters[index];
        }

        @Override
        public Key getDeclaredKey() {
            return this;
        }

        public int getParameterGroupCount() {
            if (parameterGroupCount != -1) {
                return parameterGroupCount;
            }
            if (method == null) {
                return 0;
            }
            HashMap<String, ArrayList<Parameter>> groupMap = null;
            for (int i = 0; i < getParameterCount(); i++) {
                Parameter parameter = getParameterAt(i);
                Bind bind = parameter.getAnnotation(Bind.class);
                if (bind != null) {
                    for (String token : bind.token()) {
                        if (groupMap == null) {
                            groupMap = new HashMap<>();
                        }
                        ArrayList<Parameter> list = groupMap.get(token);
                        if (list == null) {
                            list = new ArrayList<>();
                            groupMap.put(token, list);
                        }
                        list.add(parameter);
                    }
                }
            }
            parameterGroupCount = groupMap != null ? groupMap.size() : 0;
            parameterGroups = new ParameterGroup[parameterGroupCount];
            if (groupMap != null) {
                int index = 0;
                for (Map.Entry<String, ArrayList<Parameter>> entry : groupMap.entrySet()) {
                    parameterGroups[index++] = new ParameterGroupImpl(entry.getKey(), entry.getValue());
                }
            }
            return parameterGroupCount;
        }

        public ParameterGroup getParameterGroupAt(int index) {
            if (index < 0 || index >= getParameterGroupCount()) {
                return null;
            }
            return parameterGroups[index];
        }

        private class ParameterImpl implements Parameter {

            final Class<?>[] parameterClass;
            final Type[] parameterType;
            final Annotation[][] annotationMatrix;
            final int index;
            final Method method;
            java.lang.reflect.Parameter parameterRef;

            ParameterImpl(Method method, Class<?>[] parameterClass,
                          Type[] parameterType, Annotation[][] annotationMatrix, int index) {
                this.parameterClass = parameterClass;
                this.parameterType = parameterType;
                this.annotationMatrix = annotationMatrix;
                this.index = index;
                this.method = method;
            }

            @Override
            public boolean isAnnotationPresent(Class<? extends Annotation> clazz) {
                return find(annotationMatrix[index], clazz) != null;
            }

            @Override
            public <A extends Annotation> A getAnnotation(Class<A> clazz) {
                return find(annotationMatrix[index], clazz);
            }

            @Override
            public Annotation[] getAnnotations() {
                return annotationMatrix[index];
            }

            @Override
            public boolean hasNoAnnotation() {
                return annotationMatrix[index] == null || annotationMatrix[index].length == 0;
            }

            @Override
            public Class<?> getType() {
                return parameterClass[index];
            }

            @Override
            public Type getGenericType() {
                return parameterType[index];
            }

            @Override
            public int getDeclaredIndex() {
                return index;
            }

            @Override
            public Key getDeclaredKey() {
                return Key.this;
            }

            @Override
            public ParameterGroup getParameterGroup() {
                return Key.this;
            }

            @Override
            public String getName() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (parameterRef == null) {
                        parameterRef = method.getParameters()[index];
                    }
                    return parameterRef.getName();
                } else {
                    return null;
                }
            }
        }

        private class ParameterGroupImpl implements ParameterGroup {

            private final String token;
            private final ArrayList<Parameter> parameters;

            ParameterGroupImpl(String token, ArrayList<Parameter> parameters) {
                this.token = token;
                this.parameters = parameters;
            }

            @Override
            public String token() {
                return token;
            }

            @Override
            public int getParameterCount() {
                return parameters.size();
            }

            @Override
            public Parameter getParameterAt(int index) {
                return parameters.get(index);
            }

            @Override
            public Key getDeclaredKey() {
                return Key.this;
            }
        }

        public <S> S getScopeObj() {
            return (S) record.scopeObj;
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

        void checkGrammar(ArrayList<CallAdapter.CallSolution<?>> grammarRules) {
            if (isInvalid()) {
                throw new CartrofitGrammarException("Invalid key:" + this);
            }
            if (grammarRules.size() == 0) {
                throw new RuntimeException("No annotation requirement??");
            }
            boolean qualified = false;
            int checkIndex = 0;
            while (checkIndex < grammarRules.size()) {
                CallAdapter.CallSolution<?> solution = grammarRules.get(checkIndex++);
                Annotation annotation = getAnnotation(solution.getGrammarContext());
                if (annotation != null) {
                    if (qualified) {
                        throw new CartrofitGrammarException("More than one annotation presented by:" + this);
                    }

                    solution.checkParameterGrammar(annotation, this);

                    if (field != null && solution.hasCategory(CallAdapter.CATEGORY_SET)
                            && Modifier.isFinal(field.getModifiers())) {
                        throw new CartrofitGrammarException("Invalid final key:" + this);
                    }

                    if (method != null
                            && ((!isCallbackEntry && solution.hasCategory(CallAdapter.CATEGORY_GET))
                            || (isCallbackEntry && solution.hasCategory(CallAdapter.CATEGORY_SET)))) {
                        if (method.getReturnType() == void.class) {
                            throw new CartrofitGrammarException("Invalid return type:" + this);
                        }
                    }

                    qualified = true;
                }
            }
        }

        public Annotation[] getAnnotations() {
            if (annotations == null) {
                Annotation[] fromDelegate = delegateKey != null ? delegateKey.getAnnotations() : null;
                Annotation[] fromSelf = method != null ? method.getDeclaredAnnotations() : field.getDeclaredAnnotations();
                if (fromDelegate == null || fromDelegate.length == 0) {
                    annotations = fromSelf;
                } else if (fromSelf != null) {
                    // TODO: remove duplicate annotation type
                    Annotation[] annotations = new Annotation[fromDelegate.length + fromSelf.length];
                    System.arraycopy(fromSelf, 0, annotations, 0, fromSelf.length);
                    System.arraycopy(fromDelegate, 0, annotations, fromSelf.length, fromDelegate.length);
                    this.annotations = annotations;
                }
            }
            return annotations;
        }

        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return find(getAnnotations(), annotationClass);
        }

        public <T extends Annotation> boolean isAnnotationPresent(Class<T> annotationClass) {
            return find(getAnnotations(), annotationClass) != null;
        }

        public Class<?> getReturnType() {
            return method != null ? method.getReturnType() : field.getType();
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
