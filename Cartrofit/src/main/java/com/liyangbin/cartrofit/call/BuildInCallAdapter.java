package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.CallAdapter;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.ConverterFactory;
import com.liyangbin.cartrofit.annotation.Combine;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Inject;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Unregister;

import java.util.Arrays;
import java.util.HashMap;

public class BuildInCallAdapter extends CallAdapter {

    private static final Object STABLE_SCOPE = new Object();
    private final HashMap<Class<?>, InjectCall> mInjectCallCache = new HashMap<>();

    @Override
    public Object extractScope(Class<?> scopeClass, ConverterFactory factory) {
        return STABLE_SCOPE;
    }

    @Override
    public void onProvideCallSolution(CallSolutionBuilder builder) {
        builder.create(Unregister.class)
                .takeIfEqual(CATEGORY_DEFAULT)
                .provide(new CallProvider<Unregister>() {
                    @Override
                    public Call provide(int category, Unregister unregister, Cartrofit.Key key) {
                        UnregisterCall unregisterCall = new UnregisterCall();
                        unregisterCall.setRegisterCall((RegisterCall) inflateByIdIfThrow(key,
                                unregister.value(), CATEGORY_TRACK));
                        return unregisterCall;
                    }
                });

        builder.create(Inject.class)
                .takeIfEqual(CATEGORY_DEFAULT)
                .checkParameterIncluded(In.class, Out.class)
                .provide(new CallProvider<Inject>() {
                    @Override
                    public Call provide(int category, Inject inject, Cartrofit.Key key) {
                        return createInjectCommand(this, key);
                    }
                });

        builder.create(Combine.class)
                .takeIfContains(CATEGORY_TRACK | CATEGORY_GET)
                .provide(new CallProvider<Combine>() {
                    @Override
                    public Call provide(int category, Combine combine, Cartrofit.Key key) {
                        CombineCall combineCall = new CombineCall();
                        int[] elements = combine.elements();
                        if (elements.length <= 1) {
                            throw new CartrofitGrammarException("Must declare more than one element on Combine:"
                                    + key + " elements:" + Arrays.toString(elements));
                        }
                        for (int element : elements) {
                            Call childCall = inflateByIdIfThrow(key, element,
                                    category & (CATEGORY_TRACK | CATEGORY_GET));
                            combineCall.addChildCall(childCall);
                        }
                        return combineCall;
                    }
                });

        builder.create(Register.class)
                .takeIfEqual(CATEGORY_DEFAULT)
                .provide(new CallProvider<Register>() {
                    @Override
                    public Call provide(int category, Register register, Cartrofit.Key key) {
                        Class<?> targetClass = key.getSetClass();
                        if (!targetClass.isInterface()) {
                            throw new CartrofitGrammarException("Declare CarCallback parameter as an interface:" + targetClass);
                        }
                        final RegisterCall registerCall = new RegisterCall();
                        inflateCallback(targetClass, CATEGORY_TRACK, call -> {
                            Call returnCall = reInflate(call.key, CATEGORY_SET);
                            Call parameterCall = createInjectCommand(this, key);
                            registerCall.addChildCall(call, returnCall, parameterCall);
                        });
                        if (registerCall.getChildCount() == 0) {
                            throw new CartrofitGrammarException("Failed to resolve callback entry point in " + targetClass);
                        }
                        return registerCall;
                    }
                });

        builder.create(Delegate.class)
                .takeIfAny()
                .provide(new CallProvider<Delegate>() {
                    @Override
                    public Call provide(int category, Delegate delegate, Cartrofit.Key key) {
                        Call delegateTarget = inflateById(key, delegate.value(), category);
                        return delegateTarget != null ? new DelegateCall(delegateTarget) : null;
                    }
                });
    }

    private Call createInjectCommand(CallProvider<?> provider, Cartrofit.Key key) {
        if (key.method != null) {
            InjectGroupCall injectGroupCall = null;
            final int parameterCount = key.getParameterCount();
            for (int i = 0; i < parameterCount; i++) {
                Cartrofit.Parameter parameter = key.getParameterAt(i);
                boolean inDeclared = !key.isCallbackEntry && parameter.isAnnotationPresent(In.class);
                boolean outDeclared = parameter.isAnnotationPresent(Out.class);

                if (inDeclared || outDeclared) {
                    Class<?> targetClass = parameter.getType();
                    InjectCall injectCall = getOrCreateInjectCallByClass(provider, targetClass);

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
            return injectGroupCall;
        } else if (key.field != null) {
            return getOrCreateInjectCallByClass(provider, key.field.getType());
        } else {
            throw new RuntimeException("impossible condition key:" + key);
        }
    }

    private InjectCall getOrCreateInjectCallByClass(CallProvider<?> provider, Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray() || clazz == String.class) {
            throw new CartrofitGrammarException("Can not use Inject operator on class type:" + clazz);
        }
        InjectCall injectCall = mInjectCallCache.get(clazz);
        if (injectCall == null) {
            injectCall = new InjectCall(clazz);
            provider.inflateCallback(clazz, CATEGORY_SET | CATEGORY_GET | CATEGORY_TRACK,
                    injectCall::addChildCall);
            if (injectCall.getChildCount() == 0) {
                throw new CartrofitGrammarException("Failed to parse Inject call from type:"
                        + clazz);
            }
            mInjectCallCache.put(clazz, injectCall);
        }
        return injectCall;
    }
}
