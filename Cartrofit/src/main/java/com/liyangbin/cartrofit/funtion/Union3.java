package com.liyangbin.cartrofit.funtion;

public class Union3<T1, T2, T3> extends Union2<T1, T2> {

    static Union3<?, ?, ?> sPool3;
    static int sSize3;

    public T3 value3;

    Union3(T1 value1, T2 value2, T3 value3) {
        super(value1, value2);
        this.value3 = value3;
    }

    @Override
    public int getCount() {
        return 3;
    }

    @Override
    public Object get(int index) {
        return index == 2 ? value3 : super.get(index);
    }

    @Override
    public void set(int index, Object value) {
        if (index == 2) {
            value3 = (T3) value;
        } else {
            super.set(index, value);
        }
    }

    @Override
    Union mergeObj(Object obj) {
        return new Union4<>(value1, value2, value3, obj);
    }

    @Override
    public void recycle() {
        value1 = null;
        value2 = null;
        value3 = null;

        synchronized (Union3.class) {
            if (sSize3 < LIMIT) {
                next = sPool3;
                sPool3 = this;
                sSize3++;
            }
        }
    }
}
