package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.funtion.Converter;

import java.util.Objects;

public class CommandSet extends CommandPropertyOperation<Set> {
    BuildInValue buildInValue;
    Converter<Object, ?> argConverter;

    CommandSet(CarPropertyAdapter propertyDataSource) {
        this.source = Objects.requireNonNull(propertyDataSource);
    }

    @Override
    void onInit(Set set) {
        this.propertyId = set.id();
        buildInValue = BuildInValue.build(set.value());
        resolveArea(set.area());

        if (buildInValue != null) {
            return;
        }
        resolveArgConverter(key.getSetClass());
    }

    @SuppressWarnings("unchecked")
    private void resolveArgConverter(Class<?> userArgClass) {
        argConverter = (Converter<Object, ?>) store.find(this,
                userArgClass, source.extractValueType(propertyId));
        userDataClass = userArgClass;
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
        source.set(propertyId, area, collectArgs(parameter));
        return null;
    }

    @Override
    public CommandType getType() {
        return CommandType.SET;
    }

    @Override
    public Class<?> getInputType() {
        return key.getSetClass();
    }
}
