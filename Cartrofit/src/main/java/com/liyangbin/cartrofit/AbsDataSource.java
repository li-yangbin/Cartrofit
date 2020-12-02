package com.liyangbin.cartrofit;

import java.lang.annotation.Annotation;

public abstract class AbsDataSource<SCOPE, DESC extends AbsDataSource.Description> {

    public static final int CATEGORY_SET = 1;
    public static final int CATEGORY_GET = 1 << 1;
    public static final int CATEGORY_TRACK = 1 << 2;

    public abstract SCOPE getScopeInfo(Class<?> scopeClass);

    public abstract DESC onCreateCommandHandle(SCOPE scope, Cartrofit.Key key, int category);

    public abstract Object perform(DESC handle, Object arg);

    public abstract boolean isInterested(SCOPE scope);

    public abstract boolean hasCategory(DESC handle, int category);

    public abstract Class<?> extractValueType(DESC arg);

    public static class Description {
        public final Annotation annotation;

        Description(Annotation annotation) {
            this.annotation = annotation;
        }
    }
}
