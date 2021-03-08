package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Context;
import com.liyangbin.cartrofit.annotation.MethodCategory;
import com.liyangbin.cartrofit.solution.SolutionProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

@SuppressWarnings("unchecked")
public final class ContextFactory {

    private static final HashMap<Class<?>, ApiRecord<?>> API_CACHE = new HashMap<>();

    private final HashMap<Class<? extends Annotation>, ArrayList<CartrofitContext<?>>> cachedContextMap = new HashMap<>();
    private final HashMap<Class<? extends Annotation>, Singleton> cachedContextProviderMap = new HashMap<>();
    private final HashMap<ApiRecord<?>, LoadedApi> apiCache = new HashMap<>();
    private final ContextFactory parentFactory;
    private final String where;

    public ContextFactory() {
        this(Cartrofit.DEFAULT_FACTORY, "user-create");
    }

    ContextFactory(String where) {
        this(null, where);
    }

    ContextFactory(ContextFactory parentFactory, String where) {
        this.parentFactory = parentFactory;
        this.where = where;
        add(EmptyContext.class, new DelegateContext());
    }

    static <T> ApiRecord<T> getApi(Class<T> apiClass) {
        return getApi(apiClass, false);
    }

    static <T> ApiRecord<T> getApi(Class<T> apiClass, boolean allowEmpty) {
        synchronized (ContextFactory.class) {
            ApiRecord<T> singletonRecord = (ApiRecord<T>) API_CACHE.get(apiClass);
            if (singletonRecord != null) {
                return singletonRecord;
            }

            Class<?> loopedClass = apiClass;
            do {
                Annotation[] annotations = loopedClass.getDeclaredAnnotations();
                for (Annotation annotation : annotations) {
                    if (annotation.annotationType().isAnnotationPresent(Context.class)) {
                        API_CACHE.put(apiClass, singletonRecord = new ApiRecord<>(annotation,
                                annotation.annotationType(), apiClass));
                        return singletonRecord;
                    }
                }
                loopedClass = loopedClass.getEnclosingClass();
            } while (loopedClass != null);

            if (allowEmpty) {
                singletonRecord = new ApiRecord<>(null, EmptyContext.class, apiClass);
                API_CACHE.put(apiClass, singletonRecord);
                return singletonRecord;
            }
            throw new CartrofitGrammarException("Failed to find valid context annotation from " + apiClass);
        }
    }

    static Class<?> findImplement(Class<?> clazz, Class<?> lookForTarget) {
        if (clazz == lookForTarget || !lookForTarget.isAssignableFrom(clazz)) {
            throw new CartrofitGrammarException("Invalid parameter:" + clazz);
        }
        return lookForTarget.isInterface() ? findInterfaceImplement(clazz, lookForTarget)
                : findClassImplement(clazz, lookForTarget);
    }

    private static Class<?> findInterfaceImplement(Class<?> clazz, Class<?> lookForTarget) {
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
                    Class<?> extendsBy = findInterfaceImplement(subIfClazz, lookForTarget);
                    if (extendsBy != null) {
                        return extendsBy;
                    }
                }
            }
        }
        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz != null && superClazz != Object.class) {
            return findInterfaceImplement(superClazz, lookForTarget);
        } else {
            return null;
        }
    }

    private static Class<?> findClassImplement(Class<?> clazz, Class<?> lookForTarget) {
        while (clazz.getSuperclass() != null
                && clazz.getSuperclass() != lookForTarget) {
            clazz = clazz.getSuperclass();
        }
        return clazz;
    }

    private static Class<? extends Annotation> findAnnotationFromContextObj(CartrofitContext<?> contextObj) {
        Class<?> implementBy = findImplement(contextObj.getClass(), CartrofitContext.class);
        Type genericSuper = implementBy.getGenericSuperclass();
        if (genericSuper instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericSuper;
            try {
                return (Class<? extends Annotation>) parameterizedType.getActualTypeArguments()[0];
            } catch (ClassCastException ignore) {
            }
        }
        throw new CartrofitGrammarException("Invalid contextObj " + contextObj);
    }

    @Context(singleton = true)
    private @interface EmptyContext {
    }

    private static Class<? extends Annotation> findAnnotationFromContextProvider(ContextProvider<?> provider) {
        Class<?> implementBy = findImplement(provider.getClass(), ContextProvider.class);
        Type[] genericIfTypes = implementBy.getGenericInterfaces();
        for (Type type : genericIfTypes) {
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                if (parameterizedType.getRawType() == ContextProvider.class) {
                    try {
                        return (Class<? extends Annotation>) parameterizedType.getActualTypeArguments()[0];
                    } catch (ClassCastException ignore) {
                    }
                }
            }
        }
        throw new CartrofitGrammarException("Invalid provider " + provider);
    }

    public <A extends Annotation> void add(CartrofitContext<A> contextObj) {
        add(findAnnotationFromContextObj(contextObj), contextObj);
    }

    <A extends Annotation> void add(Class<? extends Annotation> annotationType,
                                    CartrofitContext<A> contextObj) {
        Context context = annotationType.getDeclaredAnnotation(Context.class);
        if (context == null) {
            throw new CartrofitGrammarException("Can not find Context identifier on type "
                    + annotationType);
        }
        ArrayList<CartrofitContext<?>> contextArrayList = cachedContextMap.get(annotationType);
        if (contextArrayList == null) {
            contextArrayList = new ArrayList<>();
            cachedContextMap.put(annotationType, contextArrayList);
        } else if (context.singleton() && contextArrayList.size() > 1) {
            throw new CartrofitGrammarException("Can not add multiple context for singleton type "
                    + annotationType);
        }
        contextArrayList.add(contextObj);
    }

    public <A extends Annotation> void addLazily(ContextProvider<A> provider) {
        Class<? extends Annotation> annotationType = findAnnotationFromContextProvider(provider);
        Context context = annotationType.getDeclaredAnnotation(Context.class);
        if (context == null) {
            throw new CartrofitGrammarException("Can not find Context identifier on type "
                    + annotationType);
        }
        if (!context.singleton()) {
            throw new CartrofitGrammarException("Can not add multiple Context provider by type "
                    + annotationType);
        }
        cachedContextProviderMap.put(annotationType, new Singleton(provider));
    }

    private static class Singleton {
        private ContextProvider<?> initProvider;
        private CartrofitContext<?> instance;

        Singleton(ContextProvider<?> initProvider) {
            this.initProvider = initProvider;
        }

        CartrofitContext<?> get() {
            if (instance == null) {
                synchronized (this) {
                    if (instance == null) {
                        instance = Objects.requireNonNull(initProvider.provide());
                        initProvider = null;
                    }
                }
            }
            return instance;
        }
    }

    public interface ContextProvider<A extends Annotation> {
        CartrofitContext<A> provide();
    }

    public synchronized <T> T from(Class<T> apiClass) {
        return (T) getOrLoadApi(getApi(apiClass, true)).apiObj;
    }

    synchronized CartrofitContext<?> findContext(ApiRecord<?> record) {
        return getOrLoadApi(record).runtimeContext;
    }

    private LoadedApi getOrLoadApi(ApiRecord<?> record) {
        return getOrLoadApi(record, true);
    }

    private synchronized LoadedApi getOrLoadApi(ApiRecord<?> record, boolean throwIfNotFound) {
        LoadedApi api = apiCache.get(record);
        if (api != null) {
            return api;
        }
        ArrayList<CartrofitContext<?>> cachedContextList = cachedContextMap.get(record.scopeType);
        final int count = cachedContextList != null ? cachedContextList.size() : 0;
        for (int i = 0; i < count; i++) {
            final CartrofitContext<Annotation> context = (CartrofitContext<Annotation>) cachedContextList.get(i);
            api = createApi(context, record);
            if (api != null) {
                apiCache.put(record, api);
                return api;
            }
        }
        Singleton contextProvider = cachedContextProviderMap.get(record.scopeType);
        if (contextProvider != null) {
            CartrofitContext<Annotation> context = (CartrofitContext<Annotation>) contextProvider.get();
            api = createApi(context, record);
            if (api != null) {
                apiCache.put(record, api);
                return api;
            }
        }
        api = parentFactory != null ? parentFactory.getOrLoadApi(record, false) : null;
        if (api != null) {
            apiCache.put(record, api);
        } else if (throwIfNotFound) {
            throw new CartrofitGrammarException("Can not find valid context for " + record
                    + " from " + this);
        }
        return api;
    }

    private LoadedApi createApi(CartrofitContext<Annotation> context, ApiRecord<?> record) {
        if (!context.onApiCreate(record.scopeObj, record.clazz)) {
            return null;
        }
        LoadedApi api = new LoadedApi();
        context.attachRunningFactory(this);
        api.runtimeContext = context;
        api.apiObj = Proxy.newProxyInstance(record.clazz.getClassLoader(),
                new Class<?>[]{record.clazz},
                (proxy, method, args) -> {
                    if (method.isDefault()) {
                        throw new UnsupportedOperationException(
                                "Do not declare any default method in Cartrofit interface");
                    }
                    final ApiRecord<?> invokeRecord;
                    Class<?> declaringClass = method.getDeclaringClass();
                    CartrofitContext<?> runtimeContext;
                    if (record.clazz == declaringClass) {
                        invokeRecord = record;
                        runtimeContext = context;
                    } else {
                        invokeRecord = getApi(declaringClass, true);
                        runtimeContext = getOrLoadApi(invokeRecord).runtimeContext;
                    }
                    if (declaringClass == Object.class) {
                        return method.invoke(invokeRecord, args);
                    }
                    Call call;
                    Key key = new Key(invokeRecord, method, false);
                    synchronized (ContextFactory.this) {
                        call = runtimeContext.getOrCreateCall(key, MethodCategory.CATEGORY_ALL, true);
                    }
                    return call.exceptionalInvoke(args);
                });
        return api;
    }

    @Override
    public String toString() {
        return "ContextFactory{0x" + Integer.toHexString(hashCode())
                + ", from='" + where + '\'' + '}';
    }

    private static class LoadedApi {
        Object apiObj;
        CartrofitContext<?> runtimeContext;
    }

    private final class DelegateContext extends CartrofitContext<EmptyContext> {

        @Override
        public SolutionProvider onProvideCallSolution() {
            return null;
        }

        @Override
        public Call onCreateCall(Key key, int category) {
            Call call = super.onCreateCall(key, category);
            if (call == null) {
                throw new CartrofitGrammarException("Must specify type annotation on "
                        + key.record.clazz);
            }
            return call;
        }

        @Override
        public String toString() {
            return "DelegateContext in " + ContextFactory.this;
        }
    }
}
