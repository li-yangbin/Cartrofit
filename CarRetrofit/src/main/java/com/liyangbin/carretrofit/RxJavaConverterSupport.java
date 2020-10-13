package com.liyangbin.carretrofit;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;

class RxJavaConverter {
    static void addSupport() {
        try {
            Class.forName("io.reactivex.Observable");
            CarRetrofit.addGlobalConverter(new RxJavaConverterDefault(),
                    new RxJavaConverterFlowable(),
                    new RxJavaConverterSingle(),
                    new RxJavaConverterMaybe(),
                    new RxJavaConverterCompletable());
        } catch (ClassNotFoundException ignore) {
            // Add RxJava to gradle file to meet reactive support
        }
    }
}

class RxJavaConverterDefault implements FlowConverter<Observable<Object>> {

    @Override
    public Observable<Object> convert(Flow<Object> flow) {
        return new FlowObservable(flow);
    }
}

class RxJavaConverterSingle implements FlowConverter<Single<Object>> {

    @Override
    public Single<Object> convert(Flow<Object> flow) {
        return new FlowObservable(flow, true).singleOrError();
    }
}

class RxJavaConverterFlowable implements FlowConverter<Flowable<Object>> {

    @Override
    public Flowable<Object> convert(Flow<Object> flow) {
        return new FlowObservable(flow).toFlowable(BackpressureStrategy.LATEST);
    }
}

class RxJavaConverterMaybe implements FlowConverter<Maybe<Object>> {

    @Override
    public Maybe<Object> convert(Flow<Object> flow) {
        return new FlowObservable(flow, true).singleElement();
    }
}

class RxJavaConverterCompletable implements FlowConverter<Completable> {

    @Override
    public Completable convert(Flow<Object> flow) {
        return new FlowObservable(flow, true).ignoreElements();
    }
}

class FlowObservable extends Observable<Object> implements Consumer<Object>, Disposable {

    Flow<Object> flow;
    AtomicBoolean disposed = new AtomicBoolean();
    boolean singleShot;
    private Observer<? super Object> observer;

    FlowObservable(Flow<Object> flow, boolean singleShot) {
        this.flow = flow;
        this.singleShot = singleShot;
    }

    FlowObservable(Flow<Object> flow) {
        this(flow, false);
    }

    @Override
    protected void subscribeActual(Observer<? super Object> observer) {
        observer.onSubscribe(this);
        if (!disposed.get()) {
            try {
                this.observer = observer;
                flow.addObserver(this);
            } catch (Exception exception) {
                observer.onError(exception);
            }
        }
    }

    @Override
    public void accept(Object t) {
        if (!disposed.get()) {
            try {
                observer.onNext(t);
            } catch (Exception exception) {
                observer.onError(exception);
            }
            if (singleShot) {
                observer.onComplete();
                dispose();
            }
        }
    }

    @Override
    public void dispose() {
        if (!disposed.getAndSet(true)) {
            flow.removeObserver(this);
            flow = null;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}
