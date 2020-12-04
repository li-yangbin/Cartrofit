package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import static com.liyangbin.cartrofit.CarType.AVAILABILITY;
import static com.liyangbin.cartrofit.CarType.VALUE;

public class CommandImpl extends CommandFlow {

    Converter<Object, ?> argConverter;

    @Override
    void onInit(CallAdapter<?, ?>.Call call) {
        super.onInit(call);

    }

    @Override
    Object doInvoke(boolean isFlowInvoke, Object parameter) {
        Object result = call.invoke(argConverter != null ?
                argConverter.apply(parameter) : parameter);
        if (isFlowInvoke) {
            result = installFlowWithCommand(Flow.map((Flow<Object>)result, mapConverter));
            if (mapFlowSuppressed) {
                return result;
            }
        }
        return resultConverter != null ? resultConverter.convert(result) : result;
    }

    @Override
    public CommandType getType() {
        return null;
    }
}
