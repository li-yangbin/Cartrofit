package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.ScheduleOn;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.funtion.FlowConverter;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.concurrent.Executor;
import java.util.function.Function;

public class FixedTypeCall<INPUT, OUTPUT> extends Call {

    private Function<Union, INPUT> inputConverter;
    private Function<OUTPUT, Union> outputConverter;
    private Function<OUTPUT, ?> returnConverter;
    private FlowConverter<?> flowConverter;
    private Executor flowSubscribeExecutor;
    private Executor flowConsumeExecutor;

    @Override
    public void onInit() {
        super.onInit();
        AbsContext context = getContext();
        inputConverter = context.findInputConverter(this);
        if (hasCategory(AbsContext.CATEGORY_TRACK)) {
            if (getKey().isCallbackEntry) {
                outputConverter = context.findCallbackOutputConverter(this);
            } else {
                flowConverter = context.findFlowConverter(this);
                returnConverter = context.findReturnOutputConverter(this);
            }

            ScheduleOn scheduleOn = getKey().getAnnotation(ScheduleOn.class);
            if (scheduleOn != null) {
                flowSubscribeExecutor = context.getSubscribeExecutor(scheduleOn.subscribe());
                flowConsumeExecutor = context.getConsumeExecutor(scheduleOn.consume());
            }
        } else {
            returnConverter = context.findReturnOutputConverter(this);
        }
    }

    @Override
    public Object mapInvoke(Union parameter) {
        INPUT input = inputConverter.apply(parameter);
        if (hasCategory(AbsContext.CATEGORY_TRACK)) {
            Flow<OUTPUT> result = doTrackInvoke(input);
            if (flowSubscribeExecutor != null) {
                result = result.subscribeOn(flowSubscribeExecutor);
            }
            if (flowConsumeExecutor != null) {
                result = result.consumeOn(flowConsumeExecutor);
            }
            if (getKey().isCallbackEntry) {
                return result.map(outputConverter);
            } else {
                Flow<?> userFlow = returnConverter != null ? result.map(returnConverter) : result;
                return flowConverter != null ? flowConverter.apply(userFlow) : userFlow;
            }
        } else {
            OUTPUT output = doTypedInvoke(input);
            return returnConverter != null ? returnConverter.apply(output) : output;
        }
    }

    public Flow<OUTPUT> doTrackInvoke(INPUT input) {
        return null;
    }

    public OUTPUT doTypedInvoke(INPUT input) {
        return null;
    }
}
