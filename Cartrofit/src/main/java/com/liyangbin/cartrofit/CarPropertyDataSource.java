package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Scope;

import java.lang.annotation.Annotation;

public class CarPropertyDataSource extends AbsDataSource {
    @Override
    public Class<? extends Annotation> getScopeClass() {
        return Scope.class;
    }

    @Override
    public Command onCreateCommand(Cartrofit.Key key) {
        return null;
    }
}
