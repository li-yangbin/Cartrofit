package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Union;

class ParameterContext {

    private final Key key;

    ParameterContext(Key key) {
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
        for (int i = 0; i < key.getParameterGroupCount(); i++) {
            ParameterGroup group = key.getParameterGroupAt(i);
            if (target.hasToken(group.token())) {
                Union result = Union.ofNull();
                for (int j = 0; j < group.getParameterCount(); j++) {
                    result = result.merge(all.get(group.getParameterAt(j).getDeclaredIndex()));
                }
                return result;
            }
        }
        return Union.ofNull();
    }
}
