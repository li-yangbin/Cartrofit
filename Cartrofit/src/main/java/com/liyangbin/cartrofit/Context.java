package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.ContextElement;
import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.annotation.MethodCategory;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Token;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.FlowConverter;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

@SuppressWarnings("unchecked")
public abstract class Context {

    public static final int CATEGORY_SET = 1;
    public static final int CATEGORY_GET = 1 << 1;
    public static final int CATEGORY_TRACK = 1 << 2;
    public static final int CATEGORY_TRACK_EVENT = CATEGORY_TRACK | (1 << 3);

//    public static final int CATEGORY_REGISTER = 1 << 4;
//    public static final int CATEGORY_RETURN = 1 << 5;
//    public static final int CATEGORY_INJECT_IN = 1 << 6;
//    public static final int CATEGORY_INJECT_OUT = 1 << 7;
//    public static final int CATEGORY_INJECT = CATEGORY_INJECT_IN | CATEGORY_INJECT_OUT;

    public static final int CATEGORY_DEFAULT = 0xffffffff;

    static final HashMap<Class<?>, FlowConverter<?>> FLOW_CONVERTER_MAP = new HashMap<>();

    static final HashMap<Class<?>, Class<?>> WRAPPER_CLASS_MAP = new HashMap<>();
    static final Router ID_ROUTER = new Router();

    static {
        RxJavaConverter.addSupport();
        ObservableConverter.addSupport();
        LiveDataConverter.addSupport();
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

    public static void addGlobalConverter(FlowConverter<?>... converters) {
        for (FlowConverter<?> converter : converters) {
            FLOW_CONVERTER_MAP.put(findFlowConverterTarget(converter), converter);
        }
    }

    private final ArrayList<CallSolution<?>> mCallSolutionList = new ArrayList<>();
    private final HashMap<Class<?>, ConverterBuilder<?, ?, ?>> mConverterInputMap = new HashMap<>();
    private final HashMap<Class<?>, ConverterBuilder<?, ?, ?>> mConverterOutputMap = new HashMap<>();
    private final Converter<Union, Object> mDummyInputConverter = value -> value.getCount() > 0 ? value.get(0) : null;
    private final Converter<Object, Union> mDummyOutputConverter = Union::of;

    private final HashMap<Class<?>, Context.ApiRecord<?>> mApiCache = new HashMap<>();
    private final HashMap<Key, Call> mCallCache = new HashMap<>();
    private static final HashMap<Class<?>, HashMap<String, Field>> sElementClassMap = new HashMap<>();

    private final Context mParent;

    public Context(Context parent) {
        this.mParent = Objects.requireNonNull(parent,
                "Must provide a customized Context or RootContext");
        onProvideCallSolution(new CallSolutionBuilder());
        collectContextElement(getClass());
    }

    Context() {
        this.mParent = null;
    }

    private static void collectContextElement(Class<?> type) {
        if (type == Context.class || !Context.class.isAssignableFrom(type)) {
            return;
        }
        HashMap<String, Field> elementMap = sElementClassMap.get(type);
        if (elementMap == null) {
            elementMap = new HashMap<>();
            Class<?> looped = type;
            while (looped != null && looped != Context.class) {
                Field[] fields = type.getDeclaredFields();
                for (Field field : fields) {
                    ContextElement contextElement = field.getDeclaredAnnotation(ContextElement.class);
                    if (contextElement != null) {
                        final String token = contextElement.value();
                        Class<?> explicitType = contextElement.explicitType();
                        if (token.length() == 0 && explicitType == Object.class) {
                            throw new CartrofitGrammarException("Invalid element declaration " + field);
                        }
                        field.setAccessible(true);
                        Field duplicateField;
                        if (token.length() == 0) {
                            explicitType = field.getType();
                        }
                        if (explicitType == Object.class) {
                            duplicateField = elementMap.put(token, field);
                        } else {
                            duplicateField = elementMap.put(token + " type:" + explicitType.getName(),
                                    field);
                        }
                        if (duplicateField != null) {
                            throw new IllegalStateException("Duplicate context element declared by token:"
                                    + contextElement.value());
                        }
                    }
                }
                looped = looped.getSuperclass();
            }
            sElementClassMap.put(type, elementMap);
        }
    }

    public <T> T getContextElement(Class<?> explicitType) {
        return getContextElement(" type:" + explicitType.getName());
    }

    public <T> T getContextElement(String token, Class<?> explicitType) {
        return getContextElement(token + " type:" + explicitType.getName());
    }

    public <T> T getContextElement(String token) {
        HashMap<String, Field> elementMap = sElementClassMap.get(getClass());
        Field field = elementMap != null ? elementMap.get(token) : null;
        if (field == null) {
            if (mParent != null) {
                return mParent.getContextElement(token);
            }
            throw new IllegalStateException("Can not find context element by token:" + token);
        }
        try {
            return (T) field.get(this);
        } catch (IllegalAccessException illegalAccessException) {
            throw new RuntimeException("impossible", illegalAccessException);
        }
    }

    public <T> T from(Class<T> api) {
        synchronized (mApiCache) {
            ApiRecord<T> record = getApi(api);
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
                        Key key = new Key(record, method, false);
                        synchronized (mApiCache) {
                            call = getOrCreateCall(record, key, CATEGORY_DEFAULT, true);
                        }
                        return call.invoke(Union.ofArray(args));
                    });
            return record.apiObj;
        }
    }

    Call getOrCreateCall(ApiRecord<?> record, Key key, int category, boolean fromCache) {
        Call call;
        if (fromCache) {
            call = mCallCache.get(key);
            if (call == null) {
                call = record.createAdapterCall(key, category);
                mCallCache.put(key, call);
                call.dispatchInit(call.getParameterContext());
            }
        } else {
            call = record.createAdapterCall(key, category);
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
            return getOrCreateCallById(key, getApi(apiClass), id, flag, fromDelegate);
        }
        Key targetKey = new Key(record, method, false);
        Call call;
        if (fromDelegate) {
            key.setDelegateKey(targetKey);
            call = getOrCreateCall(record, key, flag, false);
        } else {
            call = getOrCreateCall(record, targetKey, flag, true);
        }
        return call;
    }

    Call getOrCreateCallById(Key key, int id, int category) {
        return getOrCreateCallById(key, key.record, id, category, false);
    }

    Call createDelegateCallById(Key key, int id, int category) {
        return getOrCreateCallById(key, key.record, id, category, true);
    }

    public final class CallSolutionBuilder {

        private CallSolutionBuilder() {
        }

        public <A extends Annotation> CallSolution<A> create(Class<A> annotationClass) {
            return new CallSolution<>(annotationClass);
        }
    }

    private <T> ApiRecord<T> getApi(Class<T> api) {
        ApiRecord<T> record = (ApiRecord<T>) mApiCache.get(api);
        if (record == null) {
            Object scope = extractScope(api);
            record = new ApiRecord<>(scope, api);
            mApiCache.put(api, record);
        }
        return record;
    }

//    public final Call createChildCall(Key key, int category) {
//        return getOrCreateCall(key.record, key, category, false);
//    }

    public final <T> void inflateCallback(Key key, Class<?> callbackClass,
                                          int category, Consumer<Call> resultReceiver) {
        ArrayList<Key> childKeys = getChildKey(key, callbackClass);
        for (int i = 0; i < childKeys.size(); i++) {
            Key childKey = childKeys.get(i);
            Call call = getOrCreateCall(childKey.record, childKey, category, false);
            resultReceiver.accept(call);
        }
    }

    public interface CallProvider<A extends Annotation, T extends Call> {
        T provide(int category, A annotation, Key key);
    }

    public final <INPUT> Converter<Union, INPUT> findInputConverter(FixedTypeCall<INPUT, ?> call) {
        ConverterBuilder<INPUT, ?, ?> builder = (ConverterBuilder<INPUT, ?, ?>) mConverterInputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkIn(call.getParameterContext()
                    .extractParameterFromCall(call));
        } else {
            return (Converter<Union, INPUT>) mDummyInputConverter;
        }
    }

    public final <OUTPUT> Converter<OUTPUT, Union> findCallbackOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        ConverterBuilder<?, OUTPUT, ?> builder = (ConverterBuilder<?, OUTPUT, ?>) mConverterOutputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkOutCallback(call.getParameterContext()
                    .extractParameterFromCall(call));
        } else {
            return (Converter<OUTPUT, Union>) mDummyOutputConverter;
        }
    }

    public FlowConverter<?> findFlowConverter(Call call) {
        if (call.getKey().isCallbackEntry) {
            return null;
        }
        return findFlowConverter(call.getKey().getReturnType());
    }

    public final <OUTPUT> Converter<OUTPUT, ?> findReturnOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        ConverterBuilder<?, OUTPUT, ?> builder = (ConverterBuilder<?, OUTPUT, ?>) mConverterOutputMap.get(call.getClass());
        if (builder != null) {
            return builder.checkOutReturn(call.getKey());
        } else {
            return null;
        }
    }

    public ArrayList<Key> getChildKey(Key parent, Class<?> callbackClass) {
        return getApi(callbackClass).getChildKey(parent);
    }

    public final class CallSolution<A extends Annotation> {
        IntPredicate predictor;
        Class<A> candidateClass;
        int expectedCategory;
        CallProvider<A, ?> provider;
        BiConsumer<A, Key> keyGrammarChecker;
        List<Class<? extends Annotation>[]> withInAnnotationCandidates;
        List<Class<? extends Annotation>> withAnnotationCandidates;
        HashMap<Class<? extends Annotation>, Class<?>> withAnnotationTypeMap;
//        boolean keepLookingIfNull;

        CallSolution(Class<A> candidateClass) {
            this.candidateClass = candidateClass;
            MethodCategory category = candidateClass.getDeclaredAnnotation(MethodCategory.class);
            if (category != null) {
                expectedCategory = category.value();
                if (expectedCategory == MethodCategory.CATEGORY_DEFAULT) {
//                    keepLookingIfNull = true;
                    predictor = flag -> true;
                } else {
                    predictor = flag -> (flag & expectedCategory) != 0;
                }
            } else {
                throw new CartrofitGrammarException("Must declare Category attribute on annotation:"
                        + candidateClass);
            }
        }

        @SafeVarargs
        public final CallSolution<A> checkParameterIncluded(Class<? extends Annotation>... included) {
            return checkParameterIncluded(null, included);
        }

        @SafeVarargs
        public final CallSolution<A> checkParameterIncluded(Class<?> fixedType, Class<? extends Annotation>... included) {
            if (included == null || included.length == 0) {
                return this;
            }
            if (fixedType != null) {
                if (withAnnotationTypeMap == null) {
                    withAnnotationTypeMap = new HashMap<>();
                }
                for (Class<? extends Annotation> annotationClass : included) {
                    withAnnotationTypeMap.put(annotationClass, fixedType);
                }
            }
            if (withInAnnotationCandidates == null) {
                withInAnnotationCandidates = new ArrayList<>();
            }
            withInAnnotationCandidates.add(included);
            return this;
        }

        public CallSolution<A> checkParameter(BiConsumer<A, Key> keyConsumer) {
            keyGrammarChecker = keyConsumer;
            return this;
        }

        public CallSolution<A> checkParameter(Class<? extends Annotation> clazz) {
            return checkParameter(clazz, null);
        }

        public CallSolution<A> checkParameter(Class<? extends Annotation> clazz, Class<?> fixedType) {
            if (fixedType != null) {
                if (withAnnotationTypeMap == null) {
                    withAnnotationTypeMap = new HashMap<>();
                }
                withAnnotationTypeMap.put(clazz, fixedType);
            }
            if (withAnnotationCandidates == null) {
                withAnnotationCandidates = new ArrayList<>();
            }
            withAnnotationCandidates.add(clazz);
            return this;
        }

        public <INPUT, OUTPUT, T extends FixedTypeCall<INPUT, OUTPUT>>
                ConverterBuilder<INPUT, OUTPUT, A> buildParameter(Class<T> callType) {
            if (mConverterInputMap.containsKey(callType) || mConverterOutputMap.containsKey(callType)) {
                throw new CartrofitGrammarException("There is a parameter solution exists already");
            }
            return new ConverterBuilder<>(callType, this);
        }

        void commitInputConverter(Class<?> callType, ConverterBuilder<?, ?, ?> builder) {
            mConverterInputMap.put(callType, builder);
        }

        void commitOutputConverter(Class<?> callType, ConverterBuilder<?, ?, ?> builder) {
            mConverterOutputMap.put(callType, builder);
        }

        public <T extends Call> void provide(CallProvider<A, T> provider) {
            this.provider = provider;
            mCallSolutionList.add(this);
        }

        private Call createCall(int category, Key key) {
            if (predictor.test(category)) {
                A annotation = key.getAnnotation(candidateClass);
                if (annotation != null) {
                    Call call = provider.provide(category, annotation, key);
                    if (call != null) {
                        call.addCategory(expectedCategory);
                    }
                    return call;
                }
            }
            return null;
        }

        boolean hasCategory(int category) {
            return (expectedCategory & category) != 0;
        }

        Class<? extends Annotation> getGrammarContext() {
            return candidateClass;
        }

        void checkParameterGrammar(Annotation annotation, Key key) {
            if (withAnnotationCandidates != null) {
                for (int i = 0; i < withAnnotationCandidates.size(); i++) {
                    Class<? extends Annotation> annotationClass = withAnnotationCandidates.get(i);
                    if (!checkParameterDeclared(key, annotationClass)) {
                        throw new CartrofitGrammarException("Can not find parameter with annotation:"
                                + annotationClass + " on:" + key);
                    }
                }
            }
            if (withInAnnotationCandidates != null) {
                for (int i = 0; i < withInAnnotationCandidates.size(); i++) {
                    for (Class<? extends Annotation> annotationClass : withInAnnotationCandidates.get(i)) {
                        if (checkParameterDeclared(key, annotationClass)) {
                            break;
                        }
                        throw new CartrofitGrammarException("Can not find parameter with annotation:"
                                + Arrays.toString(withInAnnotationCandidates.get(i)) + " on:" + key);
                    }
                }
            }
            if (keyGrammarChecker != null) {
                keyGrammarChecker.accept((A) annotation, key);
            }
        }

        private boolean checkParameterDeclared(Key key, Class<? extends Annotation> annotationClass) {
            for (int i = 0; i < key.getParameterCount(); i++) {
                Parameter parameter = key.getParameterAt(i);
                if (parameter.isAnnotationPresent(annotationClass)) {
                    Class<?> expectedType = withAnnotationTypeMap != null ?
                            withAnnotationTypeMap.get(annotationClass) : null;
                    if (expectedType == null
                            || classEquals(parameter.getType(), expectedType)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void getAnnotationCandidates(int category, ArrayList<CallSolution<?>> grammarRule) {
            if (predictor.test(category)) {
                grammarRule.add(this);
            }
        }
    }

    public Executor getSubscribeExecutor(String tag) {
        return mParent != null ? mParent.getSubscribeExecutor(tag) : null;
    }

    public Executor getConsumeExecutor(String tag) {
        return mParent != null ? mParent.getConsumeExecutor(tag) : null;
    }

    public Object extractScope(Class<?> scopeClass) {
        Object scopeObj = onExtractScope(scopeClass);
        if (scopeObj != null) {
            return scopeObj;
        }
        return mParent != null ? mParent.extractScope(scopeClass) : null;
    }

    public Object onExtractScope(Class<?> scopeClass) {
        return null;
    }

    public void onProvideCallSolution(CallSolutionBuilder builder) {
    }

    Call createCall(Key key, int category) {
        for (int i = 0; i < mCallSolutionList.size(); i++) {
            CallSolution<?> solution = mCallSolutionList.get(i);
            Call call = solution.createCall(category, key);
            if (call != null /*|| solution.keepLookingIfNull*/) {
                return call;
            }
        }
        return mParent != null ? mParent.createCall(key, category) : null;
    }

    void collectGrammarRules(int category, ArrayList<CallSolution<?>> grammarRule) {
        for (int i = 0; i < mCallSolutionList.size(); i++) {
            mCallSolutionList.get(i).getAnnotationCandidates(category, grammarRule);
        }
        if (mParent != null) {
            mParent.collectGrammarRules(category, grammarRule);
        }
    }

    public static <A extends Annotation> A findScopeByClass(Class<A> annotationClazz, Class<?> clazz) {
        A scope = clazz.getDeclaredAnnotation(annotationClazz);
        if (scope != null) {
            return scope;
        }
        Class<?> enclosingClass = clazz.getEnclosingClass();
        while (enclosingClass != null) {
            scope = enclosingClass.getDeclaredAnnotation(annotationClazz);
            if (scope != null) {
                return scope;
            }
            enclosingClass = enclosingClass.getEnclosingClass();
        }
        return null;
    }

    class ApiRecord<T> {
        private static final String ID_SUFFIX = "Id";

        Class<T> clazz;
        String dataScope;
        int apiArea;
        T apiObj;

        ArrayList<Key> childrenKey;

        HashMap<Integer, Method> selfDependency = new HashMap<>();
        HashMap<Integer, ArrayList<Context.CallSolution<?>>> grammarRuleMap = new HashMap<>();
        HashMap<Method, Integer> selfDependencyReverse = new HashMap<>();

        Object scopeObj;

        ApiRecord(Object scopeObj, Class<T> clazz) {
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
            ArrayList<Context.CallSolution<?>> grammarRule = grammarRuleMap.get(category);
            if (grammarRule == null) {
                grammarRule = new ArrayList<>();
                collectGrammarRules(category, grammarRule);
                grammarRuleMap.put(category, grammarRule);
            }

            key.checkGrammar(grammarRule);

            Call call = createCall(key, category);
            if (call != null) {
                call.setKey(key, Context.this);

                Token tokenAnnotation = key.getAnnotation(Token.class);
                if (tokenAnnotation != null) {
                    call.setTokenList(tokenAnnotation);
                }

                Call wrappedCall = RootContext.getInstance().wrapNormalTrack2RegisterIfNeeded(call);
                if (wrappedCall != null) {
                    wrappedCall.setKey(key, Context.this);
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

        ArrayList<Key> getChildKey(Key parentKey) {
            ArrayList<Key> result = new ArrayList<>();
            if (clazz.isInterface()) {
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    Key childKey = new Key(this, method, true);
                    if (!childKey.isInvalid()) {
                        childKey.setDelegateKey(parentKey);
                        result.add(childKey);
                    }
                }
            } else {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    Key childKey = new Key(this, field);
                    if (!childKey.isInvalid()) {
                        childKey.setDelegateKey(parentKey);
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
}
