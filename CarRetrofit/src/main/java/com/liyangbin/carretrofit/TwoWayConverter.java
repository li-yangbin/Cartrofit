package com.liyangbin.carretrofit;

public interface TwoWayConverter<T, R> extends Converter<T, R> {

    @Override
    default R convert(T value) {
        return fromCar2App(value);
    }

    R fromCar2App(T value);

    T fromApp2Car(R r);
}
