package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Bind;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

public class ParameterContext {

//    private static final String DEFAULT_TOKEN = "_default_token_";

    private final HashMap<String, ParameterMap> parameterIndexMap = new HashMap<>();

    public ParameterContext(/*CallGroup<?> group, */Cartrofit.Key key) {
        for (int i = 0; i < key.getParameterCount(); i++) {
            Cartrofit.Parameter parameter = key.getParameterAt(i);
            Bind bind = parameter.getAnnotation(Bind.class);
            if (bind != null /*|| !parameter.isAnnotationPresent(Callback.class)*/) {
                String[] tokenArray = bind.token()/*bind != null ? bind.token() : DEFAULT_TOKEN*/;
                for (String token : tokenArray) {
                    ParameterMap indexBit = parameterIndexMap.get(token);
                    if (indexBit == null) {
                        indexBit = new ParameterMap();
                        parameterIndexMap.put(token, indexBit);
                    }
                    indexBit.addIndex(i);
                }
            }
        }
    }

//    public void prepareForParameterDeliverable(List<Call> callList) {
//        Call onlyOneParameterReceiver = null;
//        for (int i = 0; i < callList.size(); i++) {
//            Call call = callList.get(i);
//            if (call.isParameterRequired()) {
//                if (onlyOneParameterReceiver != null) {
//                    return;
//                }
//                onlyOneParameterReceiver = call;
//            }
//        }
//        if (onlyOneParameterReceiver != null) {
//            onlyOneParameterReceiver.addToken(DEFAULT_TOKEN);
//        }
//    }

    public Union<?> getParameter(Call target, Union<?> all) {
        for (Map.Entry<String, ParameterMap> parameterEntry : parameterIndexMap.entrySet()) {
            if (target.hasToken(parameterEntry.getKey())) {
                ParameterMap map = parameterEntry.getValue();
                return map.find(all);
            }
        }
        return Union.ofNull();
    }

    public static class ParameterMap {
        int indexBit;
        int size;

        void addIndex(int index) {
            indexBit |= (1 << index);
            size++;
        }

        Union<?> find(Union<?> all) {
            Object[] elements = new Object[size];
            for (int i = 0, j = 0; i < all.getCount(); i++) {
                if ((indexBit & (1 << i)) != 0) {
                    elements[j++] = all.get(i);
                }
            }
            return Union.of(elements);
        }
    }
}
