package com.liyangbin.cartrofit;

import java.lang.annotation.Annotation;
import java.util.HashMap;

public abstract class CallAdapter<SCOPE, CALL extends CallAdapter<SCOPE, CALL>.Call> {

    public static final int CATEGORY_SET = 1;
    public static final int CATEGORY_GET = 1 << 1;
    public static final int CATEGORY_TRACK = 1 << 2;
    public static final int CATEGORY_TRACK_EVENT = CATEGORY_TRACK | (1 << 3);

    public abstract SCOPE getScopeInfo(Class<?> scopeClass);

    public abstract CALL onCreateCall(SCOPE scope, Cartrofit.Key key, int category);

    public abstract Object invoke(CALL call, Object arg);

    public abstract boolean hasCategory(CALL call, int category);

    public abstract Class<?> extractValueType(CALL arg);

    @SuppressWarnings("unchecked")
    public abstract class Call {
        private final Annotation annotation;

        public Call(Annotation annotation) {
            this.annotation = annotation;
        }

        public <A extends Annotation> A getAnnotation() {
            return (A) annotation;
        }

        final Object invoke(Object arg) {
            return CallAdapter.this.invoke((CALL) this, arg);
        }

        final boolean isTrackable() {
            return CallAdapter.this.hasCategory((CALL) this, CATEGORY_TRACK);
        }

        void getConvertSolutionList(CommandBuilder builder) {
        }

        final boolean isEvent() {
            return CallAdapter.this.hasCategory((CALL) this, CATEGORY_TRACK_EVENT);
        }
    }
}
