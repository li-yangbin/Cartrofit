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

import java.lang.reflect.Field;
import java.util.function.Consumer;

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

class ObservableConverterField implements FlowConverter<ObservableField<Object>> {

    @Override
    public ObservableField<Object> convert(Flow<Object> value) {
        return new FlowObservableField<>(value);
    }

    private static class FlowObservableField<T> extends ObservableField<T> implements Consumer<T> {
        Flow<T> flow;
        boolean hasCallback;

        public FlowObservableField(Flow<T> flow) {
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
                flow.addObserver(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.removeObserver(this);
            }
        }
    }
}

@WrappedData(type = int.class)
class ObservableConverterInt implements FlowConverter<ObservableInt> {

    @Override
    public ObservableInt convert(Flow<Object> value) {
        return new FlowObservableInt(value);
    }

    private static class FlowObservableInt extends ObservableInt implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;

        FlowObservableInt(Flow<Object> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.addObserver(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.removeObserver(this);
            }
        }

        @Override
        public void accept(Object value) {
            set((Integer) value);
        }
    }
}

@WrappedData(type = byte.class)
class ObservableConverterByte implements FlowConverter<ObservableByte> {

    @Override
    public ObservableByte convert(Flow<Object> value) {
        return new FlowObservableByte(value);
    }

    private static class FlowObservableByte extends ObservableByte implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;

        FlowObservableByte(Flow<Object> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.addObserver(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.removeObserver(this);
            }
        }

        @Override
        public void accept(Object value) {
            set((Byte) value);
        }
    }
}

@WrappedData(type = boolean.class)
class ObservableConverterBoolean implements FlowConverter<ObservableBoolean> {

    @Override
    public ObservableBoolean convert(Flow<Object> value) {
        return new FlowObservableBoolean(value);
    }

    private static class FlowObservableBoolean extends ObservableBoolean implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;

        public FlowObservableBoolean(Flow<Object> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.addObserver(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.removeObserver(this);
            }
        }

        @Override
        public void accept(Object value) {
            set((Boolean) value);
        }
    }
}

@WrappedData(type = float.class)
class ObservableConverterFloat implements FlowConverter<ObservableFloat> {

    @Override
    public ObservableFloat convert(Flow<Object> value) {
        return new FlowObservableFloat(value);
    }

    private static class FlowObservableFloat extends ObservableFloat implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;

        FlowObservableFloat(Flow<Object> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.addObserver(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.removeObserver(this);
            }
        }

        @Override
        public void accept(Object value) {
            set((Float) value);
        }
    }
}

@WrappedData(type = long.class)
class ObservableConverterLong implements FlowConverter<ObservableLong> {

    @Override
    public ObservableLong convert(Flow<Object> value) {
        return new FlowObservableLong(value);
    }

    private static class FlowObservableLong extends ObservableLong implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;

        FlowObservableLong(Flow<Object> flow) {
            this.flow = flow;
        }

        @Override
        public void addOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.addOnPropertyChangedCallback(callback);
            if (!hasCallback) {
                hasCallback = true;
                this.flow.addObserver(this);
            }
        }

        @Override
        public void removeOnPropertyChangedCallback(@NonNull OnPropertyChangedCallback callback) {
            super.removeOnPropertyChangedCallback(callback);
            if (hasCallback && ObservableConverter.isCallbackEmpty(this)) {
                hasCallback = false;
                this.flow.removeObserver(this);
            }
        }

        @Override
        public void accept(Object value) {
            set((Long) value);
        }
    }
}