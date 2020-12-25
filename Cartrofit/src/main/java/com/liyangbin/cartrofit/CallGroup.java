package com.liyangbin.cartrofit;

import java.util.ArrayList;

public abstract class CallGroup<T> extends CallAdapter.Call {
    ArrayList<T> childrenCallList = new ArrayList<>();

    public void addChildCall(T call) {
        childrenCallList.add(call);
    }

    public void removeChildCall(T call) {
        childrenCallList.remove(call);
    }

    public int getChildCount() {
        return childrenCallList.size();
    }

    public T getChildAt(int index) {
        return childrenCallList.get(index);
    }
}
