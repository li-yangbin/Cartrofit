package com.android.carretrofit;

import androidx.annotation.NonNull;
import androidx.databinding.BaseObservable;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableByte;
import androidx.databinding.ObservableField;
import androidx.databinding.ObservableFloat;
import androidx.databinding.ObservableInt;
import androidx.databinding.ObservableLong;
import androidx.databinding.PropertyChangeRegistry;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Function;

class ObservableConverter {
    static void addSupport() {
        try {
            Class.forName("androidx.databinding.ObservableField");
            CarRetrofit.addGlobalConverter(new ObservableConverterField(),
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
        return new FlowObservableField<>(value, Function.identity());
    }

    @Override
    public <NEW_R> ObservableField<NEW_R> map(ObservableField<Object> dataObservableField,
                                              Converter<Object, NEW_R> converter) {
        FlowObservableField<NEW_R> field
                = (FlowObservableField<NEW_R>) dataObservableField;
        field.setMapper(converter);
        return field;
    }

    private static class FlowObservableField<T> extends ObservableField<T> implements Consumer<Object> {
        Flow<Object> flow;
        Function<Object, T> mapper;
        boolean hasCallback;

        public FlowObservableField(Flow<Object> flow, Function<Object, T> mapper) {
            this.flow = flow;
            this.mapper = mapper;
            flow.addObserver(this);
        }

        void setMapper(Function<Object, T> mapper) {
            this.mapper = mapper;
        }

        @Override
        public void accept(Object t) {
            set(mapper.apply(t));
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

    @Override
    public <NEW_R> Object map(ObservableInt observableInt, Converter<Object, NEW_R> converter) {
        return FlowObservableInt.map(observableInt, (Function<Object, Integer>) converter);
    }

    private static class FlowObservableInt extends ObservableInt implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;
        Function<Object, Integer> mapper;

        FlowObservableInt(Flow<Object> flow) {
            this.flow = flow;
        }

        static BaseObservable map(BaseObservable observable, Function<Object, Integer> mapper) {
            ((FlowObservableInt)observable).mapper = mapper;
            return observable;
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
            set(mapper != null ? mapper.apply(value) : (Integer) value);
        }
    }
}

@WrappedData(type = byte.class)
class ObservableConverterByte implements FlowConverter<ObservableByte> {

    @Override
    public ObservableByte convert(Flow<Object> value) {
        return new FlowObservableByte(value);
    }

    @Override
    public <NEW_R> Object map(ObservableByte observableByte, Converter<Object, NEW_R> converter) {
        return FlowObservableByte.map(observableByte, (Function<Object, Byte>) converter);
    }

    private static class FlowObservableByte extends ObservableByte implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;
        Function<Object, Byte> mapper;

        FlowObservableByte(Flow<Object> flow) {
            this.flow = flow;
        }

        static BaseObservable map(BaseObservable observable, Function<Object, Byte> mapper) {
            ((FlowObservableByte)observable).mapper = mapper;
            return observable;
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
            set(mapper != null ? mapper.apply(value) : (Byte) value);
        }
    }
}

@WrappedData(type = boolean.class)
class ObservableConverterBoolean implements FlowConverter<ObservableBoolean> {

    @Override
    public ObservableBoolean convert(Flow<Object> value) {
        return new FlowObservableBoolean(value);
    }

    @Override
    public <NEW_R> Object map(ObservableBoolean observableBoolean, Converter<Object, NEW_R> converter) {
        return FlowObservableBoolean.map(observableBoolean, (Function<Object, Boolean>) converter);
    }

    private static class FlowObservableBoolean extends ObservableBoolean implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;
        Function<Object, Boolean> mapper;

        public FlowObservableBoolean(Flow<Object> flow) {
            this.flow = flow;
        }

        static ObservableBoolean map(ObservableBoolean observableBoolean,
                                     Function<Object, Boolean> mapper) {
            ((FlowObservableBoolean)observableBoolean).mapper = mapper;
            return observableBoolean;
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
            set(mapper != null ? mapper.apply(value) : (Boolean) value);
        }
    }
}

@WrappedData(type = float.class)
class ObservableConverterFloat implements FlowConverter<ObservableFloat> {

    @Override
    public ObservableFloat convert(Flow<Object> value) {
        return new FlowObservableFloat(value);
    }

    @Override
    public <NEW_R> Object map(ObservableFloat observableFloat, Converter<Object, NEW_R> converter) {
        return FlowObservableFloat.map(observableFloat, (Function<Object, Float>) converter);
    }

    private static class FlowObservableFloat extends ObservableFloat implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;
        Function<Object, Float> mapper;

        FlowObservableFloat(Flow<Object> flow) {
            this.flow = flow;
        }

        static BaseObservable map(BaseObservable observable, Function<Object, Float> mapper) {
            ((FlowObservableFloat)observable).mapper = mapper;
            return observable;
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
            set(mapper != null ? mapper.apply(value) : (Float) value);
        }
    }
}

@WrappedData(type = long.class)
class ObservableConverterLong implements FlowConverter<ObservableLong> {

    @Override
    public ObservableLong convert(Flow<Object> value) {
        return new FlowObservableLong(value);
    }

    @Override
    public <NEW_R> Object map(ObservableLong observableLong, Converter<Object, NEW_R> converter) {
        return FlowObservableLong.map(observableLong, (Function<Object, Long>) converter);
    }

    private static class FlowObservableLong extends ObservableLong implements Consumer<Object> {
        Flow<Object> flow;
        boolean hasCallback;
        Function<Object, Long> mapper;

        FlowObservableLong(Flow<Object> flow) {
            this.flow = flow;
        }

        static BaseObservable map(BaseObservable observable, Function<Object, Long> mapper) {
            ((FlowObservableLong)observable).mapper = mapper;
            return observable;
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
            set(mapper != null ? mapper.apply(value) : (Long) value);
        }
    }
}