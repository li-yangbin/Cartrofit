package com.android.carretrofit;

import android.car.hardware.CarPropertyValue;

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
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CarRetrofit {

    static final String EMPTY_VALUE = "car_retrofit_empty_value";
    private static final ConverterStore GLOBAL_CONVERTER = new ConverterStore(null);
    private static final Object SKIP = new Object();
    private static CarRetrofit sDefault;
    private static final HashMap<Class<?>, ApiRecord<?>> sDefaultApiCache = new HashMap<>();

    private HashMap<String, DataSource> mDataMap;
    private HashMap<Method, MethodHandler> mHandlerCache = new HashMap<>();
    private ConverterStore mConverterStore;
    private InterceptorChain mChainHead;

    static {
        RxJavaConverter.addSupport();
        ObservableConverter.addSupport();
        LiveDataConverter.addSupport();
    }

    private CarRetrofit(Builder builder) {
        mConverterStore = new ConverterStore("scope:" + this);
        mConverterStore.addParent(GLOBAL_CONVERTER);
        mDataMap = builder.dataMap;
        if (mDataMap.isEmpty()) {
            throw new IllegalArgumentException("CarRetrofit must be setup with data source");
        }
        for (int i = 0; i < builder.converters.size(); i++) {
            addConverter(builder.converters.get(i));
        }
        for (int i = 0; i < builder.interceptors.size(); i++) {
            addInterceptor(builder.interceptors.get(i));
        }
    }

    public static void setDefault(CarRetrofit retrofit) {
        synchronized (sDefaultApiCache) {
            sDefaultApiCache.clear();
            sDefault = retrofit;
        }
    }

    public static CarRetrofit getDefault() {
        return Objects.requireNonNull(sDefault);
    }

    @SuppressWarnings("unchecked")
    public static <T> T from(Class<T> api) {
        ApiRecord<T> record;
        synchronized (sDefaultApiCache) {
            record = (ApiRecord<T>) sDefaultApiCache.get(api);
            if (record == null) {
                record = new ApiRecord<>(api);
                sDefaultApiCache.put(api, record);
                record.apiObj = Objects.requireNonNull(sDefault,
                        "Call setDefault() before use").create(api, record);
            }
        }
        return record.apiObj;
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

    static class ApiRecord<T> {
        private static final String INTERCEPTOR_NAME = "INTERCEPTOR";
        Class<T> clazz;
        T apiObj;
        Interceptor apiOwnedInterceptor;

        ApiRecord(Class<T> clazz) {
            this.clazz = clazz;

            try {
                Field selfInterceptorField = clazz.getDeclaredField(INTERCEPTOR_NAME);
                apiOwnedInterceptor = (Interceptor) selfInterceptorField.get(null);
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    }

    public static class ConverterStore {
        final ArrayList<ConverterWrapper<?, ?>> converterWrapperList = new ArrayList<>();
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
                throw new CarRetrofitException("Invalid args:" + clazz);
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
                if (checkConverter(convertFrom, convertTo)) {
                    throw new CarRetrofitException("Can not add duplicate converter from:" +
                            toClassString(convertFrom) + " to:" + toClassString(convertTo));
                }
                converterWrapperList.add(new ConverterWrapper<>(convertFrom, convertTo,
                        converter));
            } else {
                throw new CarRetrofitException("invalid converter class:" + implementsBy
                        + " type:" + Arrays.toString(ifTypes));
            }
        }

        private boolean checkConverter(Class<?> from, Class<?> to) {
            ConverterStore parentStore = this.parentStore;
            this.parentStore = null;
            boolean hasConverter = find(from, to) != null;
            this.parentStore = parentStore;
            return hasConverter;
        }

        private Converter<?, ?> find(Class<?> from, Class<?> to) {
            if (from == to) {
                return null;
            }
            for (int i = 0; i < converterWrapperList.size(); i++) {
                ConverterWrapper<?, ?> converterWrapper = converterWrapperList.get(i);
                Converter<?, ?> converter = converterWrapper.asConverter(from, to);
                if (converter != null) {
                    return converter;
                }
            }
            return parentStore != null ? parentStore.find(from, to) : null;
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

        static Converter<?, ?> find(Class<?> from, Class<?> to, ConverterStore store) {
            if (from == to) {
                return null;
            }
            from = from.isPrimitive() ? boxTypeOf(from) : from;
            to = to.isPrimitive() ? boxTypeOf(to) : to;
            if (from == to) {
                return null;
            }
            Converter<?, ?> converter = store.find(from, to);
            if (converter != null) {
                return converter;
            }
            throw new CarRetrofitException("Can not resolve converter from:"
                    + toClassString(from) + " to:" + toClassString(to));
        }

        private static String toClassString(Class<?> clazz) {
            return clazz.isArray() ? clazz.getComponentType() + "[]" : clazz.toString();
        }
    }

    public static final class Builder {
        private ArrayList<Converter<?, ?>> converters = new ArrayList<>();
        private HashMap<String, DataSource> dataMap = new HashMap<>();
        private ArrayList<Interceptor> interceptors = new ArrayList<>();

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

        public Builder addInterceptor(Interceptor interceptor) {
            interceptors.add(interceptor);
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

    public void addConverter(Converter<?, ?> converter) {
        mConverterStore.addConverter(converter);
    }

    public void addInterceptor(Interceptor interceptor) {
        mChainHead = new InterceptorChain(mChainHead, interceptor);
    }

//    public void inject(Object obj) {
//        inject(obj, null);
//    }
//
//    private void inject(Object obj, ConverterScope scope) {
//        new MethodHandler(obj.getClass(), true, scope).invoke(new Object[]{obj});
//    }
//
//    public void apply(Object obj) {
//        apply(obj, null);
//    }
//
//    private void apply(Object obj, ConverterScope scope) {
//        new MethodHandler(obj.getClass(), false, scope).invoke(new Object[]{obj});
//    }

//    public final class ConverterScope {
//        ConverterStore store;
//
//        private ConverterScope(String name) {
//            store = new ConverterStore(name);
//            store.addParent(mConverterStore);
//        }
//
//        public ConverterScope addConverter(Converter<?, ?> converter) {
//            store.addConverter(converter);
//            return this;
//        }
//
//        public <T> T create(Class<T> api) {
//            return CarRetrofit.this.create(api, this);
//        }
//
//        public void inject(Object obj) {
//            CarRetrofit.this.inject(obj, this);
//        }
//
//        public void apply(Object obj) {
//            CarRetrofit.this.apply(obj, this);
//        }
//    }
//
//    public ConverterScope obtainConverterScope() {
//        return obtainConverterScope(null);
//    }
//
//    public ConverterScope obtainConverterScope(String scopeName) {
//        if (scopeName == null) {
//            return new ConverterScope(null);
//        }
//        ConverterScope scope = mScopeMap.get(scopeName);
//        if (scope == null) {
//            mScopeMap.put(scopeName, scope = new ConverterScope(scopeName));
//        }
//        return scope;
//    }
//
//    public <T> T create(Class<T> api) {
//        return create(api, (ConverterScope) null);
//    }
//
//    public <T> T create(Class<T> api, String scopeName) {
//        return create(api, Objects.requireNonNull(mScopeMap.get(scopeName),
//                "Add scope with name:" + scopeName + " before create()"));
//    }

    @SuppressWarnings("unchecked")
    private <T> T create(Class<T> api, ApiRecord<T> record) {
        return (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api},
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
                        MethodHandler handler = mHandlerCache.get(method);
                        if (handler == null) {
                            handler = new MethodHandler(record, method);
                            mHandlerCache.put(method, handler);
                        }
                        return handler.invoke(args);
                    }
                });
    }

    public <T> T create(Class<T> api) {
        return create(api, new ApiRecord<>(api));
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
        InterceptorChain chain;
        ApiRecord<?> apiClassRecord;

        private MethodHandler(ApiRecord<?> record, Method method) {
            this.method = method;
            this.apiClassRecord = record;

            Annotation[] methodAnnotation = method.getDeclaredAnnotations();
            for (Annotation annotation : methodAnnotation) {
                operationCommand = parseAnnotation(annotation);
                if (operationCommand != null) {
                    break;
                }
            }
            Objects.requireNonNull(operationCommand,
                    "Can not parse command from method:" + method);
            operationCommand.dispatchArgs(method, 0);
            if (record.apiOwnedInterceptor != null) {
                chain = new InterceptorChain(mChainHead, record.apiOwnedInterceptor);
            } else {
                chain = mChainHead;
            }
        }

        private MethodHandler(Class<?> clazz, boolean injectOrApply) {
            if (injectOrApply) {
                operationCommand = parseInjectClass(null, clazz, false);
            } else {
                operationCommand = parseApplyClass(null, clazz);
            }
            Objects.requireNonNull(operationCommand,
                    "Can not parse command from class:" + clazz);
//            ConverterStore store = scope != null ? scope.store : mConverterStore;
//            operationCommand.dispatchSetConverterStore(store);
            operationCommand.dispatchArgs(null, 0);
            chain = mChainHead;
        }

        private CommandImpl parseAnnotation(Annotation annotation) {
            return parseAnnotation(method.getDeclaringClass(), method.getReturnType(),
                    annotation, FLAG_PARSE_ALL);
        }

        private CommandImpl parseAnnotation(Class<?> areaScope, Class<?> targetType, Annotation annotation, int flag) {
            CommandImpl command = null;
            if ((flag & FLAG_PARSE_SET) != 0 && annotation instanceof Set) {
                command = new CommandSet();
                command.init(areaScope, annotation);
            } /*else if ((flag & FLAG_PARSE_SET) != 0 && annotation instanceof MultiSet) {
                MultiSet multiSet = (MultiSet) annotation;
                CommandGroup group = new CommandGroup();
                for (Set set : multiSet.set()) {
                    group.childrenCommand.add(parseAnnotation(areaScope, targetType, set, flag));
                }
                command = group;
            }*/ else if ((flag & FLAG_PARSE_GET) != 0 && annotation instanceof Get) {
                command = new CommandGet();
                command.init(areaScope, annotation);
            } else if ((flag & FLAG_PARSE_TRACK) != 0 && annotation instanceof Track) {
                command = new CommandTrack();
                command.init(areaScope, annotation);
            } else if ((flag & FLAG_PARSE_INJECT) != 0 && annotation instanceof Inject) {
                for (int i = 0; i < method.getParameterCount(); i++) {
                    Class<?> clazz = method.getParameterTypes()[i];
                    if (!clazz.isPrimitive() && !clazz.isArray() && clazz != String.class) {
                        command = parseInjectClass(areaScope, clazz, clazz == targetType);
                        break;
                    }
                }
                if (targetType != void.class) {
                    if (command != null) {
                        throw new CarRetrofitException("Inject method with input parameter must return void type");
                    }
                    command = parseInjectClass(areaScope, targetType, true);
                }
            } else if ((flag & FLAG_PARSE_APPLY) != 0 && annotation instanceof Apply) {
                if (targetType != void.class) {
                    throw new CarRetrofitException("Can not have any return type with Apply");
                }
                for (int i = 0; i < method.getParameterCount(); i++) {
                    Class<?> clazz = method.getParameterTypes()[i];
                    if (!clazz.isPrimitive() && !clazz.isArray() && clazz != String.class) {
                        command = parseApplyClass(areaScope, clazz);
                        break;
                    }
                }
            }
            return command;
        }

        private CommandImpl parseInjectClass(Class<?> parentAreaScope, Class<?> targetClass, boolean doReturn) {
            if (targetClass.isPrimitive() || targetClass.isArray() || targetClass == String.class) {
                throw new CarRetrofitException("Can not use Inject on class type:" + targetClass);
            }

            CommandInject injectCommand = new CommandInject(targetClass, doReturn);
            Class<?> loopClass = targetClass;
            do {
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
                            childCommand = parseInjectClass(parentAreaScope, field.getType(), true);
                        } else if (fieldAnnotation instanceof Get || fieldAnnotation instanceof Track) {
                            if (Modifier.isFinal(modifiers)) {
                                throw new CarRetrofitException("Can not use Set or Track command on a final field");
                            }
                            Class<?> areaScopeClass;
                            if (targetClass.isAnnotationPresent(CarApi.class)) {
                                areaScopeClass = targetClass;
                            } else {
                                areaScopeClass = parentAreaScope;
                            }
                            childCommand = parseAnnotation(areaScopeClass, field.getType(),
                                    fieldAnnotation, FLAG_PARSE_GET | FLAG_PARSE_TRACK);
                        }
                        if (childCommand != null) {
                            injectCommand.childrenCommand.add(childCommand);
                            field.setAccessible(true);
                            injectCommand.childrenField.add(field);
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

        private CommandImpl parseApplyClass(Class<?> parentAreaScope, Class<?> targetClass) {
            if (targetClass.isPrimitive() || targetClass.isArray() || targetClass == String.class) {
                throw new CarRetrofitException("Can not use Apply on class type:" + targetClass);
            }

            CommandApply applyCommand = new CommandApply(targetClass);
            Class<?> loopClass = targetClass;
            do {
                for (Field field : loopClass.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers)) {
                        continue;
                    }
                    CommandImpl childCommand = null;
                    for (Annotation fieldAnnotation : field.getDeclaredAnnotations()) {
                        if (fieldAnnotation instanceof Apply) {
                            childCommand = parseApplyClass(parentAreaScope, field.getType());
                        } else if (fieldAnnotation instanceof Set) {
                            Class<?> areaScopeClass;
                            if (targetClass.isAnnotationPresent(CarApi.class)) {
                                areaScopeClass = targetClass;
                            } else {
                                areaScopeClass = parentAreaScope;
                            }
                            childCommand = parseAnnotation(areaScopeClass, field.getType(),
                                    fieldAnnotation, FLAG_PARSE_SET);
                        }
                        if (childCommand != null) {
                            applyCommand.childrenCommand.add(childCommand);
                            field.setAccessible(true);
                            applyCommand.childrenField.add(field);
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

        Object invoke(Object[] args) {
            try {
                return chain != null ? chain.doProcess(operationCommand, args)
                        : operationCommand.invoke(args);
            } catch (Throwable e) {
                throw new CarRetrofitException(e);
            }
        }

        @Override
        public String toString() {
            return "MethodHandler{" + "method=" + method + '}';
        }
    }

    private abstract class CommandImpl extends Command {
        int key;
        int area;
        DataSource source;

        void init(Class<?> apiScope, Annotation annotation) {
            CarApi carApi = Objects.requireNonNull(apiScope.getAnnotation(CarApi.class));
            this.source = Objects.requireNonNull(mDataMap.get(carApi.scope()));
        }

        void resolveArea(Class<?> apiScope, int userDeclaredArea) {
            if (userDeclaredArea != CarApi.DEFAULT_AREA_ID) {
                this.area = userDeclaredArea;
            } else {
                CarApi carApi = Objects.requireNonNull(apiScope.getAnnotation(CarApi.class));
                if (carApi.area() != CarApi.DEFAULT_AREA_ID) {
                    this.area = carApi.area();
                } else {
                    this.area = CarApi.GLOBAL_AREA_ID;
                }
            }
        }

        void dispatchArgs(Field field) {
        }

        int dispatchArgs(Method method, int index) {
            return index;
        }

        @Override
        public Method getMethod() {
            return null;
        }

        @Override
        public Field getField() {
            return null;
        }

        @Override
        public String getName() {
            if (getMethod() != null) {
                return getMethod().getName();
            } else if (getField() != null) {
                return getField().getName();
            } else {
                return null;
            }
        }

        @Override
        public int getKey() {
            return key;
        }

        @Override
        public int getArea() {
            return area;
        }

        @Override
        public boolean fromInject() {
            return false;
        }

        @Override
        public boolean fromApply() {
            return false;
        }
    }

    private class CommandApply extends CommandGroup {

        Class<?> targetClass;

        CommandApply(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public boolean fromApply() {
            return true;
        }

        @Override
        public Object invoke(Object[] args) throws Throwable {
            Object target = null;
            if (args != null) {
                for (Object arg : args) {
                    if (targetClass.isInstance(arg)) {
                        target = arg;
                    }
                }
            }

            if (target == null) {
                throw new NullPointerException("invalid target. class:"
                        + targetClass + " args:" + Arrays.toString(args));
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
                Command command = childrenCommand.get(i);
                Field field = childrenField.get(i);
                if (command instanceof CommandApply) {
                    try {
                        Object applyFrom = field.get(target);
                        if (applyFrom != null) {
                            command.invoke(new Object[]{applyFrom});
                        }
                    } catch (IllegalAccessException ie) {
                        throw new CarRetrofitException("Apply failed", ie);
                    }
                } else {
                    command.invoke(new Object[]{target});
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
    }

    private class CommandInject extends CommandGroup {

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
        public Object invoke(Object[] args) throws Throwable {
            Object target = null;
            if (args != null) {
                for (Object arg : args) {
                    if (targetClass.isInstance(arg)) {
                        target = arg;
                    }
                }
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
                        + targetClass + " args:" + Arrays.toString(args));
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
                Command command = childrenCommand.get(i);
                Field field = childrenField.get(i);
                try {
                    if (command instanceof CommandInject) {
                        Object injectTarget = field.get(target);
                        if (injectTarget != null) {
                            command.invoke(new Object[]{injectTarget});
                        } else {
                            field.set(target, command.invoke(null));
                        }
                    } else {
                        field.set(target, command.invoke(null));
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
    }

    private abstract class CommandGroup extends CommandImpl {
        ArrayList<CommandImpl> childrenCommand = new ArrayList<>();
        ArrayList<Field> childrenField = new ArrayList<>();
        Method method;
        Field field;

        @Override
        int dispatchArgs(Method method, int index) {
            this.method = method;
            for (int i = 0; i < childrenCommand.size(); i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                childCommand.dispatchArgs(childrenField.get(i));
            }
            return index;
        }

        @Override
        void dispatchArgs(Field field) {
            this.field = field;
            for (int i = 0; i < childrenCommand.size(); i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                childCommand.dispatchArgs(childrenField.get(i));
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

    private class CommandSet extends CommandImpl {
        BuildInValue buildInValue;
        Converter<Object, ?> argConverter;
        Field applyField;
        Method method;
        int argIndex = -1;

        @Override
        void init(Class<?> apiScope, Annotation annotation) {
            super.init(apiScope, annotation);
            Set set = (Set) annotation;
            key = set.key();
            buildInValue = BuildInValue.build(set.value());
            resolveArea(apiScope, set.area());
        }

        @Override
        void dispatchArgs(Field field) {
            applyField = field;
            resolveArgConverter(field.getType(), field);
        }

        @Override
        int dispatchArgs(Method method, int index) {
            this.method = method;
            if (buildInValue != null) {
                return index;
            }
            this.argIndex = index;
            Class<?>[] parameterArray = method.getParameterTypes();
            if (argIndex >= parameterArray.length) {
                throw new CarRetrofitException("method:" + method + " is lack of parameters:"
                        + Arrays.toString(parameterArray));
            }
            Class<?> userArgType = parameterArray[argIndex];
            resolveArgConverter(userArgType, method);
            return index + 1;
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
            Converter<?, ?> converter = ConverterStore.find(userArgClass, carArgClass, mConverterStore);
            if (converter != null) {
                argConverter = (Converter<Object, ?>) converter;
            }
        }

        Object collectArgs(Object[] args) throws Throwable {
            if (buildInValue != null) {
                return buildInValue.extractValue(source.extractValueType(key));
            }
            Object rawArg;
            if (applyField != null) {
                try {
                    rawArg = applyField.get(args[0]);
                } catch (IllegalAccessException ex) {
                    throw new CarRetrofitException("Can not access to parameter", ex);
                }
            } else {
                rawArg = argIndex > -1 ? args[argIndex] : null;
            }
            return argConverter != null && rawArg != null ? argConverter.convert(rawArg) : rawArg;
        }

        @Override
        public Object invoke(Object[] args) throws Throwable {
            source.set(key, area, collectArgs(args));
            return SKIP;
        }

        @Override
        public CommandType type() {
            return CommandType.SET;
        }

        @Override
        public boolean fromApply() {
            return applyField != null;
        }

        @Override
        public Field getField() {
            return applyField;
        }

        @Override
        public Method getMethod() {
            return method;
        }
    }

    private class CommandGet extends CommandImpl {

        Converter<Object, ?> resultConverter;
        CarType type;
        Field injectField;

        @Override
        void init(Class<?> apiScope, Annotation annotation) {
            super.init(apiScope, annotation);
            Get get = (Get) annotation;
            key = get.key();
            type = get.type();
            if (type == CarType.ALL) {
                throw new CarRetrofitException("Can not use type ALL mode in Get operation");
            }
            resolveArea(apiScope, get.area());
        }

        @Override
        void dispatchArgs(Field field) {
            injectField = field;
            resolveResultConverter(field.getType());
        }

        @Override
        int dispatchArgs(Method method, int index) {
            resolveResultConverter(method.getReturnType());
            return index;
        }

        private void resolveResultConverter(Class<?> userReturnClass) {
            Class<?> carReturnClass;
            try {
                carReturnClass = type == CarType.AVAILABILITY ?
                        boolean.class : source.extractValueType(key);
            } catch (Exception e) {
                throw new CarRetrofitException(e);
            }
            Converter<?, ?> converter = ConverterStore.find(carReturnClass, userReturnClass, mConverterStore);
            resultConverter = (Converter<Object, ?>) converter;
        }

        @Override
        public Object invoke(Object[] args) throws Throwable {
            Object obj = source.get(key, area, type);
            return resultConverter != null ? resultConverter.convert(obj) : obj;
        }

        @Override
        public CommandType type() {
            return CommandType.GET;
        }

        @Override
        public boolean fromInject() {
            return injectField != null;
        }

        @Override
        public Field getField() {
            return injectField;
        }
    }

    private class CommandTrack extends CommandImpl {

        int getKey;
        boolean stickyTrack;
        boolean stickyUseCache;
        Field injectField;
        CarType type;
        Converter<Flow<?>, ?> resultConverter;
        Converter<Object, ?> mapConverter;

        @Override
        void init(Class<?> apiScope, Annotation annotation) {
            super.init(apiScope, annotation);
            Track track = (Track) annotation;
            key = track.key();
            type = track.type();
            if (type == CarType.CONFIG) {
                throw new CarRetrofitException("Can not use type CONFIG mode in Track operation");
            }

            resolveArea(apiScope, track.area());

            Sticky sticky = track.sticky();
            if (!EMPTY_VALUE.equals(sticky.token())) {
                stickyTrack = true;
                stickyUseCache = sticky.useCache();
                getKey = sticky.get();
            }
        }

        @Override
        int dispatchArgs(Method method, int index) {
            resolveResultConverter(method.getReturnType());
            resolveMapConverter(method.getGenericReturnType(), method);
            return index;
        }

        @Override
        void dispatchArgs(Field field) {
            injectField = field;
            resolveResultConverter(field.getType());
            resolveMapConverter(field.getGenericType(), field);
        }

        @Override
        public boolean fromInject() {
            return injectField != null;
        }

        @Override
        public Field getField() {
            return injectField;
        }

        private void resolveResultConverter(Class<?> userReturnClass) {
            Converter<?, ?> converter = ConverterStore.find(Flow.class, userReturnClass, mConverterStore);
            resultConverter = (Converter<Flow<?>, ?>) converter;
        }

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
                    Converter<?, ?> converter = ConverterStore.find(carType, userTargetType, mConverterStore);
                    mapConverter = (Converter<Object, ?>) converter;
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object invoke(Object[] args) throws Throwable {
            Flow<CarPropertyValue<?>> flow = source.track(key, area);
            Flow<?> result = flow;
            boolean valueMapped = false;
            switch (type) {
                case VALUE:
                    result = new FlowWrapper<>(flow, CarPropertyValue::getValue,
                            mapConverter);
                    break;
                case AVAILABILITY:
                    result = new FlowWrapper<>(flow,
                            value -> value != null && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE,
                            mapConverter);
                    break;
                case ALL:
                    if (mapConverter != null) {
                        result = new FlowWrapper<>(flow, null,
                                (Function<CarPropertyValue<Object>, CarPropertyValue<Object>>) raw ->
                                        new CarPropertyValue<>(raw.getPropertyId(),
                                        raw.getAreaId(),
                                        raw.getStatus(),
                                        raw.getTimestamp(),
                                        mapConverter.apply(raw.getValue())));
                        valueMapped = true;
                    }
                    break;
            }
            if (stickyTrack) {
                result = new StickFlow<>(result, source, this);
            }
            Object obj = resultConverter != null ? resultConverter.convert(result) : result;
            return !valueMapped && mapConverter != null && resultConverter != null ?
                    ((ConverterMapper<Object, Object>)resultConverter).map(obj, mapConverter) : obj;
        }

        @Override
        public CommandType type() {
            return CommandType.TRACK;
        }
    }

    private static class FlowWrapper<FROM, MIDDLE, TO> implements Flow<TO>, Consumer<FROM> {

        protected Flow<FROM> base;
        private Function<FROM, MIDDLE> fromRaw2Mid;
        private Function<MIDDLE, TO> fromMid2Final;
        private ArrayList<Consumer<TO>> consumers = new ArrayList<>();

        private FlowWrapper(Flow<FROM> base,
                            Function<FROM, MIDDLE> fromRaw2Mid,
                            Function<MIDDLE, TO> fromMid2Final) {
            this.base = base;
            this.fromRaw2Mid = fromRaw2Mid;
            this.fromMid2Final = fromMid2Final;
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
                MIDDLE middle;
                if (fromRaw2Mid != null) {
                    middle = fromRaw2Mid.apply(value);
                } else {
                    middle = (MIDDLE) value;
                }
                TO obj;
                if (fromMid2Final != null) {
                    obj = fromMid2Final.apply(middle);
                } else {
                    obj = (TO) middle;
                }
                for (int i = 0; i < consumers.size(); i++) {
                    consumers.get(i).accept(obj);
                }
            }
        }
    }

    private static class StickFlow<T> extends FlowWrapper<T, T, T> implements StickyFlow<T> {
        private DataSource source;

        private T lastValue;
        private boolean valueReceived;
        private CommandTrack trackCommand;

        private StickFlow(Flow<T> base, DataSource source, CommandTrack trackCommand) {
            super(base, null, null);
            this.source = source;
            this.trackCommand = trackCommand;
        }

        @Override
        public void addObserver(Consumer<T> consumer) {
            super.addObserver(consumer);
            consumer.accept(get());
        }

        @Override
        public void accept(T value) {
            super.accept(value);
            if (trackCommand.stickyUseCache) {
                valueReceived = true;
                lastValue = value;
            }
        }

        @Override
        public T get() {
            T result;
            try {
                result = trackCommand.stickyUseCache && valueReceived ?
                        lastValue : source.get(trackCommand.getKey, trackCommand.area, trackCommand.type);
            } catch (Exception e) {
                throw new CarRetrofitException(e);
            }
            if (trackCommand.stickyUseCache && !valueReceived) {
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

        static BuildInValue build(Value value) {
            if (EMPTY_VALUE.equals(value.string())) {
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
