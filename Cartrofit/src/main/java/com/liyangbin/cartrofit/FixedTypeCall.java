package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.Union;

public class FixedTypeCall<INPUT, OUTPUT> extends Call {

    private Converter<Union, INPUT> inputConverter;
    private Converter<OUTPUT, Union> outputConverter;
    private TypedFlowConverter<OUTPUT, ?> flowConverter;

    @Override
    public void onInit() {
        super.onInit();
        CallAdapter adapter = getAdapter();
        inputConverter = adapter.findInputConverter(this);
        if (hasCategory(CallAdapter.CATEGORY_TRACK)) {
            if (key.isCallbackEntry) {
                outputConverter = adapter.findOutputConverter(this);
            } else {
                flowConverter = adapter.findFlowConverter(this);
            }
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
                return flowConverter != null ? flowConverter.convert(result) : null;
            }
        } else {
            return doTypedInvoke(input);
        }
    }

    protected Flow<OUTPUT> doTrackInvoke(INPUT arg) {
        return null;
    }

    protected OUTPUT doTypedInvoke(INPUT arg) {
        return null;
    }
}
