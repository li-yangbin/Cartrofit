package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Converter;

import java.util.ArrayList;

public final class ConverterFactory {

    private final ArrayList<ConverterBuilder<?>.ConvertSolution> mSolutionList = new ArrayList<>();
    private ConverterFactory mParentFactory;
    private final Cartrofit mCartrofit;

    ConverterFactory(Cartrofit cartrofit) {
        mCartrofit = cartrofit;
    }

    ConverterFactory(ConverterFactory parentFactory) {
        mParentFactory = parentFactory;
        mCartrofit = parentFactory.mCartrofit;
    }

    public <SERIAL_TYPE> ConverterBuilder<SERIAL_TYPE> builder(Class<SERIAL_TYPE> serialTypeClass) {
        return new ConverterBuilder<SERIAL_TYPE>(serialTypeClass) {
            @Override
            void onCommit(ConvertSolution solution) {
                mSolutionList.add(solution);
            }
        };
    }

    Converter<?, ?> findInputConverterByKey(Cartrofit.Key key) {
        for (int i = 0; i < mSolutionList.size(); i++) {
            Converter<?, ?> converter = mSolutionList.get(i).findInputConverterByKey(key);
            if (converter != null) {
                return converter;
            }
        }
        return mParentFactory != null ? mParentFactory.findInputConverterByKey(key) : null;
    }

    Converter<?, ?> findOutputConverterByKey(Cartrofit.Key key, boolean flowMap) {
        for (int i = 0; i < mSolutionList.size(); i++) {
            Converter<?, ?> converter = mSolutionList.get(i).findOutputConverterByKey(key, flowMap);
            if (converter != null) {
                return converter;
            }
        }
        return mParentFactory != null ? mParentFactory.findOutputConverterByKey(key, flowMap) : null;
    }

    FlowConverter<?> findFlowConverter(Cartrofit.Key key) {
        if (key.isCallbackEntry) {
            return null;
        }
        return mCartrofit.findFlowConverter(key.getReturnType());
    }
}
