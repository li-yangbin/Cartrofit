package com.liyangbin.cartrofit.funtion;

@SuppressWarnings("unchecked")
public abstract class Union {

    public final Union merge(Object target) {
        if (target instanceof Union) {
            Union dst = this;
            Union srcUnion = (Union) target;
            for (int i = 0; i < srcUnion.getCount(); i++) {
                dst = dst.mergeObj(srcUnion.get(i));
            }
            return dst;
        } else {
            return mergeObj(target);
        }
    }

    public final Union exclude(int index) {
        final int count = getCount();
        if (index < 0 || index >= count) {
            return this;
        }
        Union result = Union1.NULL_UNION;
        for (int i = 0; i < count; i++) {
            if (i != index) {
                result = result.mergeObj(get(i));
            }
        }
        return result;
    }

    abstract Union mergeObj(Object obj);

    public abstract int getCount();

    public abstract Object get(int index);

    public abstract void set(int index, Object value);

    public abstract void recycle();

    public static Union ofNull() {
        return Union1.NULL_UNION;
    }

    public static Union of(Object obj) {
        if (obj instanceof Union) {
            return (Union) obj;
        } else {
            synchronized (Union.class) {
                if (Union1.sPool == null) {
                    return new Union1<>(obj, false);
                }
                Union1<Object> out = (Union1<Object>) Union1.sPool;
                out.value1 = obj;

                Union1.sPool = out.next;
                out.next = null;
                Union1.sSize--;
                return out;
            }
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

    public static Union ofArray(Object[] obj) {
        if (obj == null || obj.length == 0) {
            return Union1.NULL_UNION;
        }
        synchronized (Union.class) {
            if (Union1.sPool == null) {
                return new Union1<>(obj, true);
            }
            Union1<Object> out = (Union1<Object>) Union1.sPool;
            out.value1 = obj;
            out.arrayMode = true;

            Union1.sPool = out.next;
            out.next = null;
            Union1.sSize--;
            return out;
        }
    }
}
