/*
package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.Context;
import com.liyangbin.cartrofit.CallGroup;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowConsumer;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

public class CombineCall extends CallGroup<Call> {

    private int startConcernResultIndex;

    @Override
    public void addChildCall(Call call) {
        super.addChildCall(call.copyByHost(this));
    }

    @Override
    public void onInit() {
        super.onInit();
        boolean resultDetected = false;
        for (int i = 0; i < getChildCount(); i++) {
            Call call = getChildAt(i);
            boolean hasResult = call.hasCategory(Context.CATEGORY_GET | Context.CATEGORY_TRACK);
            if (!resultDetected && hasResult) {
                startConcernResultIndex = i;
                resultDetected = true;
            } else if (resultDetected && !hasResult) {
                throw new RuntimeException("SET child CALL must be declared in front of all " + getKey());
            }
        }
    }

    @Override
    protected Call asCall(Call call) {
        return call;
    }

    @Override
    public Object mapInvoke(Union parameter) {
        for (int i = 0; i < startConcernResultIndex; i++) {
            childInvoke(getChildAt(i), parameter);
        }
        if (startConcernResultIndex == getChildCount()) {
            return null;
        }
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

    private class CombineFlow implements FlowConsumer<Union> {
        CombineData trackingData;
        Flow<Object>[] flowArray;
        int trackElementCount;
        int getElementCount;
        int[] trackIndexArray;
        int[] getIndexArray;
        InternalObserver[] flowObservers;
        ArrayList<Consumer<Union>> consumers = new ArrayList<>();
        boolean notifyValueSuppressed;

        CombineFlow(Union parameter) {
            final int elementCount = getChildCount() - startConcernResultIndex;
            flowArray = new Flow[elementCount];
            trackIndexArray = new int[elementCount];
            getIndexArray = new int[elementCount];
            flowObservers = new InternalObserver[elementCount];
            trackingData = new CombineData(elementCount);

            for (int i = startConcernResultIndex; i < getChildCount(); i++) {
                Call call = getChildAt(i);
                if (call.hasCategory(Context.CATEGORY_TRACK)) {
                    flowArray[i] = childInvoke(call, parameter);
                    flowObservers[i] = new InternalObserver(i);
                    trackIndexArray[trackElementCount++] = i;
                } else if (call.hasCategory(Context.CATEGORY_GET)) {
                    trackingData.trackingObj[i] = childInvoke(call, parameter);
                    getIndexArray[getElementCount++] = i;
                } else {
                    throw new RuntimeException("impossible situation");
                }
            }
        }

        Object castAsUnionIfNeeded() {
            return trackElementCount == 0 ? Union.of(trackingData.trackingObj) : this;
        }

        @Override
        public void addObserver(Consumer<Union> consumer) {
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
        public void removeObserver(Consumer<Union> consumer) {
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
                ArrayList<Consumer<Union>> consumersClone
                        = (ArrayList<Consumer<Union>>) consumers.clone();
                for (int i = 0; i < consumersClone.size(); i++) {
                    consumersClone.get(i).accept(Union.of(trackingData.trackingObj));
                }
            }
        }

        @Override
        public Union get() {
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
*/
