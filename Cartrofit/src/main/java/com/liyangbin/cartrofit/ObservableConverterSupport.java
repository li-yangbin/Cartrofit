package com.liyangbin.cartrofit;

import androidx.annotation.NonNull;
import androidx.databinding.BaseObservable;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableByte;
import androidx.databinding.ObservableField;
import androidx.databinding.ObservableFloat;
import androidx.databinding.ObservableInt;
import androidx.databinding.ObservableLong;
import androidx.databinding.PropertyChangeRegistry;

import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowConsumer;
import com.liyangbin.cartrofit.funtion.FlowConverter;

import java.lang.reflect.Field;

class ObservableConverter {
    static void addSupport() {
        try {
            Class.forName("androidx.databinding.ObservableField");
            Cartrofit.addGlobalConverter(new ObservableConverterField(),
                    new ObservableConverterInt(),
                    new ObservableConverterByte(),
                    new ObservableConverterBoolean(),
                    new ObservableConverterFloat(),
                    new ObservableConverterLong());
        } catch (Throwable ignore) {
            // Enable data binding in gradle file to meet ObservableField support
        }
    }

    static boolean isCallbackEmpty(BaseObservable observable) {
        try {
            Field field = BaseObservable.class.getDeclaredField("mCallbacks");
            field.setAccessible(true);
            PropertyChangeRegistry registry = (PropertyChangeRegistry) field.get(observable);
            return registry == null || registry.isEmpty();
        } catch (ReflectiveOperationException impossible) {
            throw new RuntimeException(impossible);
        }
    }
}

class ObservableConverterField implements FlowConverter<ObservableField<?>> {

    @Override
    public ObservableField<?> apply(Flow<?> flow) {
        if (!flow.isHot()) {
            throw new IllegalStateException("Can not apply cold flow:" + flow + " to ObservableField");
        }
        return new FlowObservableField<>(flow);
    }

    private static class FlowObservableField<T> extends ObservableField<T> implements FlowConsumer<T> {
        Flow<T> flow;
        boolean hasCallback;

        FlowObservableField(Flow<T> flow) {
            this.flow = flow;
        }

        @Override
        public void accept(T t) {
            set(t);
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                flow.subscribe(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.stopSubscribe();
            }
        }
    }
}

@WrappedData(type = int.class)
class ObservableConverterInt implements FlowConverter<ObservableInt> {

    @Override
    public ObservableInt apply(Flow<?> flow) {
        if (!flow.isHot()) {
            throw new IllegalStateException("Can not apply cold flow:" + flow + " to ObservableInt");
        }
        return new FlowObservableInt<>(flow);
    }

    private static class FlowObservableInt<T> extends ObservableInt implements FlowConsumer<T> {
        Flow<T> flow;
        boolean hasCallback;

        FlowObservableInt(Flow<T> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.subscribe(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.stopSubscribe();
            }
        }

        @Override
        public void accept(T value) {
            set((Integer) value);
        }
    }
}

@WrappedData(type = byte.class)
class ObservableConverterByte implements FlowConverter<ObservableByte> {

    @Override
    public ObservableByte apply(Flow<?> flow) {
        if (!flow.isHot()) {
            throw new IllegalStateException("Can not apply cold flow:" + flow + " to ObservableByte");
        }
        return new FlowObservableByte<>(flow);
    }

    private static class FlowObservableByte<T> extends ObservableByte implements FlowConsumer<T> {
        Flow<T> flow;
        boolean hasCallback;

        FlowObservableByte(Flow<T> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.subscribe(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.stopSubscribe();
            }
        }

        @Override
        public void accept(T value) {
            set((Byte) value);
        }
    }
}

@WrappedData(type = boolean.class)
class ObservableConverterBoolean implements FlowConverter<ObservableBoolean> {

    @Override
    public ObservableBoolean apply(Flow<?> flow) {
        if (!flow.isHot()) {
            throw new IllegalStateException("Can not apply cold flow:" + flow + " to ObservableBoolean");
        }
        return new FlowObservableBoolean<>(flow);
    }

    private static class FlowObservableBoolean<T> extends ObservableBoolean implements FlowConsumer<T> {
        Flow<T> flow;
        boolean hasCallback;

        FlowObservableBoolean(Flow<T> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.subscribe(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.stopSubscribe();
            }
        }

        @Override
        public void accept(T value) {
            set((Boolean) value);
        }
    }
}

@WrappedData(type = float.class)
class ObservableConverterFloat implements FlowConverter<ObservableFloat> {

    @Override
    public ObservableFloat apply(Flow<?> flow) {
        if (!flow.isHot()) {
            throw new IllegalStateException("Can not apply cold flow:" + flow + " to ObservableFloat");
        }
        return new FlowObservableFloat<>(flow);
    }

    private static class FlowObservableFloat<T> extends ObservableFloat implements FlowConsumer<T> {
        Flow<T> flow;
        boolean hasCallback;

        FlowObservableFloat(Flow<T> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.subscribe(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.stopSubscribe();
            }
        }

        @Override
        public void accept(T value) {
            set((Float) value);
        }
    }
}

@WrappedData(type = long.class)
class ObservableConverterLong implements FlowConverter<ObservableLong> {

    @Override
    public ObservableLong apply(Flow<?> flow) {
        if (!flow.isHot()) {
            throw new IllegalStateException("Can not apply cold flow:" + flow + " to ObservableLong");
        }
        return new FlowObservableLong<>(flow);
    }

    private static class FlowObservableLong<T> extends ObservableLong implements FlowConsumer<T> {
        Flow<T> flow;
        boolean hasCallback;

        FlowObservableLong(Flow<T> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.subscribe(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.stopSubscribe();
            }
        }

        @Override
        public void accept(T value) {
            set((Long) value);
        }
    }
}