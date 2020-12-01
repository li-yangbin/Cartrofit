package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.CarValue;

import java.util.ArrayList;

interface UnTrackable extends Command {
    void untrack(Object obj);
}

abstract class CommandGroup extends CommandImpl {
    ArrayList<CommandImpl> childrenCommand = new ArrayList<>();

    void addChildCommand(CommandImpl command) {
        childrenCommand.add(command);
    }

    @Override
    boolean requireSource() {
        return false;
    }
}

class BuildInValue {
    int intValue;
    int[] intArray;

    boolean booleanValue;
    boolean[] booleanArray;

    long longValue;
    long[] longArray;

    byte byteValue;
    byte[] byteArray;

    float floatValue;
    float[] floatArray;

    String stringValue;
    String[] stringArray;

    static BuildInValue build(CarValue value) {
        if (CarValue.EMPTY_VALUE.equals(value.string())) {
            return null;
        }
        BuildInValue result = new BuildInValue();

        result.intValue = value.Int();
        result.intArray = value.IntArray();

        result.booleanValue = value.Boolean();
        result.booleanArray = value.BooleanArray();

        result.byteValue = value.Byte();
        result.byteArray = value.ByteArray();

        result.floatValue = value.Float();
        result.floatArray = value.FloatArray();

        result.longValue = value.Long();
        result.longArray = value.LongArray();

        result.stringValue = value.string();
        result.stringArray = value.stringArray();

        return result;
    }

    Object extractValue(Class<?> clazz) {
        if (String.class == clazz) {
            return stringValue;
        } else if (String[].class == clazz) {
            return stringArray;
        }
        else if (int.class == clazz || Integer.class == clazz) {
            return intValue;
        } else if (int[].class == clazz) {
            return intArray;
        }
        else if (byte.class == clazz || Byte.class == clazz) {
            return byteValue;
        } else if (byte[].class == clazz) {
            return byteArray;
        }
        else if (float.class == clazz || Float.class == clazz) {
            return floatValue;
        } else if (float[].class == clazz) {
            return floatArray;
        }
        else if (long.class == clazz || Long.class == clazz) {
            return longValue;
        } else if (long[].class == clazz) {
            return longArray;
        }
        else {
            throw new CartrofitGrammarException("invalid type:" + clazz);
        }
    }
}