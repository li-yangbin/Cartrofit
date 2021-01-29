package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Union;

public class ParameterContext {

    private final Key key;

    public ParameterContext(Key key) {
        this.key = key;
    }

    ParameterGroup extractParameterFromCall(Call call) {
        if (this.key == call.getKey()) {
            return key.getImplicitParameterGroup();
        }
        for (int i = 0; i < key.getParameterGroupCount(); i++) {
            ParameterGroup subGroup = key.getParameterGroupAt(i);
            if (call.hasToken(subGroup.token())) {
                return subGroup;
            }
        }
        return key.getImplicitParameterGroup();
    }

    public Union getParameter(Call target, Union all) {
        ParameterGroup parameterGroup = extractParameterFromCall(target);
        final int count = parameterGroup.getParameterCount();
        if (count == 0) {
            return Union.ofNull();
        }
        if (all.getCount() == count) {
            return all;
        } else {
            Union result = Union.ofArray(new Object[count]);
            for (int i = 0; i < count; i++) {
                result.set(i, all.get(parameterGroup.getParameterAt(i).getDeclaredIndex()));
            }
            return result;
        }
    }
}
