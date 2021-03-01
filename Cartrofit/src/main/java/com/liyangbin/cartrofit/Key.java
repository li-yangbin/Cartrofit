package com.liyangbin.cartrofit;

import android.os.Build;

import com.liyangbin.cartrofit.annotation.Bind;
import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.MethodCategory;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.flow.Flow;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Key {
    public final Method method;
    public final boolean isCallbackEntry;

    ApiRecord<?> record;
    private int mId = -1;

    private Annotation[] annotations;
    private Class<?>[] declaredExceptions;
    private int parameterCount = -1;
    private int parameterGroupCount = -1;
    private Parameter returnParameter;
    private Parameter[] parameters;
    private ParameterGroup[] parameterGroups;
    private ParameterGroup implicitParameterGroup;
    private boolean implicitCallbackParameterResolved;
    private boolean implicitCallbackParameterPresent;
    private Key delegateKey;

    Key(ApiRecord<?> record, Method method, boolean isCallbackEntry) {
        this.record = record;
        this.method = method;
        this.isCallbackEntry = isCallbackEntry;
    }

    void setDelegateKey(Key delegateKey) {
        this.delegateKey = delegateKey;
    }

    private static <A extends Annotation> A find(Annotation[] annotations, Class<A> expectClass) {
        for (Annotation annotation : annotations) {
            if (expectClass.isInstance(annotation)) {
                return (A) annotation;
            }
        }
        return null;
    }

    public int getId() {
        if (mId != -1) {
            return mId;
        }
        if (method == null) {
            return 0;
        }
        return mId = record.loadIdByCall(this);
    }

    public Parameter findParameterByAnnotation(Class<? extends Annotation> clazz) {
        for (int i = 0; i < getParameterCount(); i++) {
            Parameter parameter = getParameterAt(i);
            if (parameter.isAnnotationPresent(clazz)) {
                return parameter;
            }
        }
        return null;
    }

    public Parameter getReturnAsParameter() {
        if (returnParameter != null) {
            return returnParameter;
        }
        if (method == null) {
            return null;
        }
        Class<?> declaredReturnType = method.getReturnType();
        if (declaredReturnType == void.class) {
            return null;
        }
        if (Cartrofit.FLOW_CONVERTER_MAP.containsKey(declaredReturnType)
                || Flow.class.isAssignableFrom(declaredReturnType)) {
            Class<?> userTargetType;
            WrappedData dataAnnotation = declaredReturnType.getAnnotation(WrappedData.class);
            if (dataAnnotation != null) {
                userTargetType = dataAnnotation.type();
            } else {
                userTargetType = Cartrofit.WRAPPER_CLASS_MAP.get(declaredReturnType);
            }
            if (userTargetType != null) {
                returnParameter = new ReturnAsParameter(userTargetType, userTargetType);
            } else {
                Type declaredGenericType = method.getGenericReturnType();
                if (declaredGenericType instanceof ParameterizedType) {
                    Type[] typeArray = ((ParameterizedType) declaredGenericType).getActualTypeArguments();
                    Type typeInFlow = typeArray.length > 0 ? typeArray[0] : null;
                    returnParameter = new ReturnAsParameter(Cartrofit.getClassFromType(typeInFlow), typeInFlow);
                } else {
                    throw new CartrofitGrammarException("Unsupported type:" + declaredGenericType);
                }
            }
        } else {
            returnParameter = new ReturnAsParameter(declaredReturnType,
                    method.getGenericReturnType());
        }
        return returnParameter;
    }

    private class ReturnAsParameter implements Parameter, ParameterGroup {

        final Class<?> type;
        final Type genericType;

        ReturnAsParameter(Class<?> type, Type genericType) {
            this.type = type;
            this.genericType = genericType;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> clazz) {
            return Key.this.isAnnotationPresent(clazz);
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> clazz) {
            return Key.this.getAnnotation(clazz);
        }

        @Override
        public Annotation[] getAnnotations() {
            return Key.this.getAnnotations();
        }

        @Override
        public boolean hasNoAnnotation() {
            return false;
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        @Override
        public Type getGenericType() {
            return genericType;
        }

        @Override
        public int getDeclaredIndex() {
            return 0;
        }

        @Override
        public boolean isTaken(Call call) {
            return false;
        }

        @Override
        public int getParameterCount() {
            return 1;
        }

        @Override
        public Parameter getParameterAt(int index) {
            return this;
        }

        @Override
        public Key getDeclaredKey() {
            return Key.this;
        }

        @Override
        public String getName() {
            return "";
        }
    }

    private static int getParameterCount(Method method) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return method.getParameterTypes().length;
        } else {
            return method.getParameterCount();
        }
    }

    public int getParameterCount() {
        if (parameterCount != -1) {
            return parameterCount;
        }
        if (method == null) {
            return 0;
        }
        parameterCount = getParameterCount(method);
        if (parameters == null && parameterCount > 0) {
            parameters = new Parameter[parameterCount];
            Class<?>[] parameterClass = method.getParameterTypes();
            Type[] parameterType = method.getGenericParameterTypes();
            Annotation[][] annotationMatrix = method.getParameterAnnotations();
            for (int i = 0; i < parameterCount; i++) {
                parameters[i] = new ParameterImpl(method, parameterClass,
                        parameterType, annotationMatrix, i);
            }
        }
        return parameterCount;
    }

    public Parameter getParameterAt(int index) {
        if (method == null) {
            return null;
        }
        if (index < 0 || index >= getParameterCount()) {
            return null;
        }
        return parameters[index];
    }

    public int getParameterGroupCount() {
        if (parameterGroupCount != -1) {
            return parameterGroupCount;
        }
        if (method == null) {
            return 0;
        }
        HashMap<Tokenizer, ArrayList<Parameter>> groupMap = null;
        for (int i = 0; i < getParameterCount(); i++) {
            Parameter parameter = getParameterAt(i);
            Bind bind = parameter.getAnnotation(Bind.class);
            if (bind != null) {
                boolean hasTokenDeclared = bind.token().length() > 0;
                if (!hasTokenDeclared && bind.id() == 0) {
                    throw new CartrofitGrammarException("Invalid Bind grammar " + this);
                }
                Tokenizer tokenizer;
                if (hasTokenDeclared) {
                    tokenizer = new Tokenizer(bind.token());
                } else {
                    tokenizer = new Tokenizer(bind.id());
                }
                if (groupMap == null) {
                    groupMap = new HashMap<>();
                }
                ArrayList<Parameter> list = groupMap.get(tokenizer);
                if (list == null) {
                    list = new ArrayList<>();
                    groupMap.put(tokenizer, list);
                }
                list.add(parameter);
            }
        }
        parameterGroupCount = groupMap != null ? groupMap.size() : 0;
        parameterGroups = new ParameterGroup[parameterGroupCount];
        if (groupMap != null) {
            int index = 0;
            for (Map.Entry<Tokenizer, ArrayList<Parameter>> entry : groupMap.entrySet()) {
                parameterGroups[index++] = new ParameterGroupImpl(entry.getKey(), entry.getValue());
            }
        }
        return parameterGroupCount;
    }

    public ParameterGroup getParameterGroupAt(int index) {
        if (index < 0 || index >= getParameterGroupCount()) {
            return null;
        }
        return parameterGroups[index];
    }

    public boolean isImplicitCallbackParameterPresent() {
        if (implicitCallbackParameterResolved) {
            return implicitCallbackParameterPresent;
        }
        implicitCallbackParameterResolved = true;
        if (getParameterCount() != 1) {
            return implicitCallbackParameterPresent = false;
        }
        if (!getParameterAt(0).getType().isInterface()) {
            return implicitCallbackParameterPresent = false;
        }
        Annotation[] annotations = getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation instanceof Register) {
                return implicitCallbackParameterPresent = true;
            } else {
                MethodCategory methodCategory = annotation.annotationType()
                        .getDeclaredAnnotation(MethodCategory.class);
                if (methodCategory != null
                        && (methodCategory.value() & MethodCategory.CATEGORY_TRACK) != 0) {
                    return implicitCallbackParameterPresent = true;
                }
            }
        }
        return implicitCallbackParameterPresent = false;
    }

    public ParameterGroup getImplicitParameterGroup() {
        if (implicitParameterGroup != null) {
            return implicitParameterGroup;
        }
        ArrayList<Parameter> parameterArrayList = new ArrayList<>();
        if (!isImplicitCallbackParameterPresent()) {
            final int count = getParameterCount();
            anchor: for (int i = 0; i < count; i++) {
                Parameter parameter = getParameterAt(i);
                Annotation[] annotations = parameter.getAnnotations();
                for (Annotation annotation : annotations) {
                    if (annotation instanceof Bind || annotation instanceof Callback) {
                        continue anchor;
                    }
                }
                parameterArrayList.add(parameter);
            }
        }
        return implicitParameterGroup = new ParameterGroupImpl(new Tokenizer("implicit"), parameterArrayList);
    }

    private class ParameterImpl implements Parameter {

        final Class<?>[] parameterClass;
        final Type[] parameterType;
        final Annotation[][] annotationMatrix;
        final int index;
        final Method method;
        java.lang.reflect.Parameter parameterRef;

        ParameterImpl(Method method, Class<?>[] parameterClass,
                      Type[] parameterType, Annotation[][] annotationMatrix, int index) {
            this.parameterClass = parameterClass;
            this.parameterType = parameterType;
            this.annotationMatrix = annotationMatrix;
            this.index = index;
            this.method = method;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> clazz) {
            return find(annotationMatrix[index], clazz) != null;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> clazz) {
            return find(annotationMatrix[index], clazz);
        }

        @Override
        public Annotation[] getAnnotations() {
            return annotationMatrix[index];
        }

        @Override
        public boolean hasNoAnnotation() {
            return annotationMatrix[index] == null || annotationMatrix[index].length == 0;
        }

        @Override
        public Class<?> getType() {
            return parameterClass[index];
        }

        @Override
        public Type getGenericType() {
            return parameterType[index];
        }

        @Override
        public int getDeclaredIndex() {
            return index;
        }

        @Override
        public Key getDeclaredKey() {
            return Key.this;
        }

        @Override
        public String getName() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (parameterRef == null) {
                    parameterRef = method.getParameters()[index];
                }
                return parameterRef.getName();
            } else {
                return null;
            }
        }
    }

    private static class Tokenizer {
        String token;
        int id;

        Tokenizer(int id) {
            this.id = id;
        }

        Tokenizer(String token) {
            this.token = token;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tokenizer token1 = (Tokenizer) o;
            return token != null ? token.equals(token1.token) : id == token1.id;
        }

        @Override
        public int hashCode() {
            return token != null ? token.hashCode() : id;
        }
    }

    private class ParameterGroupImpl implements ParameterGroup {

        private final Tokenizer tokenizer;
        private final ArrayList<Parameter> parameters;

        ParameterGroupImpl(Tokenizer tokenizer, ArrayList<Parameter> parameters) {
            this.tokenizer = tokenizer;
            this.parameters = parameters;
        }

        @Override
        public boolean isTaken(Call call) {
            return tokenizer.token != null ?
                    call.hasToken(tokenizer.token) : call.getId() == tokenizer.id;
        }

        @Override
        public int getParameterCount() {
            return parameters.size();
        }

        @Override
        public Parameter getParameterAt(int index) {
            return parameters.get(index);
        }

        @Override
        public Key getDeclaredKey() {
            return Key.this;
        }
    }

    public <A extends Annotation> A getScope() {
        return (A) record.scopeObj;
    }

    boolean isInvalid() {
        return Modifier.isStatic(method.getModifiers());
    }

    public Annotation[] getAnnotations() {
        if (annotations == null) {
            Annotation[] fromDelegate = delegateKey != null ? delegateKey.getAnnotations() : null;
            Annotation[] fromSelf = method.getDeclaredAnnotations();
            if (fromDelegate == null || fromDelegate.length == 0) {
                annotations = fromSelf;
            } else if (fromSelf != null) {
                // TODO: remove duplicate annotation type
                Annotation[] annotations = new Annotation[fromDelegate.length + fromSelf.length];
                System.arraycopy(fromSelf, 0, annotations, 0, fromSelf.length);
                System.arraycopy(fromDelegate, 0, annotations, fromSelf.length, fromDelegate.length);
                this.annotations = annotations;
            }
        }
        return annotations;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return find(getAnnotations(), annotationClass);
    }

    public <T extends Annotation> boolean isAnnotationPresent(Class<T> annotationClass) {
        return find(getAnnotations(), annotationClass) != null;
    }

    public Class<?> getReturnType() {
        return method.getReturnType();
    }

    public boolean isExceptionDeclared(Class<? extends Throwable> type) {
        if (isCallbackEntry) {
            return false;
        }
        if (declaredExceptions == null) {
            declaredExceptions = method.getExceptionTypes();
        }
        for (Class<?> declaredException : declaredExceptions) {
            if (RuntimeException.class.isAssignableFrom(declaredException)) {
                // ignore RuntimeException declaration
                continue;
            }
            if (type.isAssignableFrom(declaredException)) {
                return true;
            }
        }
        return false;
    }

    String getName() {
        return method.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Key key = (Key) o;
        return method.equals(key.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method);
    }

    @Override
    public String toString() {
        return "Key{" + method.getDeclaringClass().getSimpleName()
                + "::" + method.getName() + '}';
    }
}
