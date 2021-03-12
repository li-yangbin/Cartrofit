package com.liyangbin.cartrofit.carproperty;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarSensorConfig;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.content.Context;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.FixedTypeCall;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.solution.Accumulator;
import com.liyangbin.cartrofit.solution.ParaVal;
import com.liyangbin.cartrofit.solution.SolutionProvider;

import java.lang.annotation.Annotation;
import java.util.Objects;

public class CarSensorContext extends CarAbstractContext<CarSensorManager,
        CarSensorContext.SensorRegisterKey, CarSensorEvent> {

    public CarSensorContext(Context context) {
        super(new DefaultCarServiceAccess<>(context, Car.SENSOR_SERVICE));
    }

    public static class SensorRegisterKey {
        int type;
        int rate;

        SensorRegisterKey(int type, int rate) {
            this.type = type;
            this.rate = rate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SensorRegisterKey that = (SensorRegisterKey) o;
            return type == that.type &&
                    rate == that.rate;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, rate);
        }
    }

    @Override
    public CarFlowSource onCreateFlowSource(SensorRegisterKey sensorRegisterKey) {
        return new SensorFlowSource(sensorRegisterKey);
    }

    @Override
    public void onGlobalRegister(boolean register) throws CarNotConnectedException {
        // ignore
    }

    @Override
    public void onRegister(boolean register, CarFlowSource flowSource) throws CarNotConnectedException {
        if (register) {
            getManagerLazily().registerListener((SensorFlowSource) flowSource,
                    flowSource.sourceKey.type, flowSource.sourceKey.rate);
        } else {
            getManagerLazily().unregisterListener((SensorFlowSource) flowSource);
        }
    }

    @Override
    public SolutionProvider onProvideCallSolution() {
        SolutionProvider solutionProvider = new SolutionProvider();

        solutionProvider.create(Sensor.class, SensorGetCall.class)
                .provide((sensor, key) -> new SensorGetCall(sensor.type()));

        solutionProvider.createWithFixedType(SensorTrack.class, SensorTrackCall.class)
                .provideAndBuildParameter((sensorTrack, key) -> new SensorTrackCall(sensorTrack.type(), sensorTrack.rate())).take(CarSensorEvent.class).output(new Accumulator<Annotation, CarSensorEvent, CarSensorEvent>() {
            @Override
            public CarSensorEvent advance(Annotation annotation, CarSensorEvent old, ParaVal<CarSensorEvent> para) {
                para.set(old);
                return old;
            }
        }).build().take(int[].class).output((annotation, old, para) -> {
            para.set(old.intValues);
            return old;
        }).build().take(float[].class).output((annotation, old, para) -> {
            para.set(old.floatValues);
            return old;
        }).build().take(long[].class).output((annotation, old, para) -> {
            para.set(old.longValues);
            return old;
        }).build().take(int.class).output((annotation, old, para) -> {
            para.set(old.intValues != null && old.intValues.length > 0 ? old.intValues[0] : 0);
            return old;
        }).build().take(float.class).output((annotation, old, para) -> {
            para.set(old.floatValues != null && old.floatValues.length > 0 ? old.floatValues[0] : 0);
            return old;
        }).build().take(long.class).output((annotation, old, para) -> {
            para.set(old.longValues != null && old.longValues.length > 0 ? old.longValues[0] : 0);
            return old;
        }).buildAndCommit();

        return solutionProvider;
    }

    private class SensorGetCall extends Call {
        int type;
        boolean readConfig;
        boolean readAvailability;

        private SensorGetCall(int type) {
            this.type = type;
        }

        @Override
        public void onInit() {
            super.onInit();
            if (getKey().isAnnotationPresent(Availability.class)) {
                readAvailability = true;
            } else {
                readConfig = getKey().getReturnType().equals(CarSensorConfig.class);
            }
        }

        @Override
        public Object invoke(Object[] parameter) throws Throwable {
            if (readAvailability) {
                return getManagerLazily().isSensorSupported(type);
            } else if (readConfig) {
                return getManagerLazily().getSensorConfig(type);
            } else {
                return getManagerLazily().getLatestSensorEvent(type);
            }
        }
    }

    private class SensorTrackCall extends FixedTypeCall<Void, CarSensorEvent> {
        SensorRegisterKey registerKey;

        private SensorTrackCall(int type, int rate) {
            registerKey = new SensorRegisterKey(type, rate);
        }

        @Override
        public void onInit() {
            super.onInit();
            if (getKey().isCallbackEntry && getKey().getParameterCount() == 0) {
                throw new CartrofitGrammarException("Must declare one parameter " + getKey());
            }
        }

        @Override
        public Flow<CarSensorEvent> onTrackInvoke(Void aVoid) {
            return Flow.fromSource(getOrCreateFlowSource(registerKey));
        }
    }

    private class SensorFlowSource extends CarFlowSource implements
            CarSensorManager.OnSensorChangedListener {

        SensorFlowSource(SensorRegisterKey registerKey) {
            super(registerKey);
        }

        @Override
        public void onSensorChanged(CarSensorEvent carSensorEvent) {
            publish(carSensorEvent);
        }

        @Override
        public CarSensorEvent loadInitData() throws CarNotConnectedException {
            return getManagerLazily().getLatestSensorEvent(sourceKey.type);
        }
    }
}
