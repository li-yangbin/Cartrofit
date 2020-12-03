package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

public class CommandImpl extends CommandFlow {

    @Override
    void onInit(CallAdapter<?, ?>.Call call) {
        super.onInit(call);
    }

    @Override
    Object doInvoke(Object parameter) {
        Object result = call.invoke(parameter);
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
    public CommandType getType() {
        return null;
    }
}
