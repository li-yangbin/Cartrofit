package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Union;

import java.util.ArrayList;

public abstract class CallGroup<T> extends Call {
    private ParameterContext parameterContext;
    private ArrayList<T> childrenCallList = new ArrayList<>();

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

    public ParameterContext getParameterContext() {
        if (getParent() != null) {
            return getParent().getParameterContext();
        }
        if (parameterContext == null) {
            parameterContext = onCreateParameterContext();
        }
        return parameterContext;
    }

    protected ParameterContext onCreateParameterContext() {
        return new ParameterContext(getKey());
    }

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

    @Override
    public boolean hasCategory(int category) {
        if (super.hasCategory(category)) {
            return true;
        }
        for (int i = 0; i < childrenCallList.size(); i++) {
            if (asCall(childrenCallList.get(i)).hasCategory(category)) {
                return true;
            }
        }
        return false;
    }

    protected <RESULT> RESULT childInvoke(Call child, Union parameter) {
        return (RESULT) child.invoke(getParameterContext().getParameter(child, parameter));
    }

    protected <RESULT> RESULT childInvokeWithExtra(Call child, Union parameter, Object extra) {
        // TODO: injectable parameter will always be the last one?
        return (RESULT) child.invoke(getParameterContext().getParameter(child, parameter).merge(extra));
    }
}
