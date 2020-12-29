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

import java.lang.annotation.Annotation;
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
    public Call onCreateCall(Object scopeObj, Cartrofit.Key key, int category) {
        Unregister unregister = category == CATEGORY_DEFAULT ? key.getAnnotation(Unregister.class) : null;
        if (unregister != null) {
            UnregisterCall unregisterCall = new UnregisterCall();
            unregisterCall.setRegisterCall((RegisterCall) mCallInflater
                    .inflateByIdIfThrow(key, unregister.value(), CATEGORY_TRACK));
            return unregisterCall;
        }

        Inject inject = (category & CATEGORY_INJECT) != 0 ? key.getAnnotation(Inject.class) : null;
        if (inject != null) {
            return createInjectCommand(key);
        }

        Combine combine = (category & (CATEGORY_TRACK | CATEGORY_GET)) != 0
                ? key.getAnnotation(Combine.class) : null;
        if (combine != null) {
            CombineCall combineCall = new CombineCall();
            int[] elements = combine.elements();
            if (elements.length <= 1) {
                throw new CartrofitGrammarException("Must declare more than one element on Combine:"
                        + key + " elements:" + Arrays.toString(elements));
            }
            for (int element : elements) {
                Call childCall = mCallInflater.inflateByIdIfThrow(key, element,
                        CATEGORY_TRACK | CATEGORY_GET);
                combineCall.addChildCall(childCall);
            }
            return combineCall;
        }

        Register register = category == CATEGORY_DEFAULT ? key.getAnnotation(Register.class) : null;
        if (register != null) {
            Class<?> targetClass = key.getSetClass();
            if (!targetClass.isInterface()) {
                throw new CartrofitGrammarException("Declare CarCallback parameter as an interface:" + targetClass);
            }
            final RegisterCall registerCall = new RegisterCall();
            mCallInflater.inflateCallback(targetClass, CATEGORY_TRACK, call -> {
                Call returnCall = mCallInflater.inflate(call.key, CATEGORY_RETURN);
                Call parameterCall = createInjectCommand(key);
                registerCall.addChildCall(call, returnCall, parameterCall);
            });
            if (registerCall.getChildCount() == 0) {
                throw new CartrofitGrammarException("Failed to resolve callback entry point in " + targetClass);
            }
            return registerCall;
        }

        Delegate delegate = key.getAnnotation(Delegate.class);
        if (delegate != null) {
            Call delegateTarget = mCallInflater.inflateById(key, delegate.value(), category);
            if (delegateTarget != null) {
                DelegateCall call = new DelegateCall();
                call.setTargetCall(delegateTarget);
                return call;
            }
        }
        return null;
    }

    private static boolean contains(Annotation[] annotations, Class<? extends Annotation> expectClass) {
        for (Annotation annotation : annotations) {
            if (expectClass.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }

    private Call createInjectCommand(Cartrofit.Key key) {
        if (key.method != null) {
            Class<?>[] parameterTypes = key.method.getParameterTypes();
            Annotation[][] annotationMatrix = key.method.getParameterAnnotations();
            InjectGroupCall injectGroupCall = null;
            for (int i = 0; i < parameterTypes.length; i++) {
                boolean inDeclared = !key.isCallbackEntry && contains(annotationMatrix[i], In.class);
                boolean outDeclared = contains(annotationMatrix[i], Out.class);

                if (inDeclared || outDeclared) {
                    Class<?> targetClass = key.method.getParameterTypes()[i];
                    InjectCall injectCall = getOrCreateInjectCallByClass(targetClass);

                    if (injectGroupCall == null) {
                        injectGroupCall = new InjectGroupCall(parameterTypes.length);
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
            return getOrCreateInjectCallByClass(key.field.getType());
        } else {
            return null;
        }
    }

    private InjectCall getOrCreateInjectCallByClass(Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray() || clazz == String.class) {
            throw new CartrofitGrammarException("Can not use Inject operator on class type:" + clazz);
        }
        InjectCall injectCall = mInjectCallCache.get(clazz);
        if (injectCall == null) {
            injectCall = new InjectCall(clazz);
            mCallInflater.inflateCallback(clazz, CATEGORY_SET | CATEGORY_GET | CATEGORY_TRACK,
                    injectCall::addChildCall);
            if (injectCall.getChildCount() == 0) {
                throw new CartrofitGrammarException("Failed to parse Inject command from type:"
                        + clazz);
            }
            mInjectCallCache.put(clazz, injectCall);
        }
        return injectCall;
    }

//    private void setupCallbackEntryCommandIfNeeded(Call entryCall, Cartrofit.Key key) {
//        if (key.isCallbackEntry) {
//            Call returnCall = mCallInflater.inflate(key, CATEGORY_RETURN);
//            Delegate returnDelegate = returnCommand == null ? key.getAnnotation(Delegate.class) : null;
//            if (returnDelegate != null && returnDelegate._return() != 0) {
//                CommandBase delegateTarget = mCallInflater.inflateById(key,
//                        returnDelegate._return(), CATEGORY_SET);
//                CommandDelegate returnDelegateCommand = new CommandDelegate();
//                returnDelegateCommand.setTargetCommand(delegateTarget);
//                returnCommand = returnDelegateCommand;
//            }
//            if (returnCommand != null) {
//                entryCommand.setReturnCommand(returnCommand);
//            }
//        }
//    }
}
