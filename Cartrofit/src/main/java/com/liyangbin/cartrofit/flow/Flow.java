package com.liyangbin.cartrofit.flow;

import com.liyangbin.cartrofit.Context;
import com.liyangbin.cartrofit.funtion.Converter;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import androidx.lifecycle.LiveData;
import io.reactivex.Flowable;
import io.reactivex.Observable;

public abstract class Flow<T> {

    private FlowPublisher<T> flowPublisher;
    private FlowConsumer<T> flowConsumer;
    private boolean subscribeOnce;
    private boolean subscribed;

    public void emptySubscribe() {
        subscribe(t -> {
            // ignore
        });
    }

    synchronized boolean isSubscribeStopped() {
        return subscribeOnce && !subscribed;
    }

    static abstract class WrappedFlow<T> extends Flow<T> {
        Flow<T> upStream;

        WrappedFlow(Flow<T> upStream) {
            this.upStream = upStream;
        }

        @Override
        public boolean isHot() {
            return upStream.isHot();
        }

        @Override
        protected void onSubscribeStopped() {
            upStream.stopSubscribe();
        }
    }

    static abstract class WrappedFusedConsumer<T, R> implements FlowConsumer<T> {
        boolean done;
        FlowConsumer<R> downStream;

        WrappedFusedConsumer(FlowConsumer<R> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void onCancel() {
            done = true;
        }

        @Override
        public void onComplete() {
            if (!done) {
                done = true;
                downStream.onComplete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!done) {
                done = true;
                downStream.onError(throwable);
            }
        }
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
        onSubscribeStarted(flowConsumer = consumer);
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
        flowConsumer.onCancel();
        flowConsumer = null;
    }

    public abstract boolean isHot();

    protected abstract void onSubscribeStopped();

    public interface FlowSource<T> {
        void startWithInjector(Injector<T> injector);
        void finishWithInjector(Injector<T> injector);

        default boolean isHot() {
            return true;
        }
    }

    public interface ColdFlowSource<T> extends FlowSource<T> {
        @Override
        default void finishWithInjector(Injector<T> injector) {
            onStop();
        }

        void onStop();

        @Override
        default boolean isHot() {
            return false;
        }
    }

    public interface Injector<T> {
        void send(T data);
        void done();
        void error(Throwable error);
    }

    private static class SimpleFlow<T> extends Flow<T> implements Injector<T> {
        private final FlowSource<T> source;
        private FlowConsumer<T> consumer;
        private boolean expired;

        SimpleFlow(FlowSource<T> source) {
            this.source = source;
        }

        @Override
        protected void onSubscribeStarted(FlowConsumer<T> consumer) {
            expired = false;
            source.startWithInjector(this);
            this.consumer = consumer;
        }

        @Override
        public boolean isHot() {
            return source.isHot();
        }

        @Override
        protected void onSubscribeStopped() {
            if (!expired) {
                expired = true;
                source.finishWithInjector(this);
                consumer.onCancel();
            }
        }

        @Override
        public void send(T data) {
            if (!expired) {
                consumer.accept(data);
            }
        }

        @Override
        public void done() {
            if (!expired) {
                expired = true;
                consumer.onComplete();
            }
        }

        @Override
        public void error(Throwable error) {
            if (!expired) {
                expired = true;
                consumer.onError(error);
            }
        }
    }

    @SafeVarargs
    public static <T> Flow<T> just(T... vt) {
        return new SimpleFlow<>(new ColdFlowSource<T>() {
            boolean expired;
            @Override
            public void startWithInjector(Injector<T> injector) {
                for (int i = 0; i < vt.length && !expired; i++) {
                    injector.send(vt[i]);
                }
                if (!expired) {
                    injector.done();
                }
            }

            @Override
            public void onStop() {
                expired = true;
            }
        });
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

    public final Flow<T> timeout(int timeoutMillis) {
        return new TimeoutFlow<>(this, timeoutMillis, null);
    }

    public final Flow<T> timeout(int timeoutMillis, Predicate<Throwable> timeoutAction) {
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

    public final Flow<T> subscribeOn(Executor handler) {
        return new SubscribeOnFlow<>(this, handler);
    }

    public final Flow<T> consumeOn(Executor handler) {
        return new ConsumeOnFlow<>(this, handler);
    }

    public final FlowPublisher<T> publish() {
        if (flowPublisher == null) {
            synchronized (this) {
                if (flowPublisher == null) {
                    if (subscribed || (subscribeOnce && !isHot())) {
                        throw new RuntimeException("Do transform into publisher before subscribe");
                    }
                    flowPublisher = new FlowPublisher<>(this, true);
                }
            }
        }
        return flowPublisher;
    }
    // TODO: combine, merge

    @SuppressWarnings("unchecked")
    public LiveData<T> toLiveData() {
        return (LiveData<T>) Context.findFlowConverter(LiveData.class).convert(this);
    }

    @SuppressWarnings("unchecked")
    public Observable<T> toRXObservable() {
        return (Observable<T>) Context.findFlowConverter(Observable.class).convert(this);
    }

    @SuppressWarnings("unchecked")
    public Flowable<T> toRXFlowable() {
        return (Flowable<T>) Context.findFlowConverter(Flowable.class).convert(this);
    }
}
