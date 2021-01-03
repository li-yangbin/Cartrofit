package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CallAdapter;
import com.liyangbin.cartrofit.funtion.Union;

public class DelegateCall extends Call {
    private Call targetCall;

    DelegateCall(Call targetCall) {
        this.targetCall = targetCall.copyByHost(this);
    }

    @Override
    public boolean hasCategory(int category) {
        return targetCall.hasCategory(category);
    }

    @Override
    public CallAdapter.FieldAccessible asFieldAccessible() {
        return targetCall.asFieldAccessible();
    }

    //    @Override
//    boolean checkElement(CommandBase command) {
//        if (command == this) {
//            return true;
//        }
//        if (targetCommand instanceof CommandFlow) {
//            return ((CommandFlow) targetCommand).checkElement(command);
//        }
//        return false;
//    }
//
//    @Override
//    public Object invoke(Object parameter) {
//        if (commandSet) {
//            return targetCommand.invokeWithChain(argConverter != null ?
//                    argConverter.convert(parameter) : parameter);
//        } else if (commandGet) {
//            Object obj = targetCommand.invokeWithChain(parameter);
//            return argConverter != null ? argConverter.convert(obj) : obj;
//        } else {
//            return super.invoke(parameter);
//        }
//    }

    @Override
    protected Object doInvoke(Object arg) {
        return targetCall.invoke((Union<?>) arg);
    }

//    @Override
//    public Class<?> getInputType() {
//        return commandSet ? key.getSetClass() : null;
//    }

//    @Override
//    Object doInvoke() {
//        Object obj = targetCommand.invokeWithChain(null);
//        Object result;
//        if (obj instanceof FlowWrapper) {
//            FlowWrapper<Object> flowWrapper = (FlowWrapper<Object>) obj;
//            if (mapConverter != null) {
//                flowWrapper.addMediator((Function<Object, Object>) mapConverter);
//            }
//            flowWrapper.addCommandReceiver(createCommandReceive());
//            result = flowWrapper;
//
//            if (flowWrapper instanceof StickyFlowImpl) {
//                StickyFlowImpl<Object> stickyFlow = (StickyFlowImpl<Object>) flowWrapper;
//                stickyFlow.addCommandStickyGet(getCommandStickyGet());
//            }
//        } else {
//            result = obj;
//        }
//        if (result == null) {
//            return null;
//        }
//        if (mapFlowSuppressed) {
//            return result;
//        }
//        return resultConverter != null ? resultConverter.convert(result) : result;
//    }

//    @Override
//    CommandBase delegateTarget() {
//        return targetCommand.delegateTarget();
//    }

//    @Override
//    String toCommandString() {
//        return super.toCommandString() + " Delegate{" + targetCommand.toCommandString() + "}";
//    }
}
