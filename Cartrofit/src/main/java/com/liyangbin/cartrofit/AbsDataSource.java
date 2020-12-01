package com.liyangbin.cartrofit;

import java.lang.annotation.Annotation;

public abstract class AbsDataSource {

    public abstract Class<? extends Annotation> getScopeClass();

    public abstract Command onCreateCommand(Cartrofit.Key key);
}
