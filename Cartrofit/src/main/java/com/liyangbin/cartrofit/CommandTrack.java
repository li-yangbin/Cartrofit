package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.funtion.Converter;

import java.lang.annotation.Annotation;

@SuppressWarnings("unchecked")
class CommandTrack extends CommandFlow {
    CarType type;
    Annotation annotation;

    @Override
    void setRestoreCommand(CommandBase restoreCommand) {
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
        return stable + " " + super.toCommandString();
    }
}
