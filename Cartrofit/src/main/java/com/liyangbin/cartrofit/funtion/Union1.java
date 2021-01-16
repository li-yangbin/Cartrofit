package com.liyangbin.cartrofit.funtion;

public class Union1<T> extends Union {
    static final Union1<Void> NULL_UNION = new Union1<Void>(null) {
        @Override
        public Object get(int index) {
            throw new RuntimeException("impossible call");
        }

        @Override
        public int getCount() {
            return 0;
        }

        @Override
        Union mergeObj(Object obj) {
            return Union.of(obj);
        }

        @Override
        public void recycle() {
            // ignore
        }
    };
    static final int LIMIT = 5;
    static Union1<?> sPool;
    static int sSize;

    public T value1;

    Union1<?> next;

    Union1(T t) {
        value1 = t;
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    Union mergeObj(Object obj) {
        return new Union2<>(value1, obj);
    }

    @Override
    public Object get(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException("size:" + getCount() + " index:" + index);
        }
        return value1;
    }

    @Override
    public void set(int index, Object value) {
        if (index == 0) {
            value1 = (T) value;
        }
    }

    @Override
    public void recycle() {
        value1 = null;

        synchronized (Union.class) {
            if (sSize < LIMIT) {
                next = sPool;
                sPool = this;
                sSize++;
            }
        }
    }
}
