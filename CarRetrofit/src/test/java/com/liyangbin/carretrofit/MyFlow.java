package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.WrappedData;

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

    MyFlow setMapper(Function<Object, Boolean> function) {
        mapper = function;
        return this;
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

    @Override
    public <NEW_R> MyFlow map(MyFlow tMyFlow, Converter<Object, NEW_R> converter) {
        return tMyFlow.setMapper((Function<Object, Boolean>) converter);
    }
}
