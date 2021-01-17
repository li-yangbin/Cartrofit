package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Converter;

public interface FlowConverter<R> extends TypedFlowConverter<Object, R> {
}

interface TypedFlowConverter<T, R> extends Converter<Flow<T>, R> {
}