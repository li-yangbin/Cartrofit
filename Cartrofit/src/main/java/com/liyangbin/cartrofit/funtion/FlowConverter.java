package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.flow.Flow;

import java.util.function.Function;

public interface FlowConverter<R> extends Function<Flow<?>, R> {
}