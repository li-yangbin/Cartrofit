package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.CallAdapter;
import com.liyangbin.cartrofit.InjectReceiver;

public class InjectCall extends CallGroup<Call> implements CallAdapter.FieldAccessible {

    Class<?> targetClass;

    static class InjectInfo {
        boolean get;
        boolean set;
        Object target;

        InjectInfo copy() {
            InjectInfo copy = new InjectInfo();
            copy.get = this.get;
            copy.set = this.set;
            return copy;
        }

        @Override
        public String toString() {
            return "InjectInfo{" +
                    "get=" + get +
                    ", set=" + set +
                    ", target=" + target +
                    '}';
        }
    }

    InjectCall(Class<?> targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    public CallAdapter.FieldAccessible asFieldAccessible() {
        return key.field != null ? this : null;
    }

//    void suppressGetAndExecute(Object object) {
//        final boolean oldGetEnable = dispatchInfo.get;
//        dispatchInfo.get = false;
//        if (shouldExecuteReflectOperation(dispatchInfo)) {
//            dispatchInfo.target = object;
//            reflectCall.invoke(dispatchInfo);
//            dispatchInfo.target = null;
//        }
//        dispatchInfo.get = oldGetEnable;
//    }

//    void suppressSetAndExecute(Object object) {
//        final boolean oldSetEnable = dispatchInfo.set;
//        dispatchInfo.set = false;
//        if (shouldExecuteReflectOperation(dispatchInfo)) {
//            dispatchInfo.target = object;
//            reflectCall.invoke(dispatchInfo);
//            dispatchInfo.target = null;
//        }
//        dispatchInfo.set = oldSetEnable;
//    }

    static boolean shouldExecuteReflectOperation(InjectInfo info) {
        return info.set || info.get;
    }

    @Override
    protected Object doInvoke(Object parameter) {
        InjectInfo dispatchedInfo = (InjectInfo) parameter;
        if (shouldExecuteReflectOperation(dispatchedInfo)) {
            final Object target = dispatchedInfo.target;

            if (target instanceof InjectReceiver) {
                if (((InjectReceiver) target).onBeforeInject(this)) {
                    return null;
                }
            }

            for (int i = 0; i < getChildCount(); i++) {
                Call childCall = getChildAt(i);
                if (childCall instanceof InjectCall) {
                    InjectInfo copiedInfo = dispatchedInfo.copy();
                    try {
                        copiedInfo.target = key.field.get(dispatchedInfo.target);
                    } catch (IllegalAccessException impossible) {
                        throw new RuntimeException(impossible);
                    }
                    if (dispatchedInfo.get && copiedInfo.target == null) {
                        throw new NullPointerException("Can not resolve target:" + key);
                    } else if (dispatchedInfo.set && copiedInfo.target == null) {
                        // TODO: really necessary?
                        try {
                            copiedInfo.target = key.field.getType().newInstance();
                            key.field.set(dispatchedInfo.target, copiedInfo.target);
                        } catch (IllegalAccessException | InstantiationException illegalAccessException) {
                            throw new RuntimeException("Do provide a default constructor for:" + key.field.getType());
                        }
                    }

                    childCall.invoke(copiedInfo);
                } else {
                    CallAdapter.FieldAccessible childKeyAccess = childCall.asFieldAccessible();
                    boolean isGetCommand = childCall.hasCategory(CallAdapter.CATEGORY_GET
                            | CallAdapter.CATEGORY_TRACK);
                    boolean isSetCommand = !isGetCommand
                            && childCall.hasCategory(CallAdapter.CATEGORY_SET);
                    try {
                        if (dispatchedInfo.get && isGetCommand) {
                            childKeyAccess.set(target, childCall.invoke(null));
                        } else if (dispatchedInfo.set && isSetCommand) {
                            Object setValue = childKeyAccess.get(target);
                            childCall.invoke(setValue);
                        }
                    } catch (IllegalAccessException impossible) {
                        throw new RuntimeException(impossible);
                    }
                }
            }

            if (target instanceof InjectReceiver) {
                ((InjectReceiver) target).onAfterInject(this);
            }
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
