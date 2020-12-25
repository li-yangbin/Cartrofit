package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.CartrofitGrammarException;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public class Union<T> {
    static final int LIMIT = 5;
    static Union<?> sPool;
    static int sSize;

    private static final Union<Void> NULL_UNION = new Union<Void>(null) {
        @Override
        public Object get(int index) {
            throw new RuntimeException("impossible call");
        }

        @Override
        public int getCount() {
            return 0;
        }

        @Override
        public void recycle() {
            // ignore
        }
    };

    public T value1;

    Union<?> next;

    Union(T t) {
        value1 = t;
    }

    public int getCount() {
        return 1;
    }

    public Object get(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException("size:" + getCount() + " index:" + index);
        }
        return value1;
    }

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

    public static Union<?> of(Object obj) {
        if (obj instanceof Union) {
            return (Union<?>) obj;
        } else {
            synchronized (Union.class) {
                if (Union.sPool == null) {
                    return new Union<>(obj);
                }
                Union<Object> out = (Union<Object>) Union.sPool;
                out.value1 = obj;

                Union.sPool = out.next;
                out.next = null;
                Union.sSize--;
                return out;
            }
        }
    }

    public static Union<?> of(Object... array) {
        if (array == null || array.length == 0) {
            return NULL_UNION;
        }
        switch (array.length) {
            case 1:
                return of(array[0]);
            case 2:
                return of(array[0], array[1]);
            case 3:
                return of(array[0], array[1], array[2]);
            case 4:
                return of(array[0], array[1], array[2], array[3]);
            case 5:
                return of(array[0], array[1], array[2], array[3], array[4]);
            default:
                throw new CartrofitGrammarException("Invalid input array:" + Arrays.toString(array));
        }
    }

    public static <T1, T2> Union2<T1, T2> of(T1 t1, T2 t2) {
        synchronized (Union2.class) {
            if (Union2.sPool2 == null) {
                return new Union2<>(t1, t2);
            }
            Union2<T1, T2> out = (Union2<T1, T2>) Union2.sPool2;
            out.value1 = t1;
            out.value2 = t2;

            Union2.sPool2 = (Union2<?, ?>) out.next;
            out.next = null;
            Union2.sSize2--;
            return out;
        }
    }

    public static <T1, T2, T3> Union3<T1, T2, T3> of(T1 t1, T2 t2, T3 t3) {
        synchronized (Union3.class) {
            if (Union3.sPool3 == null) {
                return new Union3<>(t1, t2, t3);
            }
            Union3<T1, T2, T3> out = (Union3<T1, T2, T3>) Union3.sPool3;
            out.value1 = t1;
            out.value2 = t2;
            out.value3 = t3;

            Union3.sPool3 = (Union3<?, ?, ?>) out.next;
            out.next = null;
            Union3.sSize3--;
            return out;
        }
    }

    public static <T1, T2, T3, T4> Union4<T1, T2, T3, T4> of(T1 t1, T2 t2, T3 t3, T4 t4) {
        synchronized (Union4.class) {
            if (Union4.sPool4 == null) {
                return new Union4<>(t1, t2, t3, t4);
            }
            Union4<T1, T2, T3, T4> out = (Union4<T1, T2, T3, T4>) Union4.sPool4;
            out.value1 = t1;
            out.value2 = t2;
            out.value3 = t3;
            out.value4 = t4;

            Union4.sPool4 = (Union4<?, ?, ?, ?>) out.next;
            out.next = null;
            Union4.sSize4--;
            return out;
        }
    }

    public static <T1, T2, T3, T4, T5> Union5<T1, T2, T3, T4, T5> of(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
        synchronized (Union5.class) {
            if (Union5.sPool5 == null) {
                return new Union5<>(t1, t2, t3, t4, t5);
            }
            Union5<T1, T2, T3, T4, T5> out = (Union5<T1, T2, T3, T4, T5>) Union5.sPool5;
            out.value1 = t1;
            out.value2 = t2;
            out.value3 = t3;
            out.value4 = t4;
            out.value5 = t5;

            Union5.sPool5 = (Union5<?, ?, ?, ?, ?>) out.next;
            out.next = null;
            Union5.sSize5--;
            return out;
        }
    }
}
