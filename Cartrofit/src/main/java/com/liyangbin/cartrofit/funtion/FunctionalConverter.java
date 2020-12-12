package com.liyangbin.cartrofit.funtion;

public interface FunctionalConverter<R> extends Converter<Object[], R> {
    @Override
    default R convert(Object[] objects) {
        return convert(-1, objects);
    }

    R convert(int effectIndex, Object[] objects);
}
