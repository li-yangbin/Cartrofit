package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.flow.Flow;

import java.util.function.Consumer;
import java.util.function.Function;

@WrappedData(type = boolean.class)
public class MyFlow implements Consumer<Object> {

    Flow<Object> flow;
    Function<Object, Boolean> mapper;
    Consumer<Boolean> consumer;

    public MyFlow(Flow<Object> flow) {
        this.flow = flow;
        this.flow.addObserver(this);
    }

    public void addConsumer(Consumer<Boolean> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void accept(Object t) {
        consumer.accept(mapper != null ? mapper.apply(t) : false);
    }
}

class ConverterImpl implements FlowConverter<MyFlow> {

    @Override
    public MyFlow convert(Flow<Object> value) {
        return new MyFlow(value);
    }
}
