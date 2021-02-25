package com.liyangbin.cartrofit;

public class ParameterContext {

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

    public Object[] getParameter(Call target, Object[] all) {
        ParameterGroup parameterGroup = extractParameterFromCall(target);
        final int count = parameterGroup.getParameterCount();
        if (count == 0) {
            return null;
        }
        if (all.length == count) {
            return all;
        } else {
            Object[] result = new Object[count];
            for (int i = 0; i < count; i++) {
                result[i] = all[parameterGroup.getParameterAt(i).getDeclaredIndex()];
            }
            return result;
        }
    }
}
