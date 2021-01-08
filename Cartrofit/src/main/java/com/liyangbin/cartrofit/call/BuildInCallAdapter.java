package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CallAdapter;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.ConverterFactory;
import com.liyangbin.cartrofit.annotation.Bind;
import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.Combine;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Inject;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Timeout;
import com.liyangbin.cartrofit.annotation.Unregister;

import java.util.ArrayList;
import java.util.Arrays;

public class BuildInCallAdapter extends CallAdapter {

    private static final Object STABLE_SCOPE = new Object();

    @Override
    public Object extractScope(Class<?> scopeClass, ConverterFactory factory) {
        return STABLE_SCOPE;
    }

    @Override
    public void onProvideCallSolution(CallSolutionBuilder builder) {
        builder.create(Inject.class)
                .takeIfDefault()
                .checkParameterIncluded(In.class, Out.class)
                .provide(new CallProvider<Inject>() {
                    @Override
                    public Call provide(int category, Inject inject, Cartrofit.Key key) {
                        return createInjectCommand(this, key);
                    }
                });

        builder.create(Combine.class)
                .takeIfContains(CATEGORY_TRACK | CATEGORY_GET)
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
                .provide(new CallProvider<Combine>() {
                    @Override
                    public Call provide(int category, Combine combine, Cartrofit.Key key) {
                        CombineCall combineCall = new CombineCall();
                        for (int element : combine.elements()) {
                            Call childCall = inflateByIdIfThrow(key, element,
                                    category & (CATEGORY_TRACK | CATEGORY_GET));
                            combineCall.addChildCall(childCall);
                        }
                        return combineCall;
                    }
                });

        builder.create(Timeout.class)
                .takeIfContains(CATEGORY_TRACK)
                .provide(new CallProvider<Timeout>() {
                    @Override
                    public Call provide(int category, Timeout timeout, Cartrofit.Key key) {
                        return new TimeoutCall(timeout.value());
                    }
                });

        builder.create(Register.class)
                .takeIfDefault()
                .checkParameter((register, key) -> {
                    if (key.getParameterCount() == 0) {
                        throw new CartrofitGrammarException("Register must declare a Callback parameter " + key);
                    }
                    if (key.getParameterCount() == 1) {
                        if (!key.getParameterAt(0).getType().isInterface()) {
                            throw new CartrofitGrammarException("Declare a Callback parameter as an interface " + key);
                        }
                    } else {
                        Cartrofit.Parameter callbackParameter = key.findParameterByAnnotation(Callback.class);
                        if (callbackParameter == null) {
                            throw new CartrofitGrammarException("Declare a Callback parameter as an interface " + key);
                        }
                        for (int i = 0; i < key.getParameterCount(); i++) {
                            if (i != callbackParameter.getDeclaredIndex() && !key.isAnnotationPresent(Bind.class)) {
                                throw new CartrofitGrammarException("Declare other parameter by annotation "
                                        + Bind.class);
                            }
                        }
                    }
                })
                .provide(new CallProvider<Register>() {
                    @Override
                    public Call provide(int category, Register register, Cartrofit.Key key) {
                        Cartrofit.Parameter callbackParameter = key.findParameterByAnnotation(Callback.class);
                        if (callbackParameter == null) {
                            callbackParameter = key.getParameterAt(0);
                        }
                        final RegisterCall registerCall = new RegisterCall();
                        inflateCallback(callbackParameter.getType(), CATEGORY_TRACK, call -> {
                            Call returnCall = reInflate(call.getKey(), CATEGORY_SET);
                            Call parameterCall = createInjectCommand(this, key);
                            registerCall.addChildCall(call, returnCall, parameterCall);
                        });
                        if (registerCall.getChildCount() == 0) {
                            throw new CartrofitGrammarException("Failed to resolve callback entry point in "
                                    + callbackParameter.getType());
                        }
                        return registerCall;
                    }
                });

        builder.create(Unregister.class)
                .takeIfDefault()
                .checkParameter((unregister, key) -> {
                    if (key.getParameterCount() != 1
                            || !key.getParameterAt(0).getType().isInterface()) {
                        throw new CartrofitGrammarException("Declare a Callback parameter as an interface " + key);
                    }
                })
                .provide(new CallProvider<Unregister>() {
                    @Override
                    public Call provide(int category, Unregister unregister, Cartrofit.Key key) {
                        UnregisterCall unregisterCall = new UnregisterCall();
                        unregisterCall.setRegisterCall((RegisterCall) inflateByIdIfThrow(key,
                                unregister.value(), CATEGORY_TRACK));
                        return unregisterCall;
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

    public Call wrapNormalTrack2RegisterIfNeeded(Call call) {
        if (call instanceof RegisterCall || !call.hasCategory(CATEGORY_TRACK)) {
            return null;
        }
        Cartrofit.Parameter callbackParameter = call.getKey().findParameterByAnnotation(Callback.class);
        if (callbackParameter == null) {
            return null;
        }

        ArrayList<Cartrofit.Key> childrenKey = getChildKey(callbackParameter.getType());
        Cartrofit.Key trackKey = null;
        Cartrofit.Key timeoutKey = null;
        for (int i = 0; i < childrenKey.size(); i++) {
            Cartrofit.Key entryKey = childrenKey.get(i);
            if (entryKey.isAnnotationPresent(Unregister.class)) {
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
                    InjectCall injectCall = createInjectCallByClass(provider, targetClass);

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
            return createInjectCallByClass(provider, key.field.getType());
        } else {
            throw new RuntimeException("impossible condition key:" + key);
        }
    }

    private InjectCall createInjectCallByClass(CallProvider<?> provider, Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray() || clazz == String.class) {
            throw new CartrofitGrammarException("Can not use Inject operator on class type:" + clazz);
        }
        InjectCall injectCall = new InjectCall(clazz);
        // TODO: category wrong
        provider.inflateCallback(clazz, CATEGORY_SET | CATEGORY_GET | CATEGORY_TRACK,
                injectCall::addChildCall);
        if (injectCall.getChildCount() == 0) {
            throw new CartrofitGrammarException("Failed to parse Inject call from type:"
                    + clazz);
        }
        return injectCall;
    }
}
