package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Inject;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Timeout;
import com.liyangbin.cartrofit.annotation.Token;
import com.liyangbin.cartrofit.annotation.Unregister;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.call.InjectCall;
import com.liyangbin.cartrofit.call.InjectGroupCall;
import com.liyangbin.cartrofit.call.RegisterCall;
import com.liyangbin.cartrofit.call.UnregisterCall;
import com.liyangbin.cartrofit.funtion.FlowConverter;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public abstract class AbsContext {

    public static final int CATEGORY_SET = 1;
    public static final int CATEGORY_GET = 1 << 1;
    public static final int CATEGORY_TRACK = 1 << 2;

    public static final int CATEGORY_DEFAULT = 0xffffffff;

    static final Router ID_ROUTER = new Router();
    private static final SolutionProvider ROOT_PROVIDER = new SolutionProvider();

    static {
        RxJavaConverter.addSupport();
        ObservableConverter.addSupport();
        LiveDataConverter.addSupport();

        ROOT_PROVIDER.create(Inject.class)
                .provide((context, category, inject, key) -> context.createInjectCommand(key));

        // TODO: delete
        /*builder.create(Combine.class)
                .checkParameter((combine, key) -> {
                    if (combine.elements().length <= 1) {
                        throw new CartrofitGrammarException("Must declare more than one element on Combine:"
                                + key + " elements:" + Arrays.toString(combine.elements()));
                    }
                    for (int i = 0; i < key.getParameterCount(); i++) {
                        if (!key.isAnnotationPresent(Bind.class)) {
                            throw new CartrofitGrammarException("Parameter declared by " + key
                                    + " must be annotated by " + Bind.class);
                        }
                    }
                })
                .provide((category, combine, key) -> {
                    CombineCall combineCall = new CombineCall();
                    for (int element : combine.elements()) {
                        combineCall.addChildCall(getOrCreateCallById(key, element,
                                category & (CATEGORY_TRACK | CATEGORY_GET)));
                    }
                    return combineCall;
                });*/

        ROOT_PROVIDER.create(Register.class)
                .provide((context, category, register, key) -> {
                    if (key.getParameterCount() == 0) {
                        throw new CartrofitGrammarException("Register must declare a Callback parameter " + key);
                    }
                    if (key.getParameterCount() == 1) {
                        if (!key.getParameterAt(0).getType().isInterface()) {
                            throw new CartrofitGrammarException("Declare a Callback parameter as an interface " + key);
                        }
                    } else {
                        Parameter callbackParameter = key.findParameterByAnnotation(Callback.class);
                        if (callbackParameter == null) {
                            throw new CartrofitGrammarException("Declare a Callback parameter as an interface " + key);
                        }
                    }
                    Parameter callbackParameter = key.findParameterByAnnotation(Callback.class);
                    if (callbackParameter == null) {
                        callbackParameter = key.getParameterAt(0);
                    }
                    final RegisterCall registerCall = new RegisterCall();
                    context.inflateCallback(key, callbackParameter.getType(), CATEGORY_TRACK,
                            call -> {
                                Call returnCall = null/*createChildCall(key, CATEGORY_SET)TODO: delete?*/;
                                Call parameterCall = null/* TODO: createInjectCommand(key) requirement assessment */;
                                registerCall.addChildCall(call, returnCall, parameterCall);
                            });
                    if (registerCall.getChildCount() == 0) {
                        throw new CartrofitGrammarException("Failed to resolve callback entry point in "
                                + callbackParameter.getType());
                    }
                    return registerCall;
                });

        ROOT_PROVIDER.create(Unregister.class)
                .provide((context, category, unregister, key) -> {
                    int count = key.getParameterCount();
                    if (count != 1) {
                        throw new CartrofitGrammarException("Unregister must provide a single" +
                                " one Callback parameter:" + key);
                    }
                    RegisterCall registerCall = (RegisterCall) context.getOrCreateCallById(key,
                            unregister.value(), CATEGORY_DEFAULT);
                    Key registerKey = registerCall.getKey();
                    if (registerKey.getParameterCount() == 1) {
                        if (key.getParameterAt(0).getType() != registerKey.getParameterAt(0).getType()) {
                            throw new CartrofitGrammarException("Unregister must provide a single " +
                                    "one Callback parameter under the same Callback type as well as key:" + key + " does");
                        }
                    } else {
                        for (int i = 0; i < registerKey.getParameterCount(); i++) {
                            Parameter parameter = registerKey.getParameterAt(i);
                            if (parameter.isAnnotationPresent(Callback.class)) {
                                if (key.getParameterAt(0).getType() != parameter.getType()) {
                                    throw new CartrofitGrammarException("Unregister must provide a single " +
                                            "one Callback parameter under the same Callback type as well as key:" + key + " does");
                                }
                            }
                        }
                    }
                    return new UnregisterCall(registerCall);
                });

        ROOT_PROVIDER.create(Delegate.class)
                .provide((context, category, delegate, key) -> context.createDelegateCallById(key, delegate.value(), category));
    }

    <INPUT> Function<Union, INPUT> findInputConverter(FixedTypeCall<INPUT, ?> call) {
        return mSolutionProvider.findInputConverter(call);
    }

    <OUTPUT> Function<OUTPUT, ?> findReturnOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        return mSolutionProvider.findReturnOutputConverter(call);
    }

    <OUTPUT> Function<OUTPUT, Union> findCallbackOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        return mSolutionProvider.findCallbackOutputConverter(call);
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

    public static boolean classEquals(Class<?> a, Class<?> b) {
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

    private final SolutionProvider mSolutionProvider;
    private boolean mIsDefaultSingleton;
    private final HashMap<ApiRecord<?>, Object> mApiCache = new HashMap<>();
    private final HashMap<Key, Call> mCallCache = new HashMap<>();

    public AbsContext() {
        mSolutionProvider = onProvideCallSolution();
    }

    public <T> T from(Class<T> api) {
        return from(Cartrofit.getApi(api));
    }

    void createFromCartrofit() {
        mIsDefaultSingleton = true;
    }

    protected final boolean isDefaultSingleton() {
        return mIsDefaultSingleton;
    }

    synchronized <T> T from(ApiRecord<T> record) {
        T apiObj = (T) mApiCache.get(record);
        if (apiObj != null) {
            return apiObj;
        }
        apiObj = (T) Proxy.newProxyInstance(record.clazz.getClassLoader(),
                new Class<?>[]{record.clazz},
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
                    synchronized (AbsContext.this) {
                        call = getOrCreateCall(key, CATEGORY_DEFAULT, true);
                    }
                    return call.invoke(Union.ofArray(args));
                });
        mApiCache.put(record, apiObj);
        return apiObj;
    }

    Call getOrCreateCall(Key key, int category, boolean fromCache) {
        Call call;
        if (fromCache) {
            call = mCallCache.get(key);
            if (call == null) {
                call = onCreateCall(key, category);
                mCallCache.put(key, call);
                call.dispatchInit(call.getParameterContext());
            }
        } else {
            call = onCreateCall(key, category);
            call.dispatchInit(call.getParameterContext());
        }
        return call;
    }

    Call getOrCreateCallById(Key key, ApiRecord<?> record, int id, int flag, boolean fromDelegate) {
        Method method = record.findMethodById(id);
        if (method == null) {
            Class<?> apiClass = ID_ROUTER.findApiClassById(id);
            if (apiClass == record.clazz || apiClass == null) {
                throw new CartrofitGrammarException("Can not find target Id:" + id + " from:" + record.clazz);
            }
            return getOrCreateCallById(key, Cartrofit.getApi(apiClass), id, flag, fromDelegate);
        }
        Key targetKey = new Key(record, method, false);
        Call call;
        if (fromDelegate) {
            key.setDelegateKey(targetKey);
            call = getOrCreateCall(key, flag, false);
        } else {
            call = getOrCreateCall(targetKey, flag, true);
        }
        return call;
    }

    public Call getOrCreateCallById(Key key, int id, int category) {
        return getOrCreateCallById(key, key.record, id, category, false);
    }

    Call createDelegateCallById(Key key, int id, int category) {
        return getOrCreateCallById(key, key.record, id, category, true);
    }

    public Call onCreateCall(Key key, int category) {
        Call call = mSolutionProvider.createCall(this, key, category);
        if (call != null) {
            call.setKey(key, this);

            Token tokenAnnotation = key.getAnnotation(Token.class);
            if (tokenAnnotation != null) {
                call.setTokenList(tokenAnnotation);
            }

            Call wrappedCall = wrapNormalTrack2RegisterIfNeeded(call);
            if (wrappedCall != null) {
                wrappedCall.setKey(key, this);
                return wrappedCall;
            }
        }
        return call;
    }

    public final void inflateCallback(Key key, Class<?> callbackClass,
                                      int category, Consumer<Call> resultReceiver) {
        ArrayList<Key> childKeys = getChildKey(key, callbackClass);
        for (int i = 0; i < childKeys.size(); i++) {
            Key childKey = childKeys.get(i);
            Call call = getOrCreateCall(childKey, category, false);
            resultReceiver.accept(call);
        }
    }

    public FlowConverter<?> findFlowConverter(Call call) {
        if (call.getKey().isCallbackEntry) {
            return null;
        }
        return Cartrofit.findFlowConverter(call.getKey().getReturnType());
    }

    public ArrayList<Key> getChildKey(Key parent, Class<?> callbackClass) {
        return Cartrofit.getApi(callbackClass).getChildKey(parent);
    }

    public Executor getSubscribeExecutor(String tag) {
        return null;
    }

    public Executor getConsumeExecutor(String tag) {
        return null;
    }

    public SolutionProvider onProvideCallSolution() {
        return ROOT_PROVIDER;
    }

    private Call wrapNormalTrack2RegisterIfNeeded(Call call) {
        if (call instanceof RegisterCall || !call.hasCategory(CATEGORY_TRACK)) {
            return null;
        }
        Parameter callbackParameter = call.getKey().findParameterByAnnotation(Callback.class);
        if (callbackParameter == null) {
            return null;
        }

        ArrayList<Key> childrenKey = getChildKey(call.getKey(), callbackParameter.getType());
        Key trackKey = null;
        Key timeoutKey = null;
        for (int i = 0; i < childrenKey.size(); i++) {
            Key entryKey = childrenKey.get(i);
            if (entryKey.isAnnotationPresent(Callback.class)) {
                trackKey = entryKey;
            } else if (entryKey.isAnnotationPresent(Timeout.class)) {
                timeoutKey = entryKey;
            }
            if (trackKey != null && timeoutKey != null) {
                break;
            }
        }
        if (trackKey == null) {
            throw new CartrofitGrammarException("Must provide a method with unregister annotation");
        }
        return new RegisterCall(call, trackKey, timeoutKey);
    }

    private Call createInjectCommand(Key key) {
        if (key.method != null) {
            InjectGroupCall injectGroupCall = null;
            final int parameterCount = key.getParameterCount();
            for (int i = 0; i < parameterCount; i++) {
                Parameter parameter = key.getParameterAt(i);
                boolean inDeclared = !key.isCallbackEntry && parameter.isAnnotationPresent(In.class);
                boolean outDeclared = parameter.isAnnotationPresent(Out.class);

                if (inDeclared || outDeclared) {
                    Class<?> targetClass = parameter.getType();
                    InjectCall injectCall = createInjectCallByClass(key, targetClass);

                    if (injectGroupCall == null) {
                        injectGroupCall = new InjectGroupCall(parameterCount);
                    }

                    if (key.isCallbackEntry) {
                        injectGroupCall.addChildInjectCall(i, injectCall, true, false);
                    } else {
                        injectGroupCall.addChildInjectCall(i, injectCall, inDeclared, outDeclared);
                    }
                }
            }
            if (injectGroupCall == null) {
                throw new CartrofitGrammarException("Must provide In or Out Annotation " + key);
            }
            return injectGroupCall;
        } else if (key.field != null) {
            return createInjectCallByClass(key, key.field.getType());
        } else {
            throw new RuntimeException("impossible condition key:" + key);
        }
    }

    private InjectCall createInjectCallByClass(Key parentKey, Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray() || clazz == String.class) {
            throw new CartrofitGrammarException("Can not use Inject operator on class type:" + clazz);
        }
        InjectCall injectCall = new InjectCall(clazz);
        inflateCallback(parentKey, clazz, CATEGORY_SET | CATEGORY_GET | CATEGORY_TRACK,
                call -> {
                    if (call.hasCategory(CATEGORY_SET)
                            && Modifier.isFinal(call.getKey().field.getModifiers())) {
                        throw new CartrofitGrammarException("Invalid final key:" + call.getKey());
                    }
                    injectCall.addChildCall(call);
                });
        if (injectCall.getChildCount() == 0) {
            throw new CartrofitGrammarException("Failed to parse Inject call from type:"
                    + clazz);
        }
        return injectCall;
    }
}
