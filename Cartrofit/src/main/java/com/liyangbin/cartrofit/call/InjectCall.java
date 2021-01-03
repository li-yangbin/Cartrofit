package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CallAdapter;
import com.liyangbin.cartrofit.CallGroup;
import com.liyangbin.cartrofit.InjectReceiver;
import com.liyangbin.cartrofit.funtion.Union;

public class InjectCall extends CallGroup<Call> implements CallAdapter.FieldAccessible {

    Class<?> targetClass;

    InjectCall(Class<?> targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    public CallAdapter.FieldAccessible asFieldAccessible() {
        return key.field != null ? this : null;
    }

    @Override
    protected Call asCall(Call call) {
        return call;
    }

    @Override
    public InjectGroupCall.InjectContext getParameterContext() {
        return (InjectGroupCall.InjectContext) super.getParameterContext();
    }

    @Override
    public Object mapInvoke(Union<?> parameter) {
        final boolean doGet = getParameterContext().doGet(this);
        final boolean doSet = getParameterContext().doSet(this);
        if (!doGet && !doSet) {
            return null;
        }
        try {
            Object target = getParameterContext().getTarget(this);

            if (target instanceof InjectReceiver) {
                if (((InjectReceiver) target).onBeforeInject(this)) {
                    return null;
                }
            }

            for (int i = 0; i < getChildCount(); i++) {
                Call childCall = getChildAt(i);
                if (childCall instanceof InjectCall) {
                    childInvoke(childCall, parameter);
                } else {
                    CallAdapter.FieldAccessible childKeyAccess = childCall.asFieldAccessible();
                    if (childKeyAccess != null) {
                        boolean isGetCommand = childCall.hasCategory(CallAdapter.CATEGORY_GET
                                | CallAdapter.CATEGORY_TRACK);
                        boolean isSetCommand = !isGetCommand
                                && childCall.hasCategory(CallAdapter.CATEGORY_SET);
                        if (doGet && isGetCommand) {
                            childKeyAccess.set(target, childInvoke(childCall, parameter));
                        } else if (doSet && isSetCommand) {
                            Object setValue = childKeyAccess.get(target);
                            childInvokeWithExtra(childCall, parameter, setValue);
                        }
                    }
                }
            }

            if (target instanceof InjectReceiver) {
                ((InjectReceiver) target).onAfterInject(this);
            }

        } catch (IllegalAccessException impossible) {
            throw new RuntimeException(impossible);
        }
        return null;
    }

    @Override
    public void set(Object target, Object value) throws IllegalAccessException {
        throw new RuntimeException("impossible situation");
    }

    @Override
    public Object get(Object target) throws IllegalAccessException {
        throw new RuntimeException("impossible situation");
    }

//    @Override
//    public CommandType getType() {
//        return CommandType.INJECT;
//    }
//
//    @Override
//    String toCommandString() {
//        String stable = injectClass.getSimpleName();
//        return stable + " " + super.toCommandString();
//    }
}
