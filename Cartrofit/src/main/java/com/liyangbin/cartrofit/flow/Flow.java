package com.liyangbin.cartrofit.flow;

import com.liyangbin.cartrofit.Cartrofit;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import androidx.lifecycle.LiveData;
import io.reactivex.Flowable;
import io.reactivex.Observable;

public abstract class Flow<T> {

    private FlowPublisher<T> flowPublisher;
    private boolean subscribeOnce;
    FlowConsumer<T> flowConsumer;
    boolean subscribed;

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

        SimpleFlow(FlowSource<T> source) {
            this.source = source;
        }

        @Override
        protected void onSubscribeStarted(FlowConsumer<T> consumer) {
            source.startWithInjector(this);
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
            flowConsumer.accept(data);
        }

        @Override
        public void done() {
            FlowConsumer<T> safeConsumer;
            synchronized (this) {
                safeConsumer = flowConsumer;
                flowConsumer = null;
                subscribed = false;
            }
            if (safeConsumer != null) {
                safeConsumer.onComplete();
            }
        }

        @Override
        public void error(Throwable error) {
            FlowConsumer<T> safeConsumer;
            synchronized (this) {
                safeConsumer = flowConsumer;
                flowConsumer = null;
                subscribed = false;
            }
            if (safeConsumer != null) {
                safeConsumer.onError(error);
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

    public Flow<T> doOnEach(Consumer<T> onEach) {
        return doOnAction(onEach, null, null);
    }

    public Flow<T> doOnComplete(Runnable onComplete) {
        return doOnAction(null, onComplete, null);
    }

    public Flow<T> doOnError(Consumer<Throwable> onError) {
        return doOnAction(null, null, onError);
    }

    public <E extends Exception> Flow<T> catchException(Class<E> exceptionType, Consumer<E> onError) {
        Objects.requireNonNull(exceptionType);
        return doOnAction(null, null, throwable -> {
            if (exceptionType.isInstance(throwable)) {
                if (onError != null) {
                    onError.accept((E) throwable);
                }
            } else {
                FlowConsumer.defaultThrow(throwable);
            }
        });
    }

    public Flow<T> doOnAction(Consumer<T> onEach, Runnable onComplete, Consumer<Throwable> onError) {
        return new ActionOnFlow<>(this, onEach, onComplete, onError);
    }

    public Flow<T> distinct(BiPredicate<T, T> check) {
        return new DistinctFlow<>(this, check);
    }

    public Flow<T> distinct() {
        return new DistinctFlow<>(this, (t, t2) -> !Objects.equals(t, t2));
    }

    public <T2> Flow<T2> map(Function<T, T2> converter) {
        return new MappedFlow<>(this, converter);
    }

    public <T2> Flow<T2> flatMap(Function<T, Flow<T2>> flatMapper) {
        return new FlatMapFlow<>(this, flatMapper);
    }

    public <T2> Flow<T2> switchMap(Function<T, Flow<T2>> flatMapper) {
        return new SwitchMapFlow<>(this, flatMapper);
    }

    public Flow<T> timeout(int timeoutMillis) {
        return new TimeoutFlow<>(this, timeoutMillis);
    }

    public Flow<T> takeWhile(Predicate<T> check) {
        return new TakeFlow<>(this, check, -1);
    }

    public Flow<T> takeCount(int count) {
        return new TakeFlow<>(this, null, count);
    }

    public Flow<T> take(Predicate<T> check, int count) {
        return new TakeFlow<>(this, check, count);
    }

    public Flow<T> subscribeOn(Executor handler) {
        return new SubscribeOnFlow<>(this, handler);
    }

    public Flow<T> consumeOn(Executor handler) {
        return new ConsumeOnFlow<>(this, handler);
    }

    public FlowPublisher<T> publish() {
        if (flowPublisher == null) {
            synchronized (this) {
                if (flowPublisher == null) {
                    if (subscribed || (subscribeOnce && !isHot())) {
                        throw new RuntimeException("Do transform into publisher before subscribe");
                    }
                    flowPublisher = new FlowPublisher<>(this);
                }
            }
        }
        return flowPublisher;
    }
    // TODO: combine, merge， concatMap, zip

    @SuppressWarnings("unchecked")
    public LiveData<T> toLiveData() {
        return (LiveData<T>) Cartrofit.findFlowConverter(LiveData.class).apply(this);
    }

    @SuppressWarnings("unchecked")
    public Observable<T> toRXObservable() {
        return (Observable<T>) Cartrofit.findFlowConverter(Observable.class).apply(this);
    }

    @SuppressWarnings("unchecked")
    public Flowable<T> toRXFlowable() {
        return (Flowable<T>) Cartrofit.findFlowConverter(Flowable.class).apply(this);
    }
}
