package com.liyangbin.cartrofit;

import java.util.ArrayList;

public abstract class CallGroup<T> extends Call {
    private final ArrayList<T> childrenCallList = new ArrayList<>();

    @Override
    void dispatchInit(ParameterContext context) {
        super.dispatchInit(context);
        for (int i = 0; i < getChildCount(); i++) {
            asCall(getChildAt(i)).dispatchInit(context);
        }
    }

    public void addChildCall(T call) {
        childrenCallList.add(call);
        asCall(call).attachParent(this);
    }

    public void removeChildCall(T call) {
        if (childrenCallList.remove(call)) {
            asCall(call).attachParent(null);
        }
    }

    public int getChildCount() {
        return childrenCallList.size();
    }

    public T getChildAt(int index) {
        return childrenCallList.get(index);
    }

    protected abstract Call asCall(T t);

    @Override
    public boolean hasToken(String token) {
        if (super.hasToken(token)) {
            return true;
        }
        for (int i = 0; i < childrenCallList.size(); i++) {
            if (asCall(childrenCallList.get(i)).hasToken(token)) {
                return true;
            }
        }
        return false;
    }

    protected <RESULT> RESULT childInvoke(Call child, Object[] parameter) throws Throwable {
        return (RESULT) child.invoke(getParameterContext().getParameter(child, parameter));
    }
}
