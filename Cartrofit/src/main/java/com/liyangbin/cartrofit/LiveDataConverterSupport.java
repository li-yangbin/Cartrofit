package com.liyangbin.cartrofit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowConsumer;

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
    public LiveData<?> convert(Flow<?> flow) {
        if (!flow.isHot()) {
            throw new IllegalStateException("Can not convert cold flow:" + flow + " to LiveData");
        }
        return new FlowLiveData<>(flow);
    }
}

class LiveDataConverterMutable implements FlowConverter<MutableLiveData<?>> {
    @Override
    public MutableLiveData<?> convert(Flow<?> flow) {
        if (!flow.isHot()) {
            throw new IllegalStateException("Can not convert cold flow:" + flow + " to LiveData");
        }
        return new FlowLiveData<>(flow);
    }
}

class FlowLiveData<T> extends MutableLiveData<T> implements FlowConsumer<T> {
    Flow<T> flow;

    FlowLiveData(Flow<T> flow) {
        this.flow = flow;
    }

    @Override
    protected void onActive() {
        super.onActive();
        flow.subscribe(this);
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        flow.stopSubscribe();
    }

    @Override
    public void accept(T t) {
        setValue(t);
    }
}