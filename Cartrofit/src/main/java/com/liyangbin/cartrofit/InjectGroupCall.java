package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Union;
import com.liyangbin.cartrofit.funtion.Union2;

public class InjectGroupCall extends CallGroup<Union2<InjectCall, InjectCall.InjectInfo>> {

    void addChildInjectCall(int parameterIndex, InjectCall call, boolean doSet, boolean doGet) {
        if (doGet || doSet) {
            InjectCall.InjectInfo info = new InjectCall.InjectInfo();
            info.get = doGet;
            info.set = doSet;
            info.parameterIndex = parameterIndex;
            addChildCall(Union.of(call, info));
        }
    }

    @Override
    protected Object doInvoke(Object arg) {
        final int elementCount = getChildCount();
        if (elementCount > 1 && arg instanceof Union) {
            Union<?> union = (Union<?>) arg;
            if (elementCount != union.getCount()) {
                throw new RuntimeException("input parameter count doesn't match expected count"
                        + elementCount + " arg:" + arg);
            }
            for (int i = 0; i < elementCount; i++) {
                Union2<InjectCall, InjectCall.InjectInfo> unit = getChildAt(i);
                unit.value2.target = arg;
                unit.value1.invoke(unit.value2);
            }
        } else if (elementCount == 1 && !(arg instanceof Union)) {
            Union2<InjectCall, InjectCall.InjectInfo> unit = getChildAt(0);
            unit.value2.target = arg;
            unit.value1.invoke(unit.value2);
            unit.value2.target = null;
        } else {
            throw new RuntimeException("impossible situation elementCount:"
                    + elementCount + " arg:" + arg);
        }
        return null;
    }
}
