package com.liyangbin.cartrofit;

import android.os.Build;

import com.liyangbin.cartrofit.annotation.Bind;
import com.liyangbin.cartrofit.annotation.WrappedData;
import com.liyangbin.cartrofit.flow.Flow;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Key implements ParameterGroup {
    public final Method method;
    public final boolean isCallbackEntry;

    public final Field field;

    Context.ApiRecord<?> record;
    private int mId = -1;

    private Annotation[] annotations;
    private int parameterCount = -1;
    private int parameterGroupCount = -1;
    private Parameter returnParameter;
    private Parameter[] parameters;
    private ParameterGroup[] parameterGroups;
    private Key delegateKey;

    Key(Context.ApiRecord<?> record, Method method, boolean isCallbackEntry) {
        this.record = record;
        this.method = method;
        this.field = null;
        this.isCallbackEntry = isCallbackEntry;
    }

    Key(Context.ApiRecord<?> record, Field field) {
        this.record = record;
        this.method = null;
        this.field = field;
        this.isCallbackEntry = false;
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
        return mId = record.selfDependencyReverse.getOrDefault(method, 0);
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

    @Override
    public String token() {
        return "";
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
        if (Context.FLOW_CONVERTER_MAP.containsKey(declaredReturnType)
                || Flow.class.isAssignableFrom(declaredReturnType)) {
            Class<?> userTargetType;
            WrappedData dataAnnotation = declaredReturnType.getAnnotation(WrappedData.class);
            if (dataAnnotation != null) {
                userTargetType = dataAnnotation.type();
            } else {
                userTargetType = Context.WRAPPER_CLASS_MAP.get(declaredReturnType);
            }
            if (userTargetType != null) {
                returnParameter = new ReturnAsParameter(userTargetType, userTargetType);
            } else {
                Type declaredGenericType = method.getGenericReturnType();
                if (declaredGenericType instanceof ParameterizedType) {
                    Type[] typeArray = ((ParameterizedType) declaredGenericType).getActualTypeArguments();
                    Type typeInFlow = typeArray.length > 0 ? typeArray[0] : null;
                    returnParameter = new ReturnAsParameter(Context.getClassFromType(typeInFlow), typeInFlow);
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
        public String token() {
            return "";
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
        public ParameterGroup getParameterGroup() {
            return this;
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

    @Override
    public int getParameterCount() {
        if (parameterCount != -1) {
            return parameterCount;
        }
        if (method == null) {
            return 0;
        }
        return parameterCount = getParameterCount(method);
    }

    @Override
    public Parameter getParameterAt(int index) {
        if (method == null) {
            return null;
        }
        if (parameters == null) {
            final int count = getParameterCount(method);
            parameters = new Parameter[count];
            Class<?>[] parameterClass = method.getParameterTypes();
            Type[] parameterType = method.getGenericParameterTypes();
            Annotation[][] annotationMatrix = method.getParameterAnnotations();
            for (int i = 0; i < count; i++) {
                parameters[i] = new ParameterImpl(method, parameterClass,
                        parameterType, annotationMatrix, i);
            }
        }
        if (index < 0 || index >= parameters.length) {
            return null;
        }
        return parameters[index];
    }

    @Override
    public Key getDeclaredKey() {
        return this;
    }

    public int getParameterGroupCount() {
        if (parameterGroupCount != -1) {
            return parameterGroupCount;
        }
        if (method == null) {
            return 0;
        }
        HashMap<String, ArrayList<Parameter>> groupMap = null;
        for (int i = 0; i < getParameterCount(); i++) {
            Parameter parameter = getParameterAt(i);
            Bind bind = parameter.getAnnotation(Bind.class);
            if (bind != null) {
                for (String token : bind.token()) {
                    if (groupMap == null) {
                        groupMap = new HashMap<>();
                    }
                    ArrayList<Parameter> list = groupMap.get(token);
                    if (list == null) {
                        list = new ArrayList<>();
                        groupMap.put(token, list);
                    }
                    list.add(parameter);
                }
            }
        }
        parameterGroupCount = groupMap != null ? groupMap.size() : 0;
        parameterGroups = new ParameterGroup[parameterGroupCount];
        if (groupMap != null) {
            int index = 0;
            for (Map.Entry<String, ArrayList<Parameter>> entry : groupMap.entrySet()) {
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
        public ParameterGroup getParameterGroup() {
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

    private class ParameterGroupImpl implements ParameterGroup {

        private final String token;
        private final ArrayList<Parameter> parameters;

        ParameterGroupImpl(String token, ArrayList<Parameter> parameters) {
            this.token = token;
            this.parameters = parameters;
        }

        @Override
        public String token() {
            return token;
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

    public <S> S getScopeObj() {
        return (S) record.scopeObj;
    }

    boolean isInvalid() {
        if (method != null) {
            if (method.isDefault()) {
                return true;
            }
            return Modifier.isStatic(method.getModifiers());
        } else {
            return Modifier.isStatic(field.getModifiers());
        }
    }

    void checkGrammar(ArrayList<Context.CallSolution<?>> grammarRules) {
        if (isInvalid()) {
            throw new CartrofitGrammarException("Invalid key:" + this);
        }
        if (grammarRules.size() == 0) {
            throw new RuntimeException("No annotation requirement??");
        }
        boolean qualified = false;
        int checkIndex = 0;
        while (checkIndex < grammarRules.size()) {
            Context.CallSolution<?> solution = grammarRules.get(checkIndex++);
            Annotation annotation = getAnnotation(solution.getGrammarContext());
            if (annotation != null) {
                if (qualified) {
                    throw new CartrofitGrammarException("More than one annotation presented by:" + this);
                }

                solution.checkParameterGrammar(annotation, this);

                if (field != null && solution.hasCategory(Context.CATEGORY_SET)
                        && Modifier.isFinal(field.getModifiers())) {
                    throw new CartrofitGrammarException("Invalid final key:" + this);
                }

                if (method != null
                        && ((!isCallbackEntry && solution.hasCategory(Context.CATEGORY_GET))
                        || (isCallbackEntry && solution.hasCategory(Context.CATEGORY_SET)))) {
                    if (method.getReturnType() == void.class) {
                        throw new CartrofitGrammarException("Invalid return type:" + this);
                    }
                }

                qualified = true;
            }
        }
    }

    public Annotation[] getAnnotations() {
        if (annotations == null) {
            Annotation[] fromDelegate = delegateKey != null ? delegateKey.getAnnotations() : null;
            Annotation[] fromSelf = method != null ? method.getDeclaredAnnotations()
                    : field.getDeclaredAnnotations();
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
        return method != null ? method.getReturnType() : field.getType();
    }

    String getName() {
        return method != null ? method.getName() : field.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Key key = (Key) o;

        if (isCallbackEntry != key.isCallbackEntry) return false;
        if (!Objects.equals(method, key.method)) return false;
        return Objects.equals(field, key.field);
    }

    @Override
    public int hashCode() {
        int result = method != null ? method.hashCode() : 0;
        result = 31 * result + (field != null ? field.hashCode() : 0);
        result = 31 * result + (isCallbackEntry ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Key{" + (method != null ? ("method="
                + method.getDeclaringClass().getSimpleName()
                + "::" + method.getName())
                : ("field=" + field.getDeclaringClass().getSimpleName()
                + "::" + field.getName())) + '}';
    }
}
