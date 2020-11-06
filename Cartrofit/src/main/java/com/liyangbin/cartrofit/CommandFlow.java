package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.annotation.Combine;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.funtion.FunctionalCombinator;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

class CommandReceive extends CommandImpl {
    CommandFlow commandFlow;
    FlowWrapper<Object> flowWrapper;

    CommandReceive(CommandFlow commandFlow) {
        this.commandFlow = commandFlow;
        copyFrom(commandFlow);
    }

    @Override
    public Object invoke(Object parameter) {
        flowWrapper.superHandleResult(this, parameter);
        return null;
    }

    @Override
    CommandImpl delegateTarget() {
        return commandFlow;
    }

    @Override
    public CommandType getType() {
        return CommandType.RECEIVE;
    }

    @Override
    String toCommandString() {
        return commandFlow.toCommandString();
    }
}

class CommandStickyGet extends CommandImpl {
    CommandFlow commandFlow;
    Converter<Object, ?> converter;
    CarType carType = CarType.VALUE;
    CommandStickyGet nextGetCommand;

    CommandStickyGet(CommandFlow commandFlow) {
        this.commandFlow = commandFlow;
        this.converter = commandFlow.mapConverter;
        if (commandFlow instanceof CommandTrack) {
            this.carType = ((CommandTrack) commandFlow).type;
        }
        copyFrom(commandFlow);
    }

    @Override
    public Object invoke(Object parameter) {
        if (nextGetCommand != null) {
            return nextGetCommand.invokeWithChain(parameter);
        }
        return commandFlow.loadInitialData();
    }

    @Override
    CommandImpl delegateTarget() {
        return commandFlow;
    }

    @Override
    public CommandType getType() {
        return CommandType.STICKY_GET;
    }

    @Override
    String toCommandString() {
        return commandFlow.toCommandString();
    }
}

@SuppressWarnings("unchecked")
abstract class CommandFlow extends CommandImpl implements UnTrackable {
    private static final Timer sTimeOutTimer = new Timer("timeout-tracker");
    private static final long TIMEOUT_DELAY = 1500;

    Converter<Object, ?> resultConverter;
    Converter<Object, ?> mapConverter;
    boolean mapFlowSuppressed;

    StickyType stickyType;

    private boolean registerTrack;
    ArrayList<CommandImpl> childrenCommand = new ArrayList<>();

    CommandImpl restoreCommand;
    CommandImpl returnCommand;

    // runtime variable
    CommandStickyGet commandStickyGet;
    Flow<Object> trackingFlow;
    TimerTask task;
    ArrayList<WeakReference<CommandReceive>> receiveCommandList;
    AtomicBoolean monitorSetResponseRegistered = new AtomicBoolean();

    /**
     * Principle: Flow command paired non-Flow command
     */
    void setRestoreCommand(CommandImpl restoreCommand) {
        if (isReturnFlow()) {
            if (restoreCommand.isReturnFlow()) {
                throw new CartrofitGrammarException("Invalid flow restore:" + restoreCommand
                        + " on flow:" + this);
            }
            this.restoreCommand = restoreCommand;
        } else {
            if (!restoreCommand.isReturnFlow()) {
                throw new CartrofitGrammarException("Invalid non-flow restore:" + restoreCommand
                        + " on non-Flow:" + this);
            }
            ((CommandFlow) restoreCommand).setupRestoreInterceptor(this);
        }
    }

    void setupRestoreInterceptor(CommandImpl restoreCommand) {
        if (stickyType == StickyType.NO_SET || stickyType == StickyType.OFF) {
            throw new CartrofitGrammarException("Sticky type must be ON if restore command is set");
        }
        restoreCommand.addInterceptor((command, parameter) -> {
            if (monitorSetResponseRegistered.get()) {
                synchronized (sTimeOutTimer) {
                    if (task != null) {
                        task.cancel();
                    }
                    sTimeOutTimer.schedule(task = new TimerTask() {
                        @Override
                        public void run() {
                            restoreDispatch();
                        }
                    }, TIMEOUT_DELAY);
                }
            }
            return command.invoke(parameter);
        }, true);
    }

    CommandReceive createCommandReceive() {
        CommandReceive receive = new CommandReceive(this);
        if (restoreCommand != null) {
            if (receiveCommandList == null) {
                receiveCommandList = new ArrayList<>();
            }
            receiveCommandList.add(new WeakReference<>(receive));
            if (monitorSetResponseRegistered.compareAndSet(false, true)) {
                receive.addInterceptor((command, parameter) -> {
                    synchronized (sTimeOutTimer) {
                        if (task != null) {
                            task.cancel();
                            task = null;
                        }
                    }
                    return command.invoke(parameter);
                }, false);
            }
        }
        return receive;
    }

    CommandStickyGet getCommandStickyGet() {
        if (commandStickyGet == null) {
            commandStickyGet = new CommandStickyGet(this);
        }
        return commandStickyGet;
    }

    void restoreDispatch() {
        ArrayList<WeakReference<CommandReceive>> commandReceiveList;
        synchronized (sTimeOutTimer) {
            task = null;
            if (receiveCommandList != null) {
                commandReceiveList = (ArrayList<WeakReference<CommandReceive>>) receiveCommandList.clone();
            } else {
                commandReceiveList = null;
            }
        }
        if (commandReceiveList != null) {
            boolean purgeExpired = false;
            for (int i = 0; i < commandReceiveList.size(); i++) {
                WeakReference<CommandReceive> receiveRef = commandReceiveList.get(i);
                CommandReceive commandReceive = receiveRef.get();
                if (commandReceive != null) {
                    Flow.StickyFlow<?> stickyFlow = (Flow.StickyFlow<?>) commandReceive.flowWrapper;
                    commandReceive.invokeWithChain(stickyFlow.get());
                } else {
                    purgeExpired = true;
                }
            }
            if (purgeExpired) {
                synchronized (sTimeOutTimer) {
                    for (int i = receiveCommandList.size() - 1; i >= 0; i--) {
                        WeakReference<CommandReceive> receiveRef =
                                receiveCommandList.get(i);
                        if (receiveRef.get() == null) {
                            receiveCommandList.remove(i);
                        }
                    }
                }
            }
        }
    }

    // used for CommandCombine
    void addChildCommand(CommandImpl command) {
        childrenCommand.add(command);
    }

    void setReturnCommand(CommandImpl command) {
        returnCommand = command;
    }

    @Override
    CommandFlow shallowCopy() {
        CommandFlow commandFlow = (CommandFlow) super.shallowCopy();
        commandFlow.commandStickyGet = null;
        commandFlow.trackingFlow = null;
        commandFlow.task = null;
        commandFlow.receiveCommandList = null;
        commandFlow.monitorSetResponseRegistered = new AtomicBoolean();
        return commandFlow;
    }

    Object loadInitialData() {
        throw new IllegalStateException("impossible");
    }

    @Override
    void overrideFromDelegate(CommandFlow delegateCommand) {
        registerTrack = false;
        mapFlowSuppressed = true;
        if (delegateCommand.stickyType != StickyType.NO_SET) {
            stickyType = delegateCommand.stickyType;
        }
        if (delegateCommand.restoreCommand != null) {
            setRestoreCommand(delegateCommand.restoreCommand);
        }
    }

    void resolveStickyType(StickyType stickyType) {
        if (stickyType == StickyType.NO_SET) {
            this.stickyType = record.stickyType;
        } else {
            this.stickyType = stickyType;
        }
    }

    @Override
    void onInit(Annotation annotation) {
        if (key.isCallbackEntry) {
            registerTrack = true;
            mapFlowSuppressed = true;
        } else if (key.method != null && key.method.getReturnType() == void.class) {
            Class<?>[] classArray = key.method.getParameterTypes();
            if (classArray.length == 1) {
                Class<?> parameterClass = classArray[0];
                if (Consumer.class.isAssignableFrom(parameterClass)) {
                    registerTrack = true;
                    mapFlowSuppressed = true;
                } else {
                    throw new CartrofitGrammarException("invalid track parameter:" + this);
                }
            }
        }
    }

    boolean checkElement(CommandImpl command) {
        if (command == this) {
            return true;
        }
        for (int i = 0; i < childrenCommand.size(); i++) {
            CommandImpl childCommand = childrenCommand.get(i);
            if (childCommand instanceof CommandFlow) {
                if (((CommandFlow) childCommand).checkElement(command)) {
                    return true;
                }
            } else if (command == childCommand) {
                return true;
            }
        }
        return false;
    }

    Converter<Object, ?> findMapConverter(Class<?> carType) {
        Class<?> userConcernClass = key.getUserConcernClass();
        if (userConcernClass != null) {
            userDataClass = userConcernClass;
            return (Converter<Object, ?>) store.find(this, carType, userConcernClass);
        }
        return null;
    }

    @Override
    public Object invoke(Object parameter) {
        if (isReturnFlow() && registerTrack) {
            if (trackingFlow == null) {
                trackingFlow = (Flow<Object>) doInvoke();
            }
            Consumer<Object> consumer = (Consumer<Object>) parameter;
            trackingFlow.addObserver(consumer);
            return null;
        } else {
            return doInvoke();
        }
    }

    @Override
    public void untrack(Object callback) {
        if (trackingFlow != null) {
            trackingFlow.removeObserver((Consumer<Object>) callback);
        }
    }

    abstract Object doInvoke();

    @Override
    String toCommandString() {
        String fromSuper = super.toCommandString();
        if (stickyType != StickyType.NO_SET) {
            fromSuper += " sticky:" + stickyType;
        }
        return fromSuper;
    }

    boolean isStickyOn() {
        return stickyType == StickyType.ON || stickyType == StickyType.ON_NO_CACHE;
    }
}

@SuppressWarnings("unchecked")
class CommandTrack extends CommandFlow {
    CarType type;
    Annotation annotation;

    @Override
    void setRestoreCommand(CommandImpl restoreCommand) {
        super.setRestoreCommand(restoreCommand);
        setupRestoreInterceptor(restoreCommand);
    }

    @Override
    void onInit(Annotation annotation) {
        super.onInit(annotation);
        Track track = (Track) annotation;
        propertyId = track.id();
        type = track.type();
        this.annotation = annotation;
        if (type == CarType.CONFIG) {
            throw new CartrofitGrammarException("Can not use type CONFIG mode in Track operation");
        }

        resolveArea(track.area());
        resolveStickyType(track.sticky());

        resolveConverter();
    }

    @Override
    public boolean isReturnFlow() {
        return true;
    }

    private void resolveConverter() {
        if (!mapFlowSuppressed) {
            Converter<?, ?> converter = store.find(this, Flow.class, key.getTrackClass());
            resultConverter = (Converter<Object, ?>) converter;
        }

        if (type != CarType.NONE) {
            Class<?> carType;
            if (type == CarType.AVAILABILITY) {
                carType = boolean.class;
            } else {
                try {
                    carType = source.extractValueType(propertyId);
                } catch (Exception e) {
                    throw new CartrofitGrammarException(e);
                }
            }
            mapConverter = findMapConverter(carType);
        }
    }

    @Override
    Object doInvoke() {
        Flow<CarPropertyValue<?>> flow = source.track(propertyId, area);
        Flow<?> result = flow;
        switch (type) {
            case VALUE:
                if (mapConverter != null) {
                    result = new MediatorFlow<>(flow,
                            carPropertyValue -> mapConverter.apply(carPropertyValue.getValue()));
                } else {
                    result = new MediatorFlow<>(flow, CarPropertyValue::getValue);
                }
                break;
            case AVAILABILITY:
                if (mapConverter != null) {
                    result = new MediatorFlow<>(flow, value -> mapConverter.apply(value != null
                            && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE));
                } else {
                    result = new MediatorFlow<>(flow, value -> value != null
                            && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE);
                }
                break;
            case ALL:
                if (mapConverter != null) {
                    result = new MediatorFlow<>(flow, rawValue -> new CarPropertyValue<>(
                            rawValue.getPropertyId(),
                            rawValue.getAreaId(),
                            rawValue.getStatus(),
                            rawValue.getTimestamp(),
                            mapConverter.apply(rawValue.getValue())));
                } else {
                    result = new MediatorFlow<>(flow, null);
                }
                break;
        }
        if (type != CarType.NONE && isStickyOn()) {
            result = new StickyFlowImpl<>(result, stickyType == StickyType.ON,
                    getCommandStickyGet());
            ((StickyFlowImpl<?>) result).addCommandReceiver(createCommandReceive());
        } else if (type == CarType.NONE) {
            result = new EmptyFlowWrapper((Flow<Void>) result, createCommandReceive());
        } else {
            result = new FlowWrapper<>(result, createCommandReceive());
        }
        if (mapFlowSuppressed) {
            return result;
        }
        return resultConverter != null ? resultConverter.convert(result) : result;
    }

    @Override
    Object loadInitialData() {
        Object obj;
        if (type == CarType.ALL) {
            obj = source.get(propertyId, area, CarType.VALUE);
            return new CarPropertyValue<>(propertyId, area,
                    mapConverter != null ? mapConverter.convert(obj) : obj);
        } else {
            obj = source.get(propertyId, area, type);
            return mapConverter != null ? mapConverter.convert(obj) : obj;
        }
    }

    @Override
    public CommandType getType() {
        return CommandType.TRACK;
    }

    @Override
    String toCommandString() {
        String stable = "id:0x" + Integer.toHexString(getPropertyId())
                + (area != Scope.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "");
        if (type != CarType.VALUE) {
            stable += " valueType:" + type;
        }
        return stable + super.toCommandString();
    }
}

class CommandUnTrack extends CommandImpl {
    UnTrackable trackCommand;

    @Override
    void onInit(Annotation annotation) {
        if (key.method != null && key.method.getReturnType() == void.class) {
            Class<?>[] classArray = key.method.getParameterTypes();
            if (classArray.length == 1) {
                if (classArray[0] == trackCommand.getMethod().getParameterTypes()[0]) {
                    return;
                }
            }
        }
        throw new CartrofitGrammarException("invalid unTrack:" + this);
    }

    void setTrackCommand(UnTrackable trackCommand) {
        this.trackCommand = trackCommand;
    }

    @Override
    public Object invoke(Object callback) {
        trackCommand.untrack(callback);
        return null;
    }

    @Override
    public CommandType getType() {
        return CommandType.UN_TRACK;
    }

    @Override
    String toCommandString() {
        return "paired:" + trackCommand;
    }
}

@SuppressWarnings("unchecked")
class CommandCombine extends CommandFlow {

    CombineFlow combineFlow;

    @Override
    void addChildCommand(CommandImpl command) {
        CommandImpl childCopy = command.shallowCopy();
        childrenCommand.add(childCopy);
    }

    @Override
    boolean requireSource() {
        return false;
    }

    @Override
    void onInit(Annotation annotation) {
        super.onInit(annotation);
        Combine combine = (Combine) annotation;
        resolveStickyType(combine.sticky());
        if (stickyType == StickyType.NO_SET || stickyType == StickyType.OFF) {
            throw new CartrofitGrammarException("Must declare stickyType ON for combine command");
        }

        ArrayList<CommandFlow> flowChildren = null;
        ArrayList<CommandImpl> otherChildren = null;
        for (int i = 0; i < childrenCommand.size(); i++) {
            CommandImpl childCommand = childrenCommand.get(i);
            childCommand.overrideFromDelegate(this);
            if (childCommand instanceof CommandFlow) {
                if (flowChildren == null) {
                    flowChildren = new ArrayList<>();
                }
                flowChildren.add((CommandFlow) childCommand);
            } else {
                if (otherChildren == null) {
                    otherChildren = new ArrayList<>();
                }
                otherChildren.add(childCommand);
            }
        }
        if (flowChildren != null && otherChildren != null) {
            for (int i = 0; i < flowChildren.size(); i++) {
                for (int j = 0; j < otherChildren.size(); j++) {
                    if (flowChildren.get(i).checkElement(otherChildren.get(j))) {
                        throw new CartrofitGrammarException("Duplicate combine element:" + this);
                    }
                }
            }
        }

        resolveConverter();
    }

    @Override
    public boolean isReturnFlow() {
        for (int i = 0; i < childrenCommand.size(); i++) {
            if (childrenCommand.get(i).isReturnFlow()) {
                return true;
            }
        }
        return false;
    }

    private void resolveConverter() {
        if (isReturnFlow()) {
            Converter<?, ?> converter = store.find(this, Flow.class, key.getTrackClass());
            resultConverter = (Converter<Object, ?>) converter;

            mapConverter = findMapConverter(Object[].class);
            if (mapConverter == null) {
                throw new CartrofitGrammarException("Must indicate a converter with Object[] type input");
            }
        } else {
            resultConverter = (Converter<Object, ?>) store.find(this, Object[].class,
                    key.getTrackClass());
            if (resultConverter == null) {
                throw new CartrofitGrammarException("Must indicate a converter with Object[] type input");
            }
        }
    }

    @Override
    Object doInvoke() {
        if (combineFlow == null) {
            final int size = childrenCommand.size();
            Object[] elementResult = new Object[size];
            for (int i = 0; i < size; i++) {
                CommandImpl childCommand = childrenCommand.get(i);
                elementResult[i] = childCommand.invokeWithChain(null);
            }
            combineFlow = new CombineFlow(elementResult);
        }
        Object result;
        if (isReturnFlow()) {
            Flow<Object> flow;
            if (mapConverter instanceof FunctionalCombinator) {
                flow = new MediatorFlow<>(combineFlow,
                        data -> ((FunctionalCombinator) mapConverter)
                                .apply(data.effectIndex, data.trackingObj));
            } else {
                flow = new MediatorFlow<>(combineFlow,
                        data -> mapConverter.convert(data.trackingObj));
            }
            if (isStickyOn()) {
                flow = new StickyFlowImpl<>(flow, stickyType == StickyType.ON,
                        getCommandStickyGet());
                ((StickyFlowImpl<?>)flow).addCommandReceiver(createCommandReceive());
            } else {
                flow = new FlowWrapper<>(flow, createCommandReceive());
            }
            if (mapFlowSuppressed) {
                return flow;
            }
            result = flow;
        } else {
            result = loadInitialData();
        }
        return resultConverter != null ? resultConverter.convert(result) : result;
    }

    @Override
    Object loadInitialData() {
        if (combineFlow == null) {
            return null;
        }
        CombineData data = combineFlow.get();
        return mapConverter.convert(data.trackingObj);
    }

    @Override
    public CommandType getType() {
        return CommandType.COMBINE;
    }

    private static class CombineData {
        int effectIndex;
        Object[] trackingObj;

        CombineData copy() {
            CombineData data = new CombineData();
            data.effectIndex = this.effectIndex;
            data.trackingObj = Arrays.copyOf(trackingObj, trackingObj.length);
            return data;
        }

        void update(int index, Object obj) {
            effectIndex = index;
            trackingObj[index] = obj;
        }
    }

    private static class CombineFlow implements Flow.StickyFlow<CombineData> {
        CombineData trackingData;
        StickyFlow<?>[] flowArray;
        InternalObserver[] flowObservers;
        ArrayList<Consumer<CombineData>> consumers = new ArrayList<>();
        boolean notifyValueSuppressed;

        CombineFlow(Object[] elementsArray) {
            final int size = elementsArray.length;
            trackingData = new CombineData();
            trackingData.trackingObj = new Object[size];
            flowArray = new StickyFlow<?>[size];
            flowObservers = new InternalObserver[size];
            for (int i = 0; i < size; i++) {
                Object obj = elementsArray[i];
                if (obj instanceof Flow) {
                    if (obj instanceof StickyFlow) {
                        flowArray[i] = (StickyFlow<?>) obj;
                        flowObservers[i] = new InternalObserver(i);
                        continue;
                    }
                    throw new IllegalStateException("impossible obj:" + obj);
                } else {
                    trackingData.trackingObj[i] = obj;
                }
            }
        }

        @Override
        public void addObserver(Consumer<CombineData> consumer) {
            consumers.add(consumer);
            if (consumers.size() == 1) {
                notifyValueSuppressed = true;
                trackingData.effectIndex = -1;
                for (int i = 0; i < flowArray.length; i++) {
                    StickyFlow<Object> stickyFlow = (StickyFlow<Object>) flowArray[i];
                    if (stickyFlow != null) {
                        stickyFlow.addObserver(flowObservers[i]);
                    }
                }
                notifyValueSuppressed = false;
            }
        }

        @Override
        public void removeObserver(Consumer<CombineData> consumer) {
            if (consumers.remove(consumer) && consumers.size() == 0) {
                for (int i = 0; i < flowArray.length; i++) {
                    StickyFlow<Object> stickyFlow = (StickyFlow<Object>) flowArray[i];
                    if (stickyFlow != null) {
                        stickyFlow.removeObserver(flowObservers[i]);
                    }
                }
            }
        }

        private void notifyChangeLocked() {
            if (consumers.size() > 0 && !notifyValueSuppressed) {
                ArrayList<Consumer<CombineData>> consumersClone
                        = (ArrayList<Consumer<CombineData>>) consumers.clone();
                for (int i = 0; i < consumersClone.size(); i++) {
                    consumersClone.get(i).accept(trackingData);
                }
            }
        }

        @Override
        public CombineData get() {
            synchronized (this) {
                return trackingData.copy();
            }
        }

        private class InternalObserver implements Consumer<Object> {

            int index;

            InternalObserver(int index) {
                this.index = index;
            }

            @Override
            public void accept(Object o) {
                synchronized (CombineFlow.this) {
                    trackingData.update(index, o);
                    notifyChangeLocked();
                }
            }
        }
    }
}

@SuppressWarnings("unchecked")
class MediatorFlow<FROM, TO> implements Flow<TO>, Consumer<FROM> {

    private final Flow<FROM> base;
    Function<FROM, TO> mediator;
    private ArrayList<Consumer<TO>> consumers = new ArrayList<>();

    MediatorFlow(Flow<FROM> base,
                         Function<FROM, TO> mediator) {
        this.base = base;
        this.mediator = mediator;
    }

    @Override
    public void addObserver(Consumer<TO> consumer) {
        consumers.add(consumer);
        if (consumers.size() == 1) {
            base.addObserver(this);
        }
    }

    @Override
    public void removeObserver(Consumer<TO> consumer) {
        if (consumers.remove(consumer) && consumers.size() == 0) {
            base.removeObserver(this);
        }
    }

    void addMediator(Function<TO, TO> mediator) {
        this.mediator = this.mediator != null ? this.mediator.andThen(mediator)
                : (Function<FROM, TO>) mediator;
    }

    @Override
    public void accept(FROM value) {
        TO obj;
        if (mediator != null) {
            obj = mediator.apply(value);
        } else {
            obj = (TO) value;
        }
        handleResult(obj);
    }

    void handleResult(TO to) {
        synchronized (this) {
            for (int i = 0; i < consumers.size(); i++) {
                consumers.get(i).accept(to);
            }
        }
    }
}

class EmptyFlowWrapper extends FlowWrapper<Void> implements Flow.EmptyFlow {

    private final HashMap<Runnable, InnerObserver> mObserverMap = new HashMap<>();

    EmptyFlowWrapper(Flow<Void> base, CommandReceive command) {
        super(base, command);
    }

    @Override
    public void addEmptyObserver(Runnable runnable) {
        if (runnable != null && !mObserverMap.containsKey(runnable)) {
            InnerObserver observer = new InnerObserver(runnable);
            mObserverMap.put(runnable, observer);
            addObserver(observer);
        }
    }

    @Override
    public void removeEmptyObserver(Runnable runnable) {
        InnerObserver observer = mObserverMap.remove(runnable);
        if (observer != null) {
            removeObserver(observer);
        }
    }

    private static class InnerObserver implements Consumer<Void> {
        private final Runnable mAction;

        InnerObserver(Runnable action) {
            this.mAction = action;
        }

        @Override
        public void accept(Void aVoid) {
            if (mAction != null) {
                mAction.run();
            }
        }
    }
}

@SuppressWarnings("unchecked")
class FlowWrapper<T> extends MediatorFlow<T, T> {

    ArrayList<CommandReceive> receiverList = new ArrayList<>();

    FlowWrapper(Flow<T> base, CommandReceive command) {
        this(base, null, command);
    }

    FlowWrapper(Flow<T> base, Function<?, ?> function, CommandReceive receiver) {
        super(base, (Function<T, T>) function);
        addCommandReceiver(receiver);
    }

    void addCommandReceiver(CommandReceive receiver) {
        if (receiver != null) {
            receiverList.add(receiver);
            receiver.flowWrapper = (FlowWrapper<Object>) this;
        }
    }

    @Override
    void handleResult(T t) {
        if (receiverList.size() == 0) {
            super.handleResult(t);
        } else {
            receiverList.get(0).invokeWithChain(t);
        }
    }

    void superHandleResult(CommandReceive receiver, T t) {
        CommandReceive nextReceiver = null;
        synchronized (this) {
            for (int i = 0; i < receiverList.size() - 1; i++) {
                if (receiverList.get(i) == receiver) {
                    nextReceiver = receiverList.get(i + 1);
                    break;
                }
            }
        }
        if (nextReceiver != null) {
            nextReceiver.invokeWithChain(t);
            return;
        }
        super.handleResult(t);
    }
}

class StickyFlowImpl<T> extends FlowWrapper<T> implements Flow.StickyFlow<T> {
    private T lastValue;
    private boolean valueReceived;
    private final boolean stickyUseCache;
    private CommandStickyGet headStickyGetCommand;

    StickyFlowImpl(Flow<T> base, boolean stickyUseCache, CommandStickyGet stickyGet) {
        super(base, null);
        this.stickyUseCache = stickyUseCache;
        this.headStickyGetCommand = stickyGet;
    }

    @Override
    public void addObserver(Consumer<T> consumer) {
        super.addObserver(consumer);
        consumer.accept(get());
    }

    @Override
    void handleResult(T value) {
        synchronized (this) {
            if (this.stickyUseCache) {
                valueReceived = true;
                lastValue = value;
            }
        }
        super.handleResult(value);
    }

    void addCommandStickyGet(CommandStickyGet commandStickyGet) {
        commandStickyGet.nextGetCommand = headStickyGetCommand;
        headStickyGetCommand = commandStickyGet;
    }

    @SuppressWarnings("unchecked")
    private T loadInitialStickyData() {
        return (T) headStickyGetCommand.invokeWithChain(null);
    }

    @Override
    public T get() {
        T result = null;
        boolean fetchFirstData = false;
        synchronized (this) {
            if (stickyUseCache && valueReceived) {
                result = lastValue;
            } else {
                fetchFirstData = true;
            }
        }
        if (fetchFirstData) {
            T t = loadInitialStickyData();
            result = mediator != null ? mediator.apply(t) : t;
        }
        synchronized (this) {
            if (stickyUseCache && !valueReceived) {
                valueReceived = true;
                lastValue = result;
            }
        }
        return result;
    }
}