package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;

import java.lang.annotation.Annotation;

public abstract class CommandSet extends CommandImpl {
    BuildInValue buildInValue;
    Converter<Object, ?> argConverter;

    @Override
    void onInit(Annotation annotation) {
        Set set = (Set) annotation;
        this.propertyId = set.id();
        buildInValue = BuildInValue.build(set.value());
        resolveArea(set.area());

        if (buildInValue != null) {
            return;
        }
        resolveArgConverter(key.getSetClass());
    }

    private void resolveArgConverter(Class<?> userArgClass) {
        if (buildInValue != null) {
            return;
        }
        Class<?> carArgClass;
        try {
            carArgClass = source.extractValueType(propertyId);
        } catch (Exception e) {
            throw new CartrofitGrammarException(e);
        }
        Converter<?, ?> converter = store.find(this, userArgClass, carArgClass);
        userDataClass = userArgClass;
        if (converter != null) {
            argConverter = (Converter<Object, ?>) converter;
        }
    }

    Object collectArgs(Object parameter) {
        if (buildInValue != null) {
            return buildInValue.extractValue(source.extractValueType(propertyId));
        }
        return argConverter != null && parameter != null ?
                argConverter.convert(parameter) : parameter;
    }

    @Override
    public Object invoke(Object parameter) {
        doSet(collectArgs(parameter));
        return null;
    }

    public abstract void doSet(Object parameter);

    @Override
    public CommandType getType() {
        return CommandType.SET;
    }

    @Override
    public Class<?> getInputType() {
        return key.getSetClass();
    }

    @Override
    String toCommandString() {
        String stable = "id:0x" + Integer.toHexString(getPropertyId())
                + (area != Scope.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "");
        return stable + " " + super.toCommandString();
    }
}
