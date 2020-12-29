package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.CallAdapter;
import com.liyangbin.cartrofit.Flow;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

public class CombineCall extends CallGroup<Call> {

    @Override
    public void addChildCall(Call call) {
        super.addChildCall(call.copyByHost(this));
    }

    @Override
    public boolean hasCategory(int category) {
        for (int i = 0; i < getChildCount(); i++) {
            Call call = getChildAt(i);
            if (call.hasCategory(category)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CombineCall copyByHost(Call host) {
        CombineCall copy = (CombineCall) super.copyByHost(host);
        copy.childrenCallList = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            copy.childrenCallList.add(getChildAt(i).copyByHost(host));
        }
        return copy;
    }

    @Override
    protected Object doInvoke(Object parameter) {
        return new CombineFlow(parameter).castAsUnionIfNeeded();
    }

    private static class CombineData {
        int effectIndex;
        Object[] trackingObj;

        CombineData(int size) {
            trackingObj = new Object[size];
        }

        CombineData copy() {
            CombineData data = new CombineData(trackingObj.length);
            data.effectIndex = this.effectIndex;
            data.trackingObj = Arrays.copyOf(trackingObj, trackingObj.length);
            return data;
        }

        void update(int index, Object obj) {
            effectIndex = index;
            trackingObj[index] = obj;
        }
    }

    private class CombineFlow implements Flow.StickyFlow<Union<?>> {
        CombineData trackingData;
        Flow<Object>[] flowArray;
        int trackElementCount;
        int getElementCount;
        int[] trackIndexArray;
        int[] getIndexArray;
        InternalObserver[] flowObservers;
        ArrayList<Consumer<Union<?>>> consumers = new ArrayList<>();
        boolean notifyValueSuppressed;

        CombineFlow(Object input) {
            final int elementCount = getChildCount();
            flowArray = new Flow[elementCount];
            trackIndexArray = new int[elementCount];
            getIndexArray = new int[elementCount];
            flowObservers = new InternalObserver[elementCount];
            trackingData = new CombineData(elementCount);

            for (int i = 0; i < getChildCount(); i++) {
                Call call = getChildAt(i);
                if (call.hasCategory(CallAdapter.CATEGORY_TRACK)) {
                    flowArray[i] = (Flow<Object>) call.invoke(input);
                    flowObservers[i] = new InternalObserver(i);
                    trackIndexArray[trackElementCount++] = i;
                } else if (call.hasCategory(CallAdapter.CATEGORY_GET)) {
                    trackingData.trackingObj[i] = call.invoke(input);
                    getIndexArray[getElementCount++] = i;
                } else {
                    throw new RuntimeException("impossible situation");
                }
            }
//            trackIndexArray = new int[trackCount];
//            getIndexArray = new int[getCount];
//            final int size = elementsArray.length;
//            trackingData = new CombineData();
//            trackingData.trackingObj = new Object[size];
//            flowArray = new StickyFlow<?>[size];
//            flowObservers = new InternalObserver[size];
//            for (int i = 0; i < size; i++) {
//                Object obj = elementsArray[i];
//                if (obj instanceof Flow) {
//                    if (obj instanceof StickyFlow) {
//                        flowArray[i] = (StickyFlow<?>) obj;
//                        flowObservers[i] = new InternalObserver(i);
//                        continue;
//                    }
//                    throw new IllegalStateException("impossible obj:" + obj);
//                } else {
//                    trackingData.trackingObj[i] = obj;
//                }
//            }
        }

        Object castAsUnionIfNeeded() {
            return trackElementCount == 0 ? Union.of(trackingData.trackingObj) : this;
        }

        @Override
        public void addObserver(Consumer<Union<?>> consumer) {
            consumers.add(consumer);
            if (consumers.size() == 1) {
                notifyValueSuppressed = true;
                trackingData.effectIndex = -1;
                for (int i = 0; i < trackElementCount; i++) {
                    Flow<Object> flow = flowArray[trackIndexArray[i]];
                    if (flow != null) {
                        flow.addObserver(flowObservers[trackIndexArray[i]]);
                    }
                }
                notifyValueSuppressed = false;
            }
        }

        @Override
        public void removeObserver(Consumer<Union<?>> consumer) {
            if (consumers.remove(consumer) && consumers.size() == 0) {
                for (int i = 0; i < trackElementCount; i++) {
                    Flow<Object> flow = flowArray[trackIndexArray[i]];
                    if (flow != null) {
                        flow.removeObserver(flowObservers[trackIndexArray[i]]);
                    }
                }
            }
        }

        private void notifyChangeLocked() {
            if (!notifyValueSuppressed && consumers.size() > 0) {
                ArrayList<Consumer<Union<?>>> consumersClone
                        = (ArrayList<Consumer<Union<?>>>) consumers.clone();
                for (int i = 0; i < consumersClone.size(); i++) {
                    consumersClone.get(i).accept(Union.of(trackingData.trackingObj));
                }
            }
        }

        @Override
        public Union<?> get() {
            synchronized (this) {
                return Union.of(trackingData.copy().trackingObj);
            }
        }

        private class InternalObserver implements Consumer<Object> {

            int index;

            InternalObserver(int index) {
                this.index = index;
            }

            @Override
            public void accept(Object o) {
                synchronized (CombineFlow.this) {
                    trackingData.update(index, o);
                    notifyChangeLocked();
                }
            }
        }
    }
}
