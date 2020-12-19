package com.liyangbin.cartrofit;

public class ReflectCall extends CallGroup<CallAdapter.Call> {
    private InjectCall injectCall;

    void setInjectCall(InjectCall injectCall) {
        this.injectCall = injectCall;
    }

    @Override
    public void addChildCall(CallAdapter.Call call) {
        if (call.asFieldAccessible() == null) {
            throw new CartrofitGrammarException("Child of ReflectCall must provide field accessing ability");
        }
        super.addChildCall(call);
    }

    @Override
    public Object doInvoke(Object parameter) {
        final InjectInfo info = (InjectInfo) parameter;
        final Object target = info.target;

        if (target instanceof InjectReceiver) {
            if (((InjectReceiver) target).onBeforeInject(injectCall)) {
                return null;
            }
        }

        for (int i = 0; i < getChildCount(); i++) {
            CallAdapter.Call childCall = getChildAt(i);
            if (childCall instanceof InjectCall) {
                childCall.invoke(parameter);
            } else {
                CallAdapter.FieldAccessible childKeyAccess = childCall.asFieldAccessible();
                boolean isGetCommand = childCall.hasCategory(CallAdapter.CATEGORY_GET
                        | CallAdapter.CATEGORY_TRACK);
                boolean isSetCommand = !isGetCommand
                        && childCall.hasCategory(CallAdapter.CATEGORY_SET);
                try {
                    if (info.get && isGetCommand) {
                        childKeyAccess.set(target, childCall.invoke(null));
                    } else if (info.set && isSetCommand) {
                        Object setValue = childKeyAccess.get(target);
                        childCall.invoke(setValue);
                    }
                } catch (IllegalAccessException impossible) {
                    throw new RuntimeException(impossible);
                }
            }
        }

        if (target instanceof InjectReceiver) {
            ((InjectReceiver) target).onAfterInject(injectCall);
        }

        return null;
    }
}
