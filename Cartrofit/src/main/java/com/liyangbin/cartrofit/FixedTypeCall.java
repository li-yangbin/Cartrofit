package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.flow.Flow;
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
            if (getKey().isCallbackEntry) {
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
//            if (isStickyTrackEnable()) {
//                result = result.sticky();
//            }
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
