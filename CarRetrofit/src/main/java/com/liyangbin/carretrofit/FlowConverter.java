package com.liyangbin.carretrofit;

public interface FlowConverter<T> extends Converter<Flow<Object>, T>, CommandPredictor {

    @Override
    default boolean checkCommand(Command command) {
        return command.type() == CommandType.TRACK || command.type() == CommandType.COMBINE;
    }
}