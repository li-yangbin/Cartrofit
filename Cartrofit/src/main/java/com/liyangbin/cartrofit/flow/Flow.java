package com.liyangbin.cartrofit.flow;

import androidx.lifecycle.LiveData;

import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.funtion.Converter;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import io.reactivex.Flowable;
import io.reactivex.Observable;

public abstract class Flow<T> {

    private FlowPublisher<T> flowPublisher;
    private boolean subscribeOnce;
    private boolean subscribed;

    public void subscribeWithoutResultConcern() {
        subscribe(t -> {
            // ignore
        });
    }

    public final void subscribe(FlowConsumer<T> consumer) {
        synchronized (this) {
            if (subscribeOnce && !isHot()) {
                throw new RuntimeException("Cold flow can only be subscribed once");
            }
            if (subscribed) {
                throw new RuntimeException("Do stop previous subscribe before subscribe again");
            }
            subscribeOnce = true;
            subscribed = true;
        }
        onSubscribeStarted(consumer);
    }

    protected abstract void onSubscribeStarted(FlowConsumer<T> consumer);

    public final void stopSubscribe() {
        synchronized (this) {
            if (!subscribed) {
                return;
            }
            subscribed = false;
        }
        onSubscribeStopped();
    }

    public abstract boolean isHot();

    protected abstract void onSubscribeStopped();

    public interface FlowSource<T> {
        void startWithInjector(Injector<T> injector);
        void finishWithInjector(Injector<T> injector);
        boolean isHot();
    }

    public interface Injector<T> {
        void send(T data);
        void done();
    }

    private static class SimpleFlow<T> extends Flow<T> implements Injector<T> {
        private final FlowSource<T> source;
        private FlowConsumer<T> consumer;

        SimpleFlow(FlowSource<T> source) {
            this.source = source;
        }

        @Override
        protected void onSubscribeStarted(FlowConsumer<T> consumer) {
            source.startWithInjector(this);
            this.consumer = consumer;
        }

        @Override
        public boolean isHot() {
            return source.isHot();
        }

        @Override
        protected void onSubscribeStopped() {
            source.finishWithInjector(this);
        }

        @Override
        public void send(T data) {
            consumer.accept(data);
        }

        @Override
        public void done() {
            consumer.onComplete();
        }
    }

    public static <T> Flow<T> fromSource(FlowSource<T> source) {
        return new SimpleFlow<>(source);
    }

    public static Flow<Integer> delay(int delay) {
        return new IntervalFlow(delay, 0);
    }

    public static Flow<Integer> interval(int interval) {
        return new IntervalFlow(0, interval);
    }

    public static Flow<Integer> interval(int startDelay, int interval) {
        return new IntervalFlow(startDelay, interval);
    }

    public final Flow<T> intercept(InterceptorFlow.Interceptor<T> interceptor) {
        return new InterceptorFlow<>(this, interceptor);
    }

    public final Flow<T> distinct(BiPredicate<T, T> check) {
        return new DistinctFlow<>(this, check);
    }

    public final Flow<T> distinct() {
        return new DistinctFlow<>(this, (t, t2) -> !Objects.equals(t, t2));
    }

    public final <T2> Flow<T2> map(Converter<T, T2> converter) {
        return new MappedFlow<>(this, converter);
    }

    public final <T2> Flow<T2> flatMap(Function<T, Flow<T2>> flatMapper) {
        return new FlatMapFlow<>(this, flatMapper);
    }

    public final <T2> Flow<T2> switchMap(Function<T, Flow<T2>> flatMapper) {
        return new SwitchMapFlow<>(this, flatMapper);
    }

    public final Flow<T> timeout(int timeoutMillis, Runnable timeoutAction) {
        return new TimeoutFlow<>(this, timeoutMillis, timeoutAction);
    }

    public final Flow<T> takeWhile(Predicate<T> check) {
        return new TakeFlow<>(this, check, -1);
    }

    public final Flow<T> takeCount(int count) {
        return new TakeFlow<>(this, null, count);
    }

    public final Flow<T> take(Predicate<T> check, int count) {
        return new TakeFlow<>(this, check, count);
    }

    // TODO: return Flow
    public final FlowPublisher<T> publish() {
        if (flowPublisher == null) {
            synchronized (this) {
                if (flowPublisher == null) {
                    if (subscribed || (subscribeOnce && !isHot())) {
                        throw new RuntimeException("Can not transform into publisher");
                    }
                    flowPublisher = new FlowPublisher<>(this);
                }
            }
        }
        return flowPublisher;
    }
    // TODO: combine, merge

    @SuppressWarnings("unchecked")
    public LiveData<T> toLiveData() {
        return (LiveData<T>) Cartrofit.findFlowConverter(LiveData.class).convert(this);
    }

    @SuppressWarnings("unchecked")
    public Observable<T> toRXObservable() {
        return (Observable<T>) Cartrofit.findFlowConverter(Observable.class).convert(this);
    }

    @SuppressWarnings("unchecked")
    public Flowable<T> toRXFlowable() {
        return (Flowable<T>) Cartrofit.findFlowConverter(Flowable.class).convert(this);
    }
}
