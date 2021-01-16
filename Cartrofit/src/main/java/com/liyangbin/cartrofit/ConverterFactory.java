package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.ArrayList;

public final class ConverterFactory {

    private final ArrayList<ConverterBuilder<?>.ConvertSolution> mSolutionList = new ArrayList<>();
    private ConverterFactory mParentFactory;
    private final Cartrofit mCartrofit;
    private ParameterContext mContext;

    ConverterFactory(Cartrofit cartrofit) {
        mCartrofit = cartrofit;
    }

    public ConverterFactory(ConverterFactory parentFactory, ParameterContext context) {
        mParentFactory = parentFactory;
        mCartrofit = parentFactory.mCartrofit;
        mContext = context;
    }

    public Converter<Union, ?> findInputConverterByCall(Call call) {
        for (int i = 0; i < mSolutionList.size(); i++) {
            Cartrofit.ParameterGroup targetGroup = mContext != null ?
                    mContext.extractParameterFromCall(call) : call.getKey();
            Converter<?, ?> converter = mSolutionList.get(i).findInputConverter(targetGroup, call.getKey());
            if (converter != null) {
                return converter;
            }
        }
        return mParentFactory != null ? mParentFactory.findInputConverterByCall(call) : null;
    }

    public Converter<?, Union> findOutputConverterByCall(Call call, boolean flowMap) {
        for (int i = 0; i < mSolutionList.size(); i++) {
            Cartrofit.ParameterGroup targetGroup = mContext != null ?
                    mContext.extractParameterFromCall(call) : call.getKey();
            Converter<?, ?> converter = mSolutionList.get(i).findOutputConverter(targetGroup,
                    call.getKey(), flowMap);
            if (converter != null) {
                return converter;
            }
        }
        return mParentFactory != null ? mParentFactory.findOutputConverterByCall(call, flowMap) : null;
    }

    public FlowConverter<?> findFlowConverter(Call call) {
        if (call.getKey().isCallbackEntry) {
            return null;
        }
        return mCartrofit.findFlowConverter(call.getKey().getReturnType());
    }
}
