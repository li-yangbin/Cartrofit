package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Flow;
import com.liyangbin.cartrofit.funtion.Converter;

//class EmptyFlowWrapper extends FlowWrapper<Void> implements Flow.EmptyFlow {
//
//    private final HashMap<Runnable, InnerObserver> mObserverMap = new HashMap<>();
//
//    EmptyFlowWrapper(Flow<?> base, CommandReceive command) {
//        super((Flow<Void>) base, command);
//    }
//
//    @Override
//    public void addEmptyObserver(Runnable runnable) {
//        if (runnable != null && !mObserverMap.containsKey(runnable)) {
//            InnerObserver observer = new InnerObserver(runnable);
//            mObserverMap.put(runnable, observer);
//            addObserver(observer);
//        }
//    }
//
//    @Override
//    public void removeEmptyObserver(Runnable runnable) {
//        InnerObserver observer = mObserverMap.remove(runnable);
//        if (observer != null) {
//            removeObserver(observer);
//        }
//    }
//
//    private static class InnerObserver implements Consumer<Void> {
//        private final Runnable mAction;
//
//        InnerObserver(Runnable action) {
//            this.mAction = action;
//        }
//
//        @Override
//        public void accept(Void aVoid) {
//            if (mAction != null) {
//                mAction.run();
//            }
//        }
//    }
//}

@SuppressWarnings({"unchecked", "rawtypes"})
public class MediatorFlow extends FlowWrapper {

    Converter mediator;

    public MediatorFlow(Flow<?> base, Converter mediator) {
        super(base);
        this.mediator = mediator;
    }

    public MediatorFlow addMediator(Converter<?, ?> mediator) {
        this.mediator = this.mediator != null ? this.mediator.andThen(mediator) : mediator;
        return this;
    }

    @Override
    public void accept(Object value) {
        Object obj;
        if (mediator != null) {
            obj = mediator.convert(value);
        } else {
            obj = value;
        }
        super.accept(obj);
    }
}

