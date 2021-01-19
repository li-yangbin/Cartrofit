package com.liyangbin.cartrofit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.function.Consumer;

class LiveDataConverter {
    static void addSupport() {
        try {
            Class.forName("androidx.lifecycle.LiveData");
            Cartrofit.addGlobalConverter(new LiveDataConverterDefault(),
                    new LiveDataConverterMutable());
        } catch (ClassNotFoundException ignore) {
            // Add LiveData to gradle file to meet LiveData support
        }
    }
}

class LiveDataConverterDefault implements FlowConverter<LiveData<?>> {
    @Override
    public LiveData<?> convert(Flow<?> value) {
        return new FlowLiveData<>(value);
    }
}

class LiveDataConverterMutable implements FlowConverter<MutableLiveData<?>> {
    @Override
    public MutableLiveData<?> convert(Flow<?> value) {
        return new FlowLiveData<>(value);
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
