package com.liyangbin.carretrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.carretrofit.annotation.Apply;
import com.liyangbin.carretrofit.annotation.ApplySuper;
import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.CarValue;
import com.liyangbin.carretrofit.annotation.Combine;
import com.liyangbin.carretrofit.annotation.Get;
import com.liyangbin.carretrofit.annotation.Inject;
import com.liyangbin.carretrofit.annotation.InjectSuper;
import com.liyangbin.carretrofit.annotation.Set;
import com.liyangbin.carretrofit.annotation.Track;
import com.liyangbin.carretrofit.annotation.WrappedData;
import com.liyangbin.carretrofit.funtion.Operator;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
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
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CarRetrofit {

    private static final ConverterStore GLOBAL_CONVERTER = new ConverterStore(null);
    private static final Object SKIP = new Object();
    private static CarRetrofit sDefault;
    private final HashMap<Class<?>, ApiRecord<?>> mApiCache = new HashMap<>();

    private HashMap<String, DataSource> mDataMap;
    private HashMap<Method, MethodHandler> mHandlerCache = new HashMap<>();
    private ConverterStore mConverterStore;
    private InterceptorChain mChainHead;
    private StickyType mDefaultStickyType;

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
        for (int i = 0; i < builder.converters.size(); i++) {
            mConverterStore.addConverter(builder.converters.get(i));
        }
        for (int i = 0; i < builder.interceptors.size(); i++) {
            mChainHead = new InterceptorChain(mChainHead, builder.interceptors.get(i));
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

        ApiRecord(Class<T> clazz) {
            this.clazz = clazz;
            CarApi carApi = Objects.requireNonNull(clazz.getAnnotation(CarApi.class));
            this.dataScope = carApi.scope();
            this.apiArea = carApi.area();
            this.stickyType = carApi.sticky();
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

            this.source = Objects.requireNonNull(mDataMap.get(dataScope),
                    "Make sure use a valid scope id passed by Builder().addDataSource()");
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
                } else if (Converter.class.isAssignableFrom(ifClazz)) {
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

    @SuppressWarnings("unchecked")
    public <T> T from(Class<T> api) {
        ApiRecord<T> record = fromApi(api);
        synchronized (mApiCache) {
            if (record.apiObj != null) {
                return record.apiObj;
            }
            record.apiObj = (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(this, args);
                        }
                        if (method.isDefault()) {
                            throw new UnsupportedOperationException(
                                "Do not declare any default method in CarRetrofit interface");
                        }
                        if (args != null && args.length > 1) {
                            throw new UnsupportedOperationException(
                                    "Do not declare any method with multiple parameters");
                        }
                        MethodHandler handler = mHandlerCache.get(method);
                        if (handler == null) {
                            handler = new MethodHandler(method);
                            mHandlerCache.put(method, handler);
                        }
                        return handler.invoke(args != null ? args[0] : null);
                    }
                });
        }
        return record.apiObj;
    }

    @SuppressWarnings("unchecked")
    private <T> ApiRecord<T> fromApi(Class<T> api) {
        synchronized (mApiCache) {
            ApiRecord<T> record = (ApiRecord<T>) mApiCache.get(api);
            if (record == null) {
                record = new ApiRecord<>(api);
                mApiCache.put(api, record);
            }
            return record;
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
            return hasUnresolvableType(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof TypeVariable) {
            return true;
        }
        if (type instanceof WildcardType) {
            return true;
        }
        String className = type == null ? "null" : type.getClass().getName();
        throw new CarRetrofitException("Expected a Class, ParameterizedType, or "
                + "GenericArrayType, but <" + type + "> is of type " + className);
    }

    private class MethodHandler {
        private static final int FLAG_FIRST_BIT = 1;

        private static final int FLAG_PARSE_SET = FLAG_FIRST_BIT;
        private static final int FLAG_PARSE_GET = FLAG_FIRST_BIT << 1;
        private static final int FLAG_PARSE_TRACK = FLAG_FIRST_BIT << 2;
        private static final int FLAG_PARSE_INJECT = FLAG_FIRST_BIT << 3;
        private static final int FLAG_PARSE_APPLY = FLAG_FIRST_BIT << 4;
        private static final int FLAG_PARSE_ALL = FLAG_PARSE_SET | FLAG_PARSE_GET
                | FLAG_PARSE_TRACK | FLAG_PARSE_INJECT | FLAG_PARSE_APPLY;

        Method method;
        CommandImpl operationCommand;
        ApiRecord<?> apiClassRecord;

        private MethodHandler(Method method) {
            this.method = method;
            this.apiClassRecord = fromApi(method.getDeclaringClass());
//            if (apiClassRecord.interceptorChain != null) {
//                this.chain = apiClassRecord.interceptorChain;
//            } else {
//                this.chain = mChainHead;
//            }
//            if (apiClassRecord.converterStore != null) {
//                this.converterStore = apiClassRecord.converterStore;
//            } else {
//                this.converterStore = mConverterStore;
//            }
//            this.source = Objects.requireNonNull(mDataMap.get(apiClassRecord.dataScope),
//                    "Make sure use a valid scope id passed by Builder().addDataSource()");

            Annotation[] methodAnnotation = method.getDeclaredAnnotations();
            for (Annotation annotation : methodAnnotation) {
                operationCommand = parseAnnotation(annotation);
                if (operationCommand != null) {
                    operationCommand.init(apiClassRecord, annotation, method, null);
                    break;
                }
            }
            Objects.requireNonNull(operationCommand,
                    "Can not parse command from method:" + method);
        }

        private MethodHandler(Class<?> clazz, boolean injectOrApply) {
//            if (injectOrApply) {
//                operationCommand = parseInjectClass(null, clazz, null, false);
//            } else {
//                operationCommand = parseApplyClass(null, clazz);
//            }
            Objects.requireNonNull(operationCommand,
                    "Can not parse command from class:" + clazz);
//            ConverterStore store = scope != null ? scope.store : mConverterStore;
//            operationCommand.dispatchSetConverterStore(store);
//            operationCommand.dispatchArgs(null, null);
//            chain = mChainHead;
        }

        private CommandImpl parseAnnotation(Annotation annotation) {
            return parseAnnotation(method.getDeclaringClass(), method.getReturnType(),
                    annotation, FLAG_PARSE_ALL);
        }

        private CommandImpl parseAnnotation(Class<?> areaScope, Class<?> targetType,
                                            Annotation annotation, int flag) {
            CommandImpl command = null;
            if ((flag & FLAG_PARSE_SET) != 0 && annotation instanceof Set) {
                command = new CommandSet();
            } /*else if ((flag & FLAG_PARSE_SET) != 0 && annotation instanceof MultiSet) {
                MultiSet multiSet = (MultiSet) annotation;
                CommandGroup group = new CommandGroup();
                for (Set set : multiSet.set()) {
                    group.childrenCommand.add(parseAnnotation(areaScope, targetType, set, flag));
                }
                command = group;
            }*/ else if ((flag & FLAG_PARSE_GET) != 0 && annotation instanceof Get) {
                command = new CommandGet();
            } else if ((flag & FLAG_PARSE_TRACK) != 0 && annotation instanceof Track) {
                command = new CommandTrack();
            } else if ((flag & FLAG_PARSE_INJECT) != 0 && annotation instanceof Inject) {
                for (int i = 0; i < method.getParameterCount(); i++) {
                    Class<?> clazz = method.getParameterTypes()[i];
                    if (!clazz.isPrimitive() && !clazz.isArray() && clazz != String.class) {
                        command = parseInjectClass(areaScope, clazz, annotation,
                                clazz == targetType);
                        break;
                    }
                }
                if (targetType != void.class) {
                    if (command != null) {
                        throw new CarRetrofitException("Inject method with input parameter must return void type");
                    }
                    command = parseInjectClass(areaScope, targetType, annotation, true);
                }
            } else if ((flag & FLAG_PARSE_APPLY) != 0 && annotation instanceof Apply) {
                if (targetType != void.class) {
                    throw new CarRetrofitException("Can not have any return type with Apply");
                }
                for (int i = 0; i < method.getParameterCount(); i++) {
                    Class<?> clazz = method.getParameterTypes()[i];
                    if (!clazz.isPrimitive() && !clazz.isArray() && clazz != String.class) {
                        command = parseApplyClass(areaScope, clazz, annotation);
                        break;
                    }
                }
            } else if (annotation instanceof Combine) {
                CommandCombine commandCombine = new CommandCombine();
                Combine combine = (Combine) annotation;
                String[] elementTokens = combine.elements();
                searchAndCreateCombineChildCommand(apiClassRecord.clazz,
                        Arrays.asList(combine.elements()), commandCombine);
                command = commandCombine;
            }
            return command;
        }

        private CommandImpl parseInjectClass(Class<?> parentAreaScope, Class<?> targetClass,
                                             Annotation annotation, boolean doReturn) {
            if (targetClass.isPrimitive() || targetClass.isArray() || targetClass == String.class) {
                throw new CarRetrofitException("Can not use Inject on class type:" + targetClass);
            }

            CommandInject injectCommand = new CommandInject(targetClass, doReturn);
            Class<?> loopClass = targetClass;
            do {
                Class<?> areaScopeClass;
                if (loopClass.isAnnotationPresent(CarApi.class)) {
                    areaScopeClass = loopClass;
                } else {
                    areaScopeClass = parentAreaScope;
                }
                for (Field field : loopClass.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers)) {
                        continue;
                    }
                    CommandImpl childCommand = null;
                    for (Annotation fieldAnnotation : field.getDeclaredAnnotations()) {
                        if (fieldAnnotation instanceof Inject) {
                            if (Modifier.isFinal(modifiers)) {
                                throw new CarRetrofitException("Can not use Inject command on a final field");
                            }
                            childCommand = parseInjectClass(areaScopeClass, field.getType(),
                                    fieldAnnotation, true);
                        } else if (fieldAnnotation instanceof Get || fieldAnnotation instanceof Track) {
                            if (Modifier.isFinal(modifiers)) {
                                throw new CarRetrofitException("Can not use Set or Track command on a final field");
                            }
                            childCommand = parseAnnotation(areaScopeClass, field.getType(),
                                    fieldAnnotation, FLAG_PARSE_GET | FLAG_PARSE_TRACK);
                        }
                        if (childCommand != null) {
                            injectCommand.addChildCommand(childCommand, field, fieldAnnotation);
                            break;
                        }
                    }
                }
                if (loopClass.isAnnotationPresent(InjectSuper.class)) {
                    loopClass = loopClass.getSuperclass();
                    if (loopClass != null && loopClass != Object.class) {
                        continue;
                    }
                }
                break;
            } while (true);

            if (injectCommand.childrenField.size() == 0) {
                throw new CarRetrofitException("failed to parse Inject command from type:" + targetClass);
            }
            return injectCommand;
        }

        private CommandImpl parseApplyClass(Class<?> parentAreaScope, Class<?> targetClass,
                                            Annotation annotation) {
            if (targetClass.isPrimitive() || targetClass.isArray() || targetClass == String.class) {
                throw new CarRetrofitException("Can not use Apply on class type:" + targetClass);
            }

            CommandApply applyCommand = new CommandApply(targetClass);
            Class<?> loopClass = targetClass;
            do {
                Class<?> areaScopeClass;
                if (targetClass.isAnnotationPresent(CarApi.class)) {
                    areaScopeClass = targetClass;
                } else {
                    areaScopeClass = parentAreaScope;
                }
                for (Field field : loopClass.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers)) {
                        continue;
                    }
                    CommandImpl childCommand = null;
                    for (Annotation fieldAnnotation : field.getDeclaredAnnotations()) {
                        if (fieldAnnotation instanceof Apply) {
                            childCommand = parseApplyClass(areaScopeClass, field.getType(),
                                    fieldAnnotation);
                        } else if (fieldAnnotation instanceof Set) {
                            childCommand = parseAnnotation(areaScopeClass, field.getType(),
                                    fieldAnnotation, FLAG_PARSE_SET);
                        }
                        if (childCommand != null) {
                            applyCommand.addChildCommand(childCommand, field, fieldAnnotation);
                            break;
                        }
                    }
                }
                if (loopClass.isAnnotationPresent(ApplySuper.class)) {
                    loopClass = loopClass.getSuperclass();
                    if (loopClass != null && loopClass != Object.class) {
                        continue;
                    }
                }
                break;
            } while (true);

            if (applyCommand.childrenField.size() == 0) {
                throw new CarRetrofitException("failed to parse Apply command from type:" + targetClass);
            }
            return applyCommand;
        }

        private void searchAndCreateCombineChildCommand(Class<?> apiClazz, List<String> tokenList,
                                                        CommandCombine parent) {
            Method[] declaredMethods = apiClazz.getDeclaredMethods();
            for (Method method : declaredMethods) {
                Track track = method.getDeclaredAnnotation(Track.class);
                if (track != null) {
                    if (tokenList.contains(track.token())) {
                        CommandTrack commandTrack = new CommandTrack();
                        parent.addChildCommand(commandTrack, null, track);
                        continue;
                    }
                }
                Get get = method.getDeclaredAnnotation(Get.class);
                if (get != null) {
                    if (tokenList.contains(get.token())) {
                        CommandGet commandGet = new CommandGet();
                        parent.addChildCommand(commandGet, null, get);
                        continue;
                    }
                }
                Combine combine = method.getDeclaredAnnotation(Combine.class);
                if (combine != null) {
                    if (tokenList.contains(combine.token())) {
                        CommandCombine commandCombine = new CommandCombine();
                        parent.addChildCommand(commandCombine, null, combine);
                        searchAndCreateCombineChildCommand(apiClazz,
                                Arrays.asList(combine.elements()), commandCombine);
                    }
                }
            }
            Class<?>[] apiSuperIfClassArray = apiClazz.getInterfaces();
            for (Class<?> apiSuperClass : apiSuperIfClassArray) {
                searchAndCreateCombineChildCommand(apiSuperClass, tokenList, parent);
            }
        }

        Object invoke(Object parameter) {
            try {
                return operationCommand.invokeWithChain(parameter);
            } catch (Throwable e) {
                throw new CarRetrofitException(e);
            }
        }

        @Override
        public String toString() {
            return "MethodHandler{" + "method=" + method + '}';
        }
    }

    private static abstract class CommandImpl implements Command {
        int key;
        int area;
        String token;
        ApiRecord<?> record;
        ConverterStore store;
        InterceptorChain chain;
        DataSource source;
        Method method;
        Field field;

        void init(ApiRecord<?> record, Annotation annotation, Method method, Field field) {
            this.record = record;
            this.source = record.source;
            this.store = record.converterStore;
            this.chain = record.interceptorChain;
            this.method = method;
            this.field = field;
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

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Field getField() {
            return field;
        }

        @Override
        public String getName() {
            if (field != null) {
                return field.getName();
            } else if (method != null) {
                return method.getName();
            } else {
                return null;
            }
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
        public void setKey(int key) {
            this.key = key;
        }

        @Override
        public int getKey() {
            return key;
        }

        @Override
        public void setArea(int area) {
            this.area = area;
        }

        @Override
        public int getArea() {
            return area;
        }

        @Override
        public void setSource(DataSource source) {
            this.source = source;
        }

        @Override
        public String getToken() {
            return token;
        }

        @Override
        public DataSource getSource() {
            return source;
        }

        @Override
        public String toString() {
            String stable = this.getClass().getSimpleName() + " key:0x"
                    + Integer.toHexString(key) + " area:0x" + Integer.toHexString(area);
            if (method != null) {
                stable += " from method:" + method.getName();
            }
            if (field != null) {
                stable += " from field:" + field.getName();
            }
            return stable;
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
        void init(ApiRecord<?> record, Annotation annotation, Method method, Field field) {
            super.init(record, annotation, method, field);
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

            final int count = childrenCommand.size();
            if (count != childrenField.size()) {
                throw new CarRetrofitException("invalid size. childrenCommand:" + count
                        + " while field size:" + childrenField.size());
            }

            if (target instanceof ApplyReceiver) {
                ((ApplyReceiver) target).onBeforeApply();
            }

            for (int i = 0; i < count; i++) {
                CommandImpl command = childrenCommand.get(i);
                Field field = childrenField.get(i);
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
        public String toString() {
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
        void init(ApiRecord<?> record, Annotation annotation, Method method, Field field) {
            super.init(record, annotation, method, field);
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

            final int count = childrenCommand.size();
            if (count != childrenField.size()) {
                throw new CarRetrofitException("invalid size. childrenCommand:" + count
                        + " while field size:" + childrenField.size());
            }

            if (target instanceof InjectReceiver) {
                ((InjectReceiver) target).onBeforeInject();
            }

            for (int i = 0; i < count; i++) {
                CommandImpl command = childrenCommand.get(i);
                Field field = childrenField.get(i);
                try {
                    if (command instanceof CommandInject) {
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
        public String toString() {
            boolean fromField = getField() != null;
            return "CommandInject target:" + targetClass.getSimpleName()
                    + " from" + (!fromField ? ("method:" + getMethod().getName())
                    : ("field:" + getField().getName()));
        }
    }

    private static abstract class CommandGroup extends CommandImpl {
        ArrayList<CommandImpl> childrenCommand = new ArrayList<>();
        ArrayList<Field> childrenField = new ArrayList<>();
        ArrayList<Annotation> childrenAnnotation = new ArrayList<>();
        Method method;
        Field field;

        void addChildCommand(CommandImpl command, Field field, Annotation annotation) {
            childrenCommand.add(command);
            childrenAnnotation.add(annotation);
            if (field != null) {
                field.setAccessible(true);
                childrenField.add(field);
            }
        }

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Method method, Field field) {
            super.init(record, annotation, method, field);
            for (int i = 0; i < childrenCommand.size(); i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                childCommand.init(record, childrenAnnotation.get(i), method, childrenField.get(i));
            }
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Field getField() {
            return field;
        }
    }

    private static class CommandCombine extends CommandGroup {

        Operator operator;
        boolean returnFlow;

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Method method, Field field) {
            super.init(record, annotation, method, field);
            Combine combine = (Combine) annotation;
            String userSet = combine.token();
            if (userSet.length() > 0) {
                token = userSet;
            }

            for (int i = 0; i < childrenCommand.size(); i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                childCommand.init(record, childrenAnnotation.get(i), method, field);
                if (!returnFlow) {
                    if (childCommand instanceof CommandTrack) {
                        returnFlow = true;
                    } else if (childCommand instanceof CommandCombine) {
                        returnFlow = ((CommandCombine) childCommand).returnFlow;
                    }
                }
            }

            final String combinatorExp = combine.combinator();
            try {
                Field combinatorField = record.clazz.getDeclaredField(combinatorExp);
                Object operatorObj = combinatorField.get(null);
                String operatorFullName = Operator.class.getName();
                String prefix = operatorFullName.substring(0, operatorFullName.lastIndexOf("."));
                int childElementCount = childrenCommand.size();
                String targetFunctionFullName = prefix + ".Function" + childElementCount;
                Class<?> functionClass = Class.forName(targetFunctionFullName);
                if (functionClass.isInstance(operator)) {
                    this.operator = (Operator) operatorObj;
                } else {
                    throw new CarRetrofitException("Operator:" + combinatorExp
                            + " doesn't match element count:" + childElementCount);
                }
            } catch (ReflectiveOperationException e) {
                throw new CarRetrofitException(e);
            }
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            return null;
        }

        @Override
        public CommandType type() {
            return null;
        }
    }

    private static class CommandSet extends CommandImpl {
        BuildInValue buildInValue;
        Converter<Object, ?> argConverter;

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Method method, Field field) {
            super.init(record, annotation, method, field);
            Set set = (Set) annotation;
            key = set.key();
            buildInValue = BuildInValue.build(set.value());
            resolveArea(set.area());
            String userSet = set.token();
            if (userSet.length() > 0) {
                token = userSet;
            }

            if (buildInValue != null) {
                return;
            }
            if (field != null) {
                resolveArgConverter(field.getType(), field);
            } else {
                Class<?>[] parameterArray = method.getParameterTypes();
                if (parameterArray.length > 0) {
                    Class<?> userArgType = parameterArray[0];
                    resolveArgConverter(userArgType, method);
                }
            }
        }

        private void resolveArgConverter(Class<?> userArgClass, AccessibleObject refObj) {
            if (buildInValue != null) {
                return;
            }
            Class<?> carArgClass;
            try {
                carArgClass = source.extractValueType(key);
            } catch (Exception e) {
                throw new CarRetrofitException(e);
            }
            Converter<?, ?> converter = ConverterStore.find(this,
                    userArgClass, carArgClass, store);
            if (converter != null) {
                argConverter = (Converter<Object, ?>) converter;
            }
        }

        Object collectArgs(Object parameter) throws Throwable {
            if (buildInValue != null) {
                return buildInValue.extractValue(source.extractValueType(key));
            }
            Object rawArg;
            if (field != null) {
                try {
                    rawArg = field.get(parameter);
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
            source.set(key, area, collectArgs(parameter));
            return SKIP;
        }

        @Override
        public CommandType type() {
            return CommandType.SET;
        }

        @Override
        public boolean fromApply() {
            return field != null;
        }
    }

    private static class CommandGet extends CommandImpl {

        Converter<Object, ?> resultConverter;
        CarType type;
        boolean stickyGet;

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Method method, Field field) {
            super.init(record, annotation, method, field);
            if (annotation instanceof Get) {
                Get get = (Get) annotation;
                key = get.key();
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
                key = track.key();
                type = track.type();
                resolveArea(track.area());
                String userSet = track.token();
                if (userSet.length() > 0) {
                    token = userSet;
                }
                stickyGet = true;
            }

            if (!stickyGet) {
                if (field != null) {
                    resolveResultConverter(field.getType());
                } else {
                    resolveResultConverter(method.getReturnType());
                }
            }
        }

        private void resolveResultConverter(Class<?> userReturnClass) {
            Class<?> carReturnClass;
            try {
                carReturnClass = type == CarType.AVAILABILITY ?
                        boolean.class : source.extractValueType(key);
            } catch (Exception e) {
                throw new CarRetrofitException(e);
            }
            Converter<?, ?> converter = ConverterStore.find(this, carReturnClass,
                    userReturnClass, store);
            resultConverter = (Converter<Object, ?>) converter;
        }

        @Override
        public Object invoke(Object parameter) throws Throwable {
            Object obj;
            if (stickyGet && type == CarType.ALL) {
                obj = source.get(key, area, CarType.VALUE);
                obj = new CarPropertyValue<>(key, area,
                        resultConverter != null ? resultConverter.convert(obj) : obj);
                return obj;
            } else {
                obj = source.get(key, area, type);
                return resultConverter != null ? resultConverter.convert(obj) : obj;
            }
        }

        @Override
        public CommandType type() {
            return stickyGet ? CommandType.STICKY_GET : CommandType.GET;
        }

        @Override
        public boolean fromInject() {
            return field != null;
        }

        @Override
        public String toString() {
            String stable = super.toString();
            if (stickyGet) {
                stable += " stickyGet";
            }
            if (type != CarType.VALUE) {
                stable += " valueType:" + type;
            }
            return stable;
        }
    }

    private static class CommandTrack extends CommandImpl {

        boolean stickyTrack;
        boolean stickyUseCache;
        CarType type;
        Converter<Flow<?>, ?> resultConverter;
        Converter<Object, ?> mapConverter;
        CommandGet stickyGet;

        @Override
        void init(ApiRecord<?> record, Annotation annotation, Method method, Field field) {
            super.init(record, annotation, method, field);
            Track track = (Track) annotation;
            key = track.key();
            type = track.type();
            if (type == CarType.CONFIG) {
                throw new CarRetrofitException("Can not use type CONFIG mode in Track operation");
            }

            resolveArea(track.area());

            StickyType stickyType = track.sticky();
            if (stickyType == StickyType.NO_SET) {
                stickyType = record.stickyType;
            }
            if (stickyType != StickyType.NO_SET) {
                stickyTrack = stickyType != StickyType.OFF;
                stickyUseCache = stickyType == StickyType.ON;
            }
            String userSet = track.token();
            if (userSet.length() > 0) {
                token = userSet;
            }

            if (field != null) {
                resolveResultConverter(field.getType());
                resolveMapConverter(field.getGenericType(), field);
            } else {
                resolveResultConverter(method.getReturnType());
                resolveMapConverter(method.getGenericReturnType(), method);
            }

            if (stickyTrack) {
                stickyGet = new CommandGet();
                stickyGet.init(record, annotation, method, field);
            }
        }

        @Override
        public boolean fromInject() {
            return field != null;
        }

        private void resolveResultConverter(Class<?> userReturnClass) {
            Converter<?, ?> converter = ConverterStore.find(this, Flow.class,
                    userReturnClass, store);
            resultConverter = (Converter<Flow<?>, ?>) converter;
        }

        @SuppressWarnings("unchecked")
        private void resolveMapConverter(Type targetType, AccessibleObject refObj) {
            if (type == CarType.ALL
                    || (resultConverter != null && resultConverter instanceof ConverterMapper)) {
                Class<?> userTargetType = null;
                if (type != CarType.ALL) {
                    Type userDeclaredType;
                    if (targetType instanceof ParameterizedType) {
                        userDeclaredType = ((ParameterizedType) targetType).getRawType();
                    } else {
                        userDeclaredType = targetType;
                    }
                    if (userDeclaredType instanceof Class) {
                        Class<?> userDeclaredClass = (Class<?>) userDeclaredType;
                        WrappedData dataAnnotation = userDeclaredClass.getAnnotation(WrappedData.class);
                        if (dataAnnotation != null) {
                            userTargetType = dataAnnotation.type();
                        } else if (resultConverter != null) {
                            Class<?> converterClass = resultConverter.getClass();
                            dataAnnotation = converterClass.getAnnotation(WrappedData.class);
                            if (dataAnnotation != null) {
                                userTargetType = dataAnnotation.type();
                            }
                        }
                    }
                }
                if (userTargetType == null && targetType instanceof ParameterizedType) {
                    if (hasUnresolvableType(targetType)) {
                        throw new CarRetrofitException("Can not parse type:" + targetType
                                + " from:" + refObj);
                    }
                    Type[] typeArray = ((ParameterizedType) targetType).getActualTypeArguments();
                    if (typeArray.length != 1) {
                        throw new CarRetrofitException("Can not extract target type from:"
                                + targetType + " from:" + refObj);
                    }
                    Type typeInFlow = typeArray[0];
                    userTargetType = getClassFromType(typeInFlow);
                    if (type == CarType.ALL) {
                        if (userTargetType != CarPropertyValue.class) {
                            throw new CarRetrofitException("Can not resolve" + userTargetType
                                    + " in type ALL mode");
                        }
                        if (typeInFlow instanceof ParameterizedType) {
                            typeArray = ((ParameterizedType) typeInFlow).getActualTypeArguments();
                            userTargetType = getClassFromType(typeArray[0]);
                        }
                    }
                }
                if (userTargetType != null) {
                    Class<?> carType;
                    if (type == CarType.AVAILABILITY) {
                        carType = boolean.class;
                    } else {
                        try {
                            carType = source.extractValueType(key);
                        } catch (Exception e) {
                            throw new CarRetrofitException(e);
                        }
                    }
                    mapConverter = (Converter<Object, ?>) ConverterStore
                            .find(this, carType, userTargetType, store);
                    if (stickyGet != null && type == CarType.ALL) {
                        stickyGet.resultConverter = (Converter<Object, ?>) ConverterStore
                                .find(stickyGet, carType, userTargetType, store);
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object invoke(Object parameter) throws Throwable {
            Flow<CarPropertyValue<?>> flow = source.track(key, area);
            Flow<?> result = flow;
            boolean valueMapped = false;
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
                        valueMapped = true;
                    }
                    break;
            }
            if (stickyTrack) {
                result = new StickyFlowImpl<>(result, stickyUseCache, stickyGet);
            }
            Object obj = resultConverter != null ? resultConverter.convert(result) : result;
            return !valueMapped && mapConverter != null && resultConverter != null ?
                    ((ConverterMapper<Object, Object>)resultConverter).map(obj, mapConverter) : obj;
        }

        @Override
        public CommandType type() {
            return CommandType.TRACK;
        }

        @Override
        public String toString() {
            String stable = super.toString();
            if (type != CarType.VALUE) {
                stable += " valueType:" + type;
            }
            if (stickyTrack) {
                stable += " stickyCache:" + stickyUseCache;
            }
            return stable;
        }
    }

    private static class MediatorFlow<FROM, TO> implements Flow<TO>, Consumer<FROM> {

        private Flow<FROM> base;
        private Function<FROM, TO> mediator;
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

        @Override
        @SuppressWarnings("unchecked")
        public void accept(FROM value) {
            if (consumers.size() > 0) {
                TO obj;
                if (mediator != null) {
                    obj = mediator.apply(value);
                } else {
                    obj = (TO) value;
                }
                for (int i = 0; i < consumers.size(); i++) {
                    consumers.get(i).accept(obj);
                }
            }
        }
    }

    private static class StickyFlowImpl<T> extends MediatorFlow<T, T> implements StickyFlow<T> {
        private T lastValue;
        private boolean valueReceived;
        private boolean stickyUseCache;
        private CommandGet stickyGet;

        private StickyFlowImpl(Flow<T> base, boolean stickyUseCache, CommandGet stickyGet) {
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
        public void accept(T value) {
            super.accept(value);
            if (this.stickyUseCache) {
                valueReceived = true;
                lastValue = value;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get() {
            T result;
            try {
                result = stickyUseCache && valueReceived ?
                        lastValue : (T) stickyGet.invokeWithChain(null);
            } catch (Throwable e) {
                throw new CarRetrofitException(e);
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

        @SuppressWarnings("unchecked")
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
