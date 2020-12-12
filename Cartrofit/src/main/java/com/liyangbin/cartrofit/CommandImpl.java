package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Converter;

public class CommandImpl extends CommandFlow {

    CallAdapter.Call call;

    Converter inputConverter;
    Converter outputConverter;
    Converter flowConverter;

    void initCommand(CallAdapter.Call call, Cartrofit.Key key, ConverterFactory scopeFactory) {
//        init(call.getAnnotation(), key);
        this.call = call;

        ConverterFactory callIndividualFactory = new ConverterFactory(scopeFactory);
        call.buildConvertSolution(callIndividualFactory);

        if (inputConverter == null) {
            inputConverter = callIndividualFactory.findInputConverterByKey(key);
        }
        if (outputConverter == null) {
            outputConverter = callIndividualFactory.findOutputConverterByKey(key);
        }
    }

    @Override
    Object doInvoke(boolean isFlowInvoke, Object parameter) {
        Object result = call.invoke(inputConverter != null ?
                inputConverter.apply(parameter) : parameter);
        if (isFlowInvoke) {
            result = installFlowWithCommand(Flow.map((Flow<Object>)result, outputConverter));
            if (mapFlowSuppressed) {
                return result;
            }
            return flowConverter != null ? flowConverter.convert(result) : result;
        } else {
            return outputConverter != null ? outputConverter.convert(result) : result;
        }
    }

    @Override
    public CommandType getType() {
        return null;
    }
}
