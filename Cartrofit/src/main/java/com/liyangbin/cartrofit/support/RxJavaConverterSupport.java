package com.liyangbin.cartrofit.support;

import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.FlowConverter;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowConsumer;

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
            Cartrofit.addGlobalConverter(new RxJavaConverterDefault(),
                    new RxJavaConverterFlowable(),
                    new RxJavaConverterSingle(),
                    new RxJavaConverterMaybe(),
                    new RxJavaConverterCompletable());
        } catch (ClassNotFoundException ignore) {
            // Add RxJava to gradle file to meet reactive support
        }
    }
}

class RxJavaConverterDefault implements FlowConverter<Observable<?>> {

    @Override
    public Observable<?> apply(Flow<?> flow) {
        return new FlowObservable<>(flow);
    }
}

class RxJavaConverterSingle implements FlowConverter<Single<?>> {

    @Override
    public Single<?> apply(Flow<?> flow) {
        return new FlowObservable<>(flow, true).singleOrError();
    }
}

class RxJavaConverterFlowable implements FlowConverter<Flowable<?>> {

    @Override
    public Flowable<?> apply(Flow<?> flow) {
        return new FlowObservable<>(flow).toFlowable(BackpressureStrategy.LATEST);
    }
}

class RxJavaConverterMaybe implements FlowConverter<Maybe<?>> {

    @Override
    public Maybe<?> apply(Flow<?> flow) {
        return new FlowObservable<>(flow, true).singleElement();
    }
}

class RxJavaConverterCompletable implements FlowConverter<Completable> {

    @Override
    public Completable apply(Flow<?> flow) {
        return new FlowObservable<>(flow, true).ignoreElements();
    }
}

class FlowObservable<T> extends Observable<T> implements FlowConsumer<T>, Disposable {

    Flow<T> flow;
    boolean disposed;
    boolean singleShot;
    private Observer<? super T> observer;

    FlowObservable(Flow<T> flow, boolean singleShot) {
        this.flow = flow;
        this.singleShot = singleShot;
    }

    FlowObservable(Flow<T> flow) {
        this(flow, false);
    }

    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        observer.onSubscribe(this);
        if (!disposed) {
            try {
                this.observer = observer;
                flow.subscribe(this);
            } catch (Exception exception) {
                observer.onError(exception);
            }
        }
    }

    @Override
    public void accept(T t) {
        if (!disposed) {
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
    public void onError(Throwable throwable) {
        if (!disposed) {
            observer.onError(throwable);
        }
    }

    @Override
    public void onComplete() {
        if (!disposed) {
            observer.onComplete();
        }
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            flow.stopSubscribe();
            flow = null;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
