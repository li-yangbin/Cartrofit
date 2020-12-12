package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.funtion.Converter;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unchecked")
class CommandDelegate extends CommandFlow {
    CommandBase targetCommand;
    Converter<Object, ?> argConverter;
    CarType carType;
    boolean commandSet;
    boolean commandGet;

    @Override
    void onInit(Annotation annotation) {
        super.onInit(annotation);
        Delegate delegate = (Delegate) annotation;
        resolveStickyType(delegate.sticky());
        carType = delegate.type();
        commandSet = getType() == CommandType.SET;
        commandGet = getType() == CommandType.GET;

        targetCommand.overrideFromDelegate(this);

        resolveConverter();
    }

    @Override
    boolean requireSource() {
        return false;
    }

    @Override
    public boolean isReturnFlow() {
        return targetCommand.isReturnFlow();
    }

    private void resolveConverter() {
        if (commandSet || commandGet) {
            if (targetCommand.userDataClass != null) {
                if (commandSet) {
                    argConverter = (Converter<Object, ?>) store.find(this,
                            userDataClass = key.getSetClass(),
                            targetCommand.userDataClass);
                } else {
                    argConverter = (Converter<Object, ?>) store.find(this,
                            targetCommand.userDataClass,
                            userDataClass = key.getGetClass());
                }
            }
        } else if (isReturnFlow()) {
            resultConverter = (Converter<Object, ?>) store.find(this,
                    key.isCallbackEntry ? targetCommand.userDataClass : Flow.class,
                    key.getTrackClass());
            mapConverter = findMapConverter(targetCommand.userDataClass);
        } else {
            throw new IllegalStateException("impossible situation");
        }
    }

    void setTargetCommand(CommandBase targetCommand) {
        this.targetCommand = targetCommand.shallowCopy();
    }

    @Override
    boolean checkElement(CommandBase command) {
        if (command == this) {
            return true;
        }
        if (targetCommand instanceof CommandFlow) {
            return ((CommandFlow) targetCommand).checkElement(command);
        }
        return false;
    }

    @Override
    public Object invoke(Object parameter) {
        if (commandSet) {
            return targetCommand.invokeWithChain(argConverter != null ?
                    argConverter.convert(parameter) : parameter);
        } else if (commandGet) {
            Object obj = targetCommand.invokeWithChain(parameter);
            return argConverter != null ? argConverter.convert(obj) : obj;
        } else {
            return super.invoke(parameter);
        }
    }

    @Override
    public Class<?> getInputType() {
        return commandSet ? key.getSetClass() : null;
    }

    @Override
    Object doInvoke() {
        Object obj = targetCommand.invokeWithChain(null);
        Object result;
        if (obj instanceof FlowWrapper) {
            FlowWrapper<Object> flowWrapper = (FlowWrapper<Object>) obj;
            if (mapConverter != null) {
                flowWrapper.addMediator((Function<Object, Object>) mapConverter);
            }
            flowWrapper.addCommandReceiver(createCommandReceive());
            result = flowWrapper;

            if (flowWrapper instanceof StickyFlowImpl) {
                StickyFlowImpl<Object> stickyFlow = (StickyFlowImpl<Object>) flowWrapper;
                stickyFlow.addCommandStickyGet(getCommandStickyGet());
            }
        } else {
            result = obj;
        }
        if (result == null) {
            return null;
        }
        if (mapFlowSuppressed) {
            return result;
        }
        return resultConverter != null ? resultConverter.convert(result) : result;
    }

    @Override
    public CommandType getType() {
        return targetCommand.getType();
    }

    @Override
    CommandBase delegateTarget() {
        return targetCommand.delegateTarget();
    }

    @Override
    boolean hasCategory(int category) {
        return targetCommand.hasCategory(category);
    }

    @Override
    String toCommandString() {
        return super.toCommandString() + " Delegate{" + targetCommand.toCommandString() + "}";
    }
}

class CommandReflect extends CommandGroup {

    CommandInject commandInject;

    void setCommandInject(CommandInject commandInject) {
        this.commandInject = commandInject;
    }

    static boolean isGet(CommandType type) {
        switch (type) {
            case GET:
            case TRACK:
            case COMBINE:
                return true;
        }
        return false;
    }

    @Override
    public Object invoke(Object parameter) {
        final InjectInfo info = (InjectInfo) parameter;
        final Object target = info.target;

        if (target instanceof InjectReceiver) {
            if (((InjectReceiver) target).onBeforeInject(commandInject)) {
                return null;
            }
        }

        for (int i = 0; i < childrenCommand.size(); i++) {
            CommandBase childCommand = childrenCommand.get(i);
            CommandType childType = childCommand.getType();
            if (childType == CommandType.INJECT) {
                childCommand.invoke(parameter);
            } else {
                boolean isGetCommand = isGet(childType);
                try {
                    if (info.get && isGetCommand) {
                        childCommand.getField().set(target,
                                childCommand.invokeWithChain(null));
                    } else if (info.set && !isGetCommand) {
                        Object setValue = childCommand.getField().get(target);
                        childCommand.invokeWithChain(setValue);
                    }
                } catch (IllegalAccessException impossible) {
                    throw new RuntimeException(impossible);
                }
            }
        }

        if (target instanceof InjectReceiver) {
            ((InjectReceiver) target).onAfterInject(commandInject);
        }

        return null;
    }

    @Override
    public CommandType getType() {
        return CommandType.INJECT;
    }
}

class InjectInfo {
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

class CommandInject extends CommandBase {

    CommandReflect commandReflect;
    private InjectInfo dispatchInfo;
    private final Class<?> injectClass;
    private final int parameterIndex;

    CommandInject(Class<?> injectClass, int parameterIndex, CommandReflect commandReflect) {
        this.injectClass = injectClass;
        this.parameterIndex = parameterIndex;
        this.commandReflect = commandReflect;
        commandReflect.setCommandInject(this);
    }

    @Override
    boolean requireSource() {
        return false;
    }

    @Override
    void onInit(Annotation annotation) {
        if (key.method != null || key.isCallbackEntry) {
            dispatchInfo = new InjectInfo();
            Annotation[] annotations = key.method.getParameterAnnotations()[parameterIndex];
            for (Annotation parameterAnno : annotations) {
                if (key.isCallbackEntry) {
                    dispatchInfo.get |= parameterAnno instanceof In;
                    dispatchInfo.set |= parameterAnno instanceof Out;
                } else {
                    dispatchInfo.set |= parameterAnno instanceof In;
                    dispatchInfo.get |= parameterAnno instanceof Out;
                }
            }
        }
    }

    void suppressGetAndExecute(Object object) {
        final boolean oldGetEnable = dispatchInfo.get;
        dispatchInfo.get = false;
        if (shouldExecuteReflectOperation(dispatchInfo)) {
            dispatchInfo.target = object;
            commandReflect.invoke(dispatchInfo);
            dispatchInfo.target = null;
        }
        dispatchInfo.get = oldGetEnable;
    }

    void suppressSetAndExecute(Object object) {
        final boolean oldSetEnable = dispatchInfo.set;
        dispatchInfo.set = false;
        if (shouldExecuteReflectOperation(dispatchInfo)) {
            dispatchInfo.target = object;
            commandReflect.invoke(dispatchInfo);
            dispatchInfo.target = null;
        }
        dispatchInfo.set = oldSetEnable;
    }

    static boolean shouldExecuteReflectOperation(InjectInfo info) {
        return info.set || info.get;
    }

    @Override
    public Class<?> getInputType() {
        return injectClass;
    }

    @Override
    public Object invoke(Object parameter) {
        if (dispatchInfo != null) {
            if (shouldExecuteReflectOperation(dispatchInfo)) {
                dispatchInfo.target = parameter;
                commandReflect.invoke(dispatchInfo);
                dispatchInfo.target = null;
            }
        } else if (parameter instanceof InjectInfo) {
            InjectInfo dispatchedInfo = (InjectInfo) parameter;
            if (shouldExecuteReflectOperation(dispatchedInfo)) {
                InjectInfo info = dispatchedInfo.copy();
                try {
                    info.target = key.field.get(dispatchedInfo.target);
                } catch (IllegalAccessException impossible) {
                    throw new RuntimeException(impossible);
                }
                if (dispatchedInfo.set && info.target == null) {
                    throw new NullPointerException("Can not resolve target:" + key);
                }
                commandReflect.invoke(info);
                info.target = null;
            }
        } else {
            throw new RuntimeException("impossible situation parameter:" + parameter
                    + " from:" + this);
        }
        return null;
    }

    @Override
    public CommandType getType() {
        return CommandType.INJECT;
    }

    @Override
    String toCommandString() {
        String stable = injectClass.getSimpleName();
        return stable + " " + super.toCommandString();
    }
}

class CommandRegister extends CommandGroup implements UnTrackable {

    private final HashMap<Object, RegisterCallbackWrapper> callbackWrapperMapper = new HashMap<>();
    private final HashMap<CommandBase, ArrayList<CommandInject>> outParameterMap = new HashMap<>();

    @Override
    void onInit(Annotation annotation) {
        // ignore
    }

    void addChildCommand(CommandBase command, ArrayList<CommandInject> outList) {
        addChildCommand(command);
        outParameterMap.put(command, outList);
    }

    @Override
    public Object invoke(Object callback) {
        if (callbackWrapperMapper.containsKey(
                Objects.requireNonNull(callback, "callback can not be null"))) {
            throw new CartrofitGrammarException("callback:" + callback + " is already registered");
        }
        RegisterCallbackWrapper wrapper = new RegisterCallbackWrapper(callback);
        callbackWrapperMapper.put(callback, wrapper);
        wrapper.register();
        return null;
    }

    @Override
    public CommandType getType() {
        return CommandType.REGISTER;
    }

    @Override
    public void untrack(Object callback) {
        RegisterCallbackWrapper wrapper = callbackWrapperMapper.remove(callback);
        if (wrapper != null) {
            wrapper.unregister();
        }
    }

    private class RegisterCallbackWrapper {
        Object callbackObj;
        ArrayList<InnerObserver> commandObserverList = new ArrayList<>();

        RegisterCallbackWrapper(Object callbackObj) {
            this.callbackObj = callbackObj;
        }

        void register() {
            if (commandObserverList.size() > 0) {
                throw new CartrofitGrammarException("impossible situation");
            }
            for (int i = 0; i < childrenCommand.size(); i++) {
                CommandFlow entry = (CommandFlow) childrenCommand.get(i);
                InnerObserver observer = new InnerObserver(entry, callbackObj,
                        outParameterMap.get(entry));
                commandObserverList.add(observer);
                entry.invokeWithChain(observer);
            }
        }

        void unregister() {
            for (int i = 0; i < commandObserverList.size(); i++) {
                CommandFlow entry = (CommandFlow) childrenCommand.get(i);
                InnerObserver observer = commandObserverList.get(i);
                if (observer != null) {
                    entry.trackingFlow.removeObserver(observer);
                }
            }
            commandObserverList.clear();
        }
    }

    private static class InnerObserver implements Consumer<Object> {

        CommandFlow commandFlow;
        Object callbackObj;
        Method method;
        ArrayList<CommandInject> outParameterList;
        boolean dispatchProcessing;

        InnerObserver(CommandFlow commandFlow, Object callbackObj,
                      ArrayList<CommandInject> outParameterList) {
            this.commandFlow = commandFlow;
            this.callbackObj = callbackObj;
            this.method = commandFlow.key.method;
            this.outParameterList = outParameterList;
        }

        @Override
        public void accept(Object o) {
            if (dispatchProcessing) {
                throw new IllegalStateException("Recursive invocation from:" + commandFlow);
            }
            Object result;
            dispatchProcessing = true;
            try {
                final int parameterCount = outParameterList.size();
                Object[] parameters = new Object[parameterCount];
                for (int i = 0; i < parameterCount; i++) {
                    CommandInject inject = outParameterList.get(i);
                    if (inject != null) {
                        try {
                            parameters[i] = inject.getInputType().newInstance();
                        } catch (InstantiationException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        parameters[i] = o;
                    }
                }

                for (int i = 0; i < parameterCount; i++) {
                    CommandInject inject = outParameterList.get(i);
                    if (inject != null) {
                        inject.suppressSetAndExecute(parameters[i]);
                    }
                }

                result = method.invoke(callbackObj, parameters);

                for (int i = 0; i < parameterCount; i++) {
                    CommandInject inject = outParameterList.get(i);
                    if (inject != null) {
                        inject.suppressGetAndExecute(parameters[i]);
                    }
                }

                if (commandFlow.returnCommand != null) {
                    commandFlow.returnCommand.invokeWithChain(result);
                }
            } catch (InvocationTargetException invokeExp) {
                if (invokeExp.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) invokeExp.getCause();
                } else {
                    throw new RuntimeException("Callback invoke error", invokeExp.getCause());
                }
            } catch (IllegalAccessException illegalAccessException) {
                throw new RuntimeException("Impossible", illegalAccessException);
            } finally {
                dispatchProcessing = false;
            }
        }
    }
}
