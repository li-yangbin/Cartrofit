package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.MethodCategory;
import com.liyangbin.cartrofit.annotation.ScheduleOn;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowSource;

import java.util.concurrent.Executor;
import java.util.function.Function;

public class FixedTypeCall<INPUT, OUTPUT> extends Call {

    private Function<Object[], INPUT> inputConverter;
    private Function<OUTPUT, Object[]> outputConverter;
    private Function<OUTPUT, ?> returnConverter;
    private FlowConverter<?> flowConverter;
    private Executor flowSubscribeExecutor;
    private Executor flowConsumeExecutor;

    @Override
    public void onInit() {
        super.onInit();
        CartrofitContext<?> context = getContext();
        inputConverter = context.findInputConverter(this);
        if (hasCategory(MethodCategory.CATEGORY_TRACK)) {
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
    public Object invoke(Object[] parameter) throws Throwable {
        INPUT input = inputConverter.apply(parameter);
        if (hasCategory(MethodCategory.CATEGORY_TRACK)) {
            Flow<OUTPUT> result = onTrackInvoke(input);
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
            OUTPUT output = onTypedInvoke(input);
            return returnConverter != null ? returnConverter.apply(output) : output;
        }
    }

    public Flow<OUTPUT> onTrackInvoke(INPUT input) throws Throwable {
        return Flow.fromSource(onCreateFlowSource(input));
    }

    public FlowSource<OUTPUT> onCreateFlowSource(INPUT input) {
        return null;
    }

    public OUTPUT onTypedInvoke(INPUT input) throws Throwable {
        return null;
    }
}
