package com.liyangbin.cartrofit;

public abstract class ExceptionHandler<T extends Throwable> {

    static final ExceptionHandler<Throwable> ALL = new ExceptionHandler<Throwable>() {
        @Override
        public Class<Throwable> getType() {
            return Throwable.class;
        }

        @Override
        public Object onExceptionCaught(Throwable throwable, Call call, Object[] parameter) {
            throwable.printStackTrace();
            return defaultFallbackValue(call.getKey().getReturnType());
        }
    };

    public static Object defaultFallbackValue(Class<?> type) {
        if (type == void.class) {
            return null;
        } else if (type == boolean.class || type == Boolean.class) {
            return false;
        } else if (type.isPrimitive() || Number.class.isAssignableFrom(type)) {
            return -1;
        } else if (type == byte[].class) {
            return new byte[]{};
        } else if (type == String.class) {
            return "";
        }
        return null;
    }

    public abstract Class<T> getType();

    public abstract Object onExceptionCaught(T exception, Call call, Object[] parameter);

    public void onFlowExceptionCaught(T exception, Call call, Object callback) {
        onExceptionCaught(exception, call, null);
    }

    final Object handleException(Throwable suspect, Call call, Object[] parameter) {
        if (getType().isInstance(suspect)) {
            return onExceptionCaught((T) suspect, call, parameter);
        } else {
            return Cartrofit.SKIP;
        }
    }

    final boolean handleFlowCallbackException(Throwable suspect, Call call, Object callback) {
        if (getType().isInstance(suspect)) {
            onFlowExceptionCaught((T) suspect, call, callback);
            return true;
        }
        return false;
    }
}
