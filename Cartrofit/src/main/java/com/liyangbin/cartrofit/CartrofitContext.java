package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.MethodCategory;
import com.liyangbin.cartrofit.annotation.OnComplete;
import com.liyangbin.cartrofit.annotation.OnError;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Token;
import com.liyangbin.cartrofit.annotation.Unregister;
import com.liyangbin.cartrofit.solution.CallProvider2;
import com.liyangbin.cartrofit.solution.SolutionProvider;
import com.liyangbin.cartrofit.support.SupportUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public abstract class CartrofitContext {

    private static final Router ID_ROUTER = new Router();
    private static final SolutionProvider ROOT_PROVIDER = new SolutionProvider();

    static {
        SupportUtil.addSupport();

        ROOT_PROVIDER.create(Register.class, RegisterCall.class)
                .provide(new CallProvider2<Register, RegisterCall>() {
                    @Override
                    public RegisterCall provide(CartrofitContext context, int category, Register annotation, Key key) {
                        Parameter callbackParameter = key.findParameterByAnnotation(Callback.class);
                        if (callbackParameter == null && key.isImplicitCallbackParameterPresent()) {
                            callbackParameter = key.getParameterAt(0);
                        }
                        if (callbackParameter == null) {
                            throw new CartrofitGrammarException("Declare a Callback parameter as an interface " + key);
                        }
                        final RegisterCall registerCall = new RegisterCall();
                        context.inflateCallback(key, callbackParameter.getType(), MethodCategory.CATEGORY_TRACK,
                                registerCall::addChildCall);
                        if (registerCall.getChildCount() == 0) {
                            throw new CartrofitGrammarException("Failed to resolve callback entry point in "
                                    + callbackParameter.getType());
                        }
                        return registerCall;
                    }
                });

        ROOT_PROVIDER.create(Unregister.class, UnregisterCall.class)
                .provide(new CallProvider2<Unregister, UnregisterCall>() {
                    @Override
                    public UnregisterCall provide(CartrofitContext context, int category, Unregister unregister, Key key) {
                        int count = key.getParameterCount();
                        if (count != 1) {
                            throw new CartrofitGrammarException("Unregister must provide a single" +
                                    " one Callback parameter:" + key);
                        }
                        RegisterCall registerCall = (RegisterCall) context.getOrCreateCallById(key,
                                unregister.value(), MethodCategory.CATEGORY_DEFAULT);
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
                    }
                });

        ROOT_PROVIDER.create(Delegate.class, Call.class)
                .provide(new CallProvider2<Delegate, Call>() {
                    @Override
                    public Call provide(CartrofitContext context, int category, Delegate delegate, Key key) {
                        return context.createDelegateCallById(key, delegate.value(), category);
                    }
                });
    }

    <INPUT> Function<Object[], INPUT> findInputConverter(FixedTypeCall<INPUT, ?> call) {
        return mSolutionProvider.findInputConverter(call);
    }

    <OUTPUT> Function<OUTPUT, ?> findReturnOutputConverter(FixedTypeCall<?, OUTPUT> call) {
        return mSolutionProvider.findReturnOutputConverter(call);
    }

    <OUTPUT> Function<OUTPUT, Object[]> findCallbackOutputConverter(FixedTypeCall<?, OUTPUT> call) {
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

    public static Class<?> boxTypeOf(Class<?> primitive) {
        if (!primitive.isPrimitive()) {
            return primitive;
        }
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
    private final HashMap<ApiRecord<?>, Object> mApiCache = new HashMap<>();
    private final HashMap<Key, Call> mCallCache = new HashMap<>();
    private ArrayList<ExceptionHandler<?>> mExceptionHandlers;
    private boolean mCatchExceptionByDefault = true;

    public CartrofitContext() {
        final SolutionProvider solutionProvider = onProvideCallSolution();
        if (solutionProvider == null) {
            throw new CartrofitGrammarException("Sub-class must return a valid solution provider");
        }
        mSolutionProvider = ROOT_PROVIDER.merge(solutionProvider);
    }

    public void addExceptionHandler(ExceptionHandler<?> handler) {
        if (mExceptionHandlers == null) {
            mExceptionHandlers = new ArrayList<>();
        }
        mExceptionHandlers.add(handler);
    }

    public void setIsCatchExceptionByDefault(boolean catchExceptionByDefault) {
        mCatchExceptionByDefault = catchExceptionByDefault;
    }

    public void onApiCreated(Annotation annotation, Class<?> apiType) {
    }

    public <T> T from(Class<T> api) {
        return from(Cartrofit.getApi(api));
    }

    synchronized <T> T from(ApiRecord<T> record) {
        T apiObj = (T) mApiCache.get(record);
        if (apiObj != null) {
            return apiObj;
        }
        onApiCreated(record.scopeObj, record.clazz);
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
                    synchronized (CartrofitContext.this) {
                        call = getOrCreateCall(key, MethodCategory.CATEGORY_DEFAULT, true);
                    }
                    return call.exceptionalInvoke(args);
                });
        mApiCache.put(record, apiObj);
        return apiObj;
    }

    public Call getOrCreateCall(Key key, int category, boolean fromCache) {
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
            ArrayList<ExceptionHandler<?>> callExceptionHandlers = new ArrayList<>();
            if (mExceptionHandlers != null && mExceptionHandlers.size() > 0) {
                for (int i = 0; i < mExceptionHandlers.size(); i++) {
                    ExceptionHandler<?> handler = mExceptionHandlers.get(i);
                    if (!key.isExceptionDeclared(handler.getType())) {
                        callExceptionHandlers.add(handler);
                    }
                }
            } else if (mCatchExceptionByDefault && !key.isCallbackEntry
                    && !key.isExceptionDeclared(Throwable.class)) {
                callExceptionHandlers.add(ExceptionHandler.ALL);
            }

            RegisterCall wrappedCall = transformTrack2RegisterIfNeeded(call, key);
            if (wrappedCall != null) {
                wrappedCall.attach(key, this, callExceptionHandlers);
                call.attach(wrappedCall.getColdTrackKey(), this, null);
                call = wrappedCall;
            } else {
                call.attach(key, this, callExceptionHandlers);
            }

            Token tokenAnnotation = key.getAnnotation(Token.class);
            if (tokenAnnotation != null) {
                call.setTokenList(tokenAnnotation);
            }
        }
        return call;
    }

    public Object onInterceptCallInvocation(Call call, Object[] parameter) throws Throwable {
        return Cartrofit.SKIP;
    }

    public final void inflateCallback(Key key, Class<?> callbackClass,
                                      int category, Consumer<Call> resultReceiver) {
        ArrayList<Key> childKeys = getChildKey(key, callbackClass);
        for (int i = 0; i < childKeys.size(); i++) {
            Key childKey = childKeys.get(i);
            if (childKey.isAnnotationPresent(OnError.class)
                    || childKey.isAnnotationPresent(OnComplete.class)) {
                // TODO: Support OnError
                throw new CartrofitGrammarException("Can not declare OnError or OnComplete "
                        + "in Register grammar " + childKey
                        + ". Consider adding callback in your track method");
            }
            Call call = onCreateCall(childKey, category);
            if (call != null) {
                resultReceiver.accept(call);
            }
        }
    }

    public FlowConverter<?> findFlowConverter(Call call) {
        if (call.getKey().isCallbackEntry) {
            return null;
        }
        return Cartrofit.findFlowConverter(call.getKey().getReturnType());
    }

    public ArrayList<Key> getChildKey(Key parent, Class<?> callbackClass) {
        return parent.record.getChildKey(callbackClass);
    }

    public Executor getSubscribeExecutor(String tag) {
        return null;
    }

    public Executor getConsumeExecutor(String tag) {
        return null;
    }

    public abstract SolutionProvider onProvideCallSolution();

    private RegisterCall transformTrack2RegisterIfNeeded(Call call, Key originalKey) {
        if (!call.hasCategory(MethodCategory.CATEGORY_TRACK) || originalKey.isCallbackEntry) {
            return null;
        }
        Parameter callbackParameter = originalKey.findParameterByAnnotation(Callback.class);
        if (callbackParameter == null) {
            if (originalKey.isImplicitCallbackParameterPresent()) {
                callbackParameter = originalKey.getParameterAt(0);
            } else if (originalKey.getReturnType() == void.class) {
                throw new CartrofitGrammarException("Must provide a callback parameter with Callback annotation "
                        + originalKey);
            } else {
                return null;
            }
        } else if (!callbackParameter.getType().isInterface()) {
            throw new CartrofitGrammarException("Must provide a callback parameter declared by interface "
                    + originalKey);
        }

        Class<?> callbackType = callbackParameter.getType();
        ArrayList<Key> childrenKey = getChildKey(originalKey, callbackType);
        HashMap<Class<?>, Key> errorKeyMap = null;
        Key completeKey = null;
        Key callbackKey = null;
        for (int i = 0; i < childrenKey.size(); i++) {
            Key entryKey = childrenKey.get(i);
            if (entryKey.isAnnotationPresent(OnError.class)) {
                int count = entryKey.getParameterCount();
                Class<?> declaredType;
                if (count != 1 || !Throwable.class.isAssignableFrom(
                        declaredType = entryKey.getParameterAt(0).getType())) {
                    throw new CartrofitGrammarException("OnError method can only declare one parameter" +
                            " by Throwable type " + entryKey);
                }
                if (errorKeyMap == null) {
                    errorKeyMap = new HashMap<>();
                }
                errorKeyMap.put(declaredType, entryKey);
            } else if (entryKey.isAnnotationPresent(OnComplete.class)) {
                if (completeKey != null) {
                    throw new CartrofitGrammarException("Duplicate OnComplete declaration " + entryKey);
                }
                completeKey = entryKey;
                if (entryKey.getParameterCount() != 0) {
                    throw new CartrofitGrammarException("OnComplete method can not declare any parameters "
                            + entryKey);
                }
            } else if (entryKey.isAnnotationPresent(Callback.class)) {
                if (callbackKey != null) {
                    throw new CartrofitGrammarException("Duplicate Callback declaration " + entryKey);
                }
                callbackKey = entryKey;
            }
        }
        if (callbackKey == null && childrenKey.size() == 1) {
            callbackKey = childrenKey.get(0);
        }
        if (callbackKey == null) {
            throw new CartrofitGrammarException("Must provide a method with Callback annotation in " + callbackType);
        }
        return new RegisterCall(call, callbackKey, errorKeyMap, completeKey);
    }
}
