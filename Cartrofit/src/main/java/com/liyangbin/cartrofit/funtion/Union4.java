package com.liyangbin.cartrofit.funtion;

public class Union4<T1, T2, T3, T4> extends Union3<T1, T2, T3> {

    static Union4<?, ?, ?, ?> sPool4;
    static int sSize4;

    public T4 value4;

    Union4(T1 value1, T2 value2, T3 value3, T4 value4) {
        super(value1, value2, value3);
        this.value4 = value4;
    }

    @Override
    public int getCount() {
        return 4;
    }

    @Override
    public Object get(int index) {
        return index == 3 ? value4 : super.get(index);
    }

    @Override
    Union mergeObj(Object obj) {
        return new Union5<>(value1, value2, value3, value4, obj);
    }

    @Override
    public void recycle() {
        value1 = null;
        value2 = null;
        value3 = null;
        value4 = null;

        synchronized (Union4.class) {
            if (sSize4 < LIMIT) {
                next = sPool4;
                sPool4 = this;
                sSize4++;
            }
        }
    }
}
