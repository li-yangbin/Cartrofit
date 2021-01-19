package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.Union;

public class FixedTypeCall<INPUT, OUTPUT> extends Call {

    private Converter<Union, INPUT> inputConverter;
    private Converter<OUTPUT, Union> outputConverter;
    private Converter<OUTPUT, ?> returnConverter;
    private FlowConverter<?> flowConverter;

    @Override
    public void onInit() {
        super.onInit();
        CallAdapter adapter = getAdapter();
        inputConverter = adapter.findInputConverter(this);
        if (hasCategory(CallAdapter.CATEGORY_TRACK)) {
            if (key.isCallbackEntry) {
                outputConverter = adapter.findCallbackOutputConverter(this);
            } else {
                flowConverter = adapter.findFlowConverter(this);
                returnConverter = adapter.findReturnOutputConverter(this);
            }
        } else {
            returnConverter = adapter.findReturnOutputConverter(this);
        }
    }

    @Override
    public Object mapInvoke(Union parameter) {
        INPUT input = inputConverter.convert(parameter);
        if (hasCategory(CallAdapter.CATEGORY_TRACK)) {
            Flow<OUTPUT> result = doTrackInvoke(input);
            if (isStickyTrackEnable()) {
                result = result.sticky();
            }
            result = result.untilReceive(onReceiveCall);
            if (key.isCallbackEntry) {
                return result.map(outputConverter);
            } else {
                if (returnConverter != null) {
                    return flowConverter != null ? flowConverter.convert(result.map(returnConverter)) : null;
                } else {
                    return flowConverter != null ? flowConverter.convert(result) : null;
                }
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
