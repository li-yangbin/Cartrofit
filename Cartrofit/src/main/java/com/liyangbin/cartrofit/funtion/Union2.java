package com.liyangbin.cartrofit.funtion;

public class Union2<T1, T2> extends Union1<T1> {

    static Union2<?, ?> sPool2;
    static int sSize2;

    public T2 value2;

    Union2(T1 value1, T2 value2) {
        super(value1);
        this.value2 = value2;
    }

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public Object get(int index) {
        return index == 1 ? value2 : super.get(index);
    }

    @Override
    public void set(int index, Object value) {
        if (index == 1) {
            value2 = (T2) value;
        } else {
            super.set(index, value);
        }
    }

    @Override
    Union mergeObj(Object obj) {
        return new Union3<>(value1, value2, obj);
    }

    @Override
    public void recycle() {
        value1 = null;
        value2 = null;

        synchronized (Union2.class) {
            if (sSize2 < LIMIT) {
                next = sPool2;
                sPool2 = this;
                sSize2++;
            }
        }
    }
}
