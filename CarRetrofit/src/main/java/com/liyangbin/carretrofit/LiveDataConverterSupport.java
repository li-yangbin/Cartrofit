package com.liyangbin.carretrofit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.function.Consumer;

class LiveDataConverter {
    static void addSupport() {
        try {
            Class.forName("androidx.lifecycle.LiveData");
            CarRetrofit.addGlobalConverter(new LiveDataConverterDefault(),
                    new LiveDataConverterMutable());
        } catch (ClassNotFoundException ignore) {
            // Add LiveData to gradle file to meet LiveData support
        }
    }
}

class LiveDataConverterDefault implements FlowConverter<LiveData<Object>> {
    @Override
    public LiveData<Object> convert(Flow<Object> value) {
        return new FlowLiveData<>(value);
    }

    @Override
    public <NEW_R> LiveData<NEW_R> map(LiveData<Object> raw, Converter<Object, NEW_R> converter) {
        return Transformations.map(raw, converter::convert);
    }
}

class LiveDataConverterMutable implements FlowConverter<MutableLiveData<Object>> {
    @Override
    public MutableLiveData<Object> convert(Flow<Object> value) {
        return new FlowLiveData<>(value);
    }

    @Override
    public <NEW_R> MutableLiveData<NEW_R> map(MutableLiveData<Object> raw,
                                              Converter<Object, NEW_R> converter) {
        return (MutableLiveData<NEW_R>) Transformations.map(raw, converter::convert);
    }
}

class FlowLiveData<T> extends MutableLiveData<T> implements Consumer<T> {
    Flow<T> flow;

    FlowLiveData(Flow<T> flow) {
        this.flow = flow;
    }

    @Override
    protected void onActive() {
        super.onActive();
        flow.addObserver(this);
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        flow.removeObserver(this);
    }

    @Override
    public void accept(T t) {
        postValue(t);
    }
}
