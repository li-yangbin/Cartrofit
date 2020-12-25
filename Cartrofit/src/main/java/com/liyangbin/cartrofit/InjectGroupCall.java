package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Union;

public class InjectGroupCall extends CallGroup<InjectGroupCall.Entry> {

    private final Entry[] parameterInject;

    InjectGroupCall(int parameterCount) {
        parameterInject = new Entry[parameterCount];
    }

    void addChildInjectCall(int parameterIndex, InjectCall call, boolean doSet, boolean doGet) {
        if (doGet || doSet) {
            InjectCall.InjectInfo info = new InjectCall.InjectInfo();
            info.get = doGet;
            info.set = doSet;
            addChildCall(new Entry(parameterIndex, call, info));
        }
    }

    @Override
    public void addChildCall(Entry call) {
        super.addChildCall(call);
        parameterInject[call.parameterIndex] = call;
    }

    @Override
    public void removeChildCall(Entry call) {
        super.removeChildCall(call);
        parameterInject[call.parameterIndex] = null;
    }

    void suppressGetAndExecute(Object object) {
        final int childCount = getChildCount();
        final boolean[] oldGet = new boolean[childCount];
        for (int i = 0; i < childCount; i++) {
            Entry entry = getChildAt(i);
            oldGet[i] = entry.info.get;
            entry.info.get = false;
        }

        invoke(object);

        for (int i = 0; i < childCount; i++) {
            Entry entry = getChildAt(i);
            entry.info.get = oldGet[i];
        }
    }

    void suppressSetAndExecute(Object object) {
        final int childCount = getChildCount();
        final boolean[] oldSet = new boolean[childCount];
        for (int i = 0; i < childCount; i++) {
            Entry entry = getChildAt(i);
            oldSet[i] = entry.info.set;
            entry.info.set = false;
        }

        invoke(object);

        for (int i = 0; i < childCount; i++) {
            Entry entry = getChildAt(i);
            entry.info.set = oldSet[i];
        }
    }

    InjectCall findInjectCallAtParameterIndex(int parameterIndex) {
        InjectGroupCall.Entry entry = parameterIndex >= 0
                && parameterIndex < parameterInject.length ? parameterInject[parameterIndex] : null;
        return entry != null ? entry.call : null;
    }

    static class Entry {
        InjectCall.InjectInfo info;
        InjectCall call;
        int parameterIndex;

        Entry(int index, InjectCall call, InjectCall.InjectInfo info) {
            this.info = info;
            this.call = call;
            this.parameterIndex = index;
        }
    }

    @Override
    protected Object doInvoke(Object parameter) {
        final int elementCount = getChildCount();
        boolean invoke = false;
        if (parameter instanceof Union) {
            Union<?> union = (Union<?>) parameter;
            for (int i = 0; i < union.getCount(); i++) {
                Entry unit = parameterInject[i];
                if (unit != null && (unit.info.get || unit.info.set)) {
                    unit.info.target = union.get(i);
                    unit.call.invoke(unit.info);
                    unit.info.target = null;
                    invoke = true;
                }
            }
        } else if (elementCount == 1 && !(parameter instanceof Union)) {
            Entry unit = parameterInject[0];
            if (unit != null && (unit.info.get || unit.info.set)) {
                unit.info.target = parameter;
                unit.call.invoke(unit.info);
                unit.info.target = null;
                invoke = true;
            }
        } else {
            throw new RuntimeException("impossible situation elementCount:"
                    + elementCount + " parameter:" + parameter);
        }
        if (!invoke) {
            throw new RuntimeException("Invalid input parameter:" + parameter);
        }
        return null;
    }
}
