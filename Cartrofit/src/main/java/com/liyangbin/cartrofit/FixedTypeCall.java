package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.ScheduleOn;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.FlowConverter;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.concurrent.Executor;

public class FixedTypeCall<INPUT, OUTPUT> extends Call {

    private Converter<Union, INPUT> inputConverter;
    private Converter<OUTPUT, Union> outputConverter;
    private Converter<OUTPUT, ?> returnConverter;
    private FlowConverter<?> flowConverter;
    private Executor flowSubscribeExecutor;
    private Executor flowConsumeExecutor;

    @Override
    public void onInit() {
        super.onInit();
        Context context = getContext();
        inputConverter = context.findInputConverter(this);
        if (hasCategory(Context.CATEGORY_TRACK)) {
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
        INPUT input = inputConverter.convert(parameter);
        if (hasCategory(Context.CATEGORY_TRACK)) {
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
                return flowConverter != null ? flowConverter.convert(userFlow) : userFlow;
            }
        } else {
            OUTPUT output = doTypedInvoke(input);
            return returnConverter != null ? returnConverter.convert(output) : output;
        }
    }

    protected Flow<OUTPUT> doTrackInvoke(INPUT input) {
        return null;
    }

    protected OUTPUT doTypedInvoke(INPUT input) {
        return null;
    }
}
