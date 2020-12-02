package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.Scope;

import java.lang.annotation.Annotation;

public abstract class CommandGet extends CommandBase {

    Converter<Object, ?> resultConverter;
    CarType type;

    @Override
    void onInit(Annotation annotation) {
        Get get = (Get) annotation;
        propertyId = get.id();
        type = get.type();
        if (type == CarType.ALL) {
            throw new CartrofitGrammarException("Can not use type ALL mode in Get operation");
        }
        resolveArea(get.area());
        resolveResultConverter(key.getGetClass());
    }

    private void resolveResultConverter(Class<?> userReturnClass) {
        Class<?> carReturnClass;
        try {
            carReturnClass = type == CarType.AVAILABILITY ?
                    boolean.class : source.extractValueType(propertyId);
        } catch (Exception e) {
            throw new CartrofitGrammarException(e);
        }
        Converter<?, ?> converter = store.find(this, carReturnClass, userReturnClass);
        resultConverter = (Converter<Object, ?>) converter;
        userDataClass = userReturnClass;
    }

    @Override
    public Object invoke(Object parameter) {
        Object obj = doGet(parameter);
        return resultConverter != null ? resultConverter.convert(obj) : obj;
    }

    public abstract Object doGet(Object parameter);

    @Override
    public CommandType getType() {
        return CommandType.GET;
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
