package com.liyangbin.cartrofit.broadcast;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.UserHandle;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.CartrofitContext;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.FixedTypeCall;
import com.liyangbin.cartrofit.RegisterCall;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.LifeAwareHotFlowSource;
import com.liyangbin.cartrofit.solution.ConvertSolution;
import com.liyangbin.cartrofit.solution.SolutionProvider;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class BroadcastContext extends CartrofitContext<Broadcast> {
    private final Context mContext;
    private final boolean mIsLocalBroadcast;
    private final HashMap<Call, BroadcastFlowSource> mCachedBroadcastSource = new HashMap<>();
    private final HashMap<RegisterRequest, BroadcastFlowSource> mCachedBroadcastSourceByRequest = new HashMap<>();

    public static void registerAsDefault(Context context) {
        Cartrofit.register(new BroadcastContext(context, false));
        try {
            Class.forName("androidx.localbroadcastmanager.content.LocalBroadcastManager");
            Cartrofit.register(new BroadcastContext(context, true));
        } catch (ClassNotFoundException ignore) {
            // add dependency 'androidx.localbroadcastmanager:localbroadcastmanager:1.0.0' to your gradle file
        }
    }

    public BroadcastContext(Context context, boolean isLocal) {
        this.mContext = context;
        this.mIsLocalBroadcast = isLocal;
    }

    @Override
    public boolean onApiCreate(Broadcast annotation, Class<?> apiType) {
        return annotation.isLocal() == mIsLocalBroadcast;
    }

    @Override
    public SolutionProvider onProvideCallSolution() {
        return onProvideSendSolution().merge(onProvideReceiveSolution());
    }

    public SolutionProvider onProvideSendSolution() {
        return BroadcastSolutionProvider.sendSolution();
    }

    public SolutionProvider onProvideReceiveSolution() {
        return BroadcastSolutionProvider.receiveSolution();
    }

    private BroadcastFlowSource getOrCreateFlowSource(Call call, RegisterRequest registerRequest) {
        if (call.getKey().isCallbackEntry) {
            RegisterCall registerCall = (RegisterCall) call.getParent();
            if (!registerCall.isColdTrackMode()) {
                BroadcastFlowSource flowSource = mCachedBroadcastSource.get(registerCall);
                if (flowSource == null) {
                    flowSource = new BroadcastFlowSource(registerRequest);
                    flowSource.setRegisterCallFrom(registerCall);
                    mCachedBroadcastSource.put(registerCall, flowSource);
                } else {
                    if (!flowSource.isActive() && !flowSource.mergeUnchecked(registerRequest)) {
                        throw new IllegalArgumentException("Can not present duplicate broadcast action in"
                                + " each Callback " + call.getKey());
                    }
                }
                return flowSource;
            }
        }
        BroadcastFlowSource flowSource = mCachedBroadcastSourceByRequest.get(registerRequest);
        if (flowSource == null) {
            flowSource = new BroadcastFlowSource(registerRequest);
            mCachedBroadcastSourceByRequest.put(registerRequest, flowSource);
        }
        return flowSource;
    }

    private class BroadcastFlowSource extends LifeAwareHotFlowSource<ReceiveResponse> {
        ArrayList<RegisterRequest> mergedRequest = new ArrayList<>();
        RegisterCall registerCallFrom;
        InnerBroadcastReceiver innerBroadcastReceiver = new InnerBroadcastReceiver();

        BroadcastFlowSource(RegisterRequest registerRequest) {
            mergedRequest.add(registerRequest);
        }

        void setRegisterCallFrom(RegisterCall registerCallFrom) {
            this.registerCallFrom = registerCallFrom;
        }

        boolean mergeUnchecked(RegisterRequest registerRequest) {
            for (int i = 0; i < mergedRequest.size(); i++) {
                RegisterRequest savedRequest = mergedRequest.get(i);
                if (savedRequest.action.equals(registerRequest.action)) {
                    return false;
                }
            }
            mergedRequest.add(registerRequest);
            return true;
        }

        @Override
        public void onActive() {
            if (mIsLocalBroadcast) {
                LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
                for (int i = 0; i < mergedRequest.size(); i++) {
                    RegisterRequest registerRequest = mergedRequest.get(i);
                    localBroadcastManager.registerReceiver(innerBroadcastReceiver,
                            registerRequest.intentFilter);
                }
            } else {
                for (int i = 0; i < mergedRequest.size(); i++) {
                    RegisterRequest registerRequest = mergedRequest.get(i);
                    mContext.registerReceiver(innerBroadcastReceiver, registerRequest.intentFilter,
                            registerRequest.broadcastPermission, registerRequest.scheduledHandler);
                }
            }
        }

        @Override
        public void onInactive() {
            if (mIsLocalBroadcast) {
                LocalBroadcastManager.getInstance(mContext).unregisterReceiver(innerBroadcastReceiver);
            } else {
                mContext.unregisterReceiver(innerBroadcastReceiver);
            }
            if (registerCallFrom != null) {
                mCachedBroadcastSource.remove(registerCallFrom);
            }
        }

        private class InnerBroadcastReceiver extends BroadcastReceiver {

            @Override
            public void onReceive(Context context, Intent intent) {
                publish(new ReceiveResponse(context, intent, this));
            }
        }
    }

    private static class BroadcastSolutionProvider {
        static final SolutionProvider sSendProvider = new SolutionProvider();
        static final SolutionProvider sReceiveProvider = new SolutionProvider();

        static {
            ConvertSolution<SendRequest, Void, Send> sendSolution
                    = sSendProvider.createWithFixedType(Send.class, SendCall.class)
                    .provideAndBuildParameter((send, key) -> new SendCall());

            sendSolution.provideBasic((send, key) -> new SendRequest(send));

            sendSolution.takeAnyWithAnnotation(Extra.class)
                    .input((extra, old, para) ->
                            old.withExtra(extra.key(), para.get(),
                                    para.getParameter().getGenericType()))
                    .build();

            sendSolution.takeWithAnnotation(String.class, ExtraPair.class)
                    .and(Object.class)
                    .inputTogether((extraPair, old, para1, para2) ->
                            old.withExtra(para1.get(), para2.get(),
                                    para2.getParameter().getGenericType()))
                    .build();

            sendSolution.takeWithAnnotation(Uri.class, Data.class)
                    .input((data, old, para) ->
                            old.withData(para.get(), data.mimeType()))
                    .build();

            sendSolution.takeWithAnnotation(String.class, Data.class)
                    .input((data, old, para) ->
                            old.withData(para.get(), data.mimeType()))
                    .build();

            sendSolution.takeWithAnnotation(UserHandle.class, User.class)
                    .input((annotation, old, para) -> old.withUserHandle(para.get()))
                    .buildAndCommit();
        }

        static {
            ConvertSolution<RegisterRequest, ReceiveResponse, Receive> receiveSolution
                    = sReceiveProvider.createWithFixedType(Receive.class, ReceiveCall.class)
                    .provideAndBuildParameter((receive, key) -> new ReceiveCall())
                    .provideBasic((receive, key) -> new RegisterRequest(receive));

            receiveSolution.take(Intent.class).output((annotation, old, para) -> {
                para.set(old.intent);
                return old;
            }).build()
            .take(Context.class).output((annotation, old, para) -> {
                para.set(old.context);
                return old;
            }).build()
            .take(BroadcastReceiver.class).output((annotation, old, para) -> {
                para.set(old.receiver);
                return old;
            }).build()
            .take(Handler.class).input((annotation, old, para) ->
                    old.withHandler(para.get()))
                    .build()
            .takeAnyWithAnnotation(Extra.class).output((extra, old, para) -> {
                para.set(extractExtraFromIntent(old.intent, extra.key(),
                        para.getParameter().getGenericType(), extra.defNumber(), extra.defBool()));
                return old;
            }).buildAndCommit();
        }

        static SolutionProvider sendSolution() {
            return sSendProvider;
        }

        static SolutionProvider receiveSolution() {
            return sReceiveProvider;
        }
    }

    private static String fromAnnotation(String src) {
        return src.length() > 0 ? src : null;
    }

    public static Object extractExtraFromIntent(Intent intent, String key,
                                                Type genericType, int defNum, boolean defBool) {
        Class<?> type;
        boolean isArrayList = false;
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type rawType = parameterizedType.getRawType();
            if (rawType != ArrayList.class) {
                throw new CartrofitGrammarException("Can only declare generic type as ArrayList:"
                        + genericType);
            }
            isArrayList = true;
            type = (Class<?>) parameterizedType.getActualTypeArguments()[0];
        } else if (genericType instanceof Class) {
            type = (Class<?>) genericType;
        } else {
            throw new CartrofitGrammarException("Invalid extra value type declaration:" + genericType);
        }

        boolean isArray = type.isArray();
        if (isArray) {
            type = type.getComponentType();
            assert type != null;
            if (Number.class.isAssignableFrom(type) || type == Boolean.class) {
                throw new CartrofitGrammarException("Must declare array as primitive type " + type);
            }
        }

        if (classEquals(type, boolean.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                return intent.getBooleanArrayExtra(key);
            } else {
                return intent.getBooleanExtra(key, defBool);
            }
        } else if (classEquals(type, int.class)) {
            if (isArrayList) {
                return intent.getIntegerArrayListExtra(key);
            } else if (isArray) {
                return intent.getIntArrayExtra(key);
            } else {
                return intent.getIntExtra(key, defNum);
            }
        } else if (classEquals(type, byte.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                return intent.getByteArrayExtra(key);
            } else {
                return intent.getByteExtra(key, (byte) defNum);
            }
        } else if (classEquals(type, float.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                return intent.getFloatArrayExtra(key);
            } else {
                return intent.getFloatExtra(key, defNum);
            }
        } else if (classEquals(type, double.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                return intent.getDoubleArrayExtra(key);
            } else {
                return intent.getDoubleExtra(key, defNum);
            }
        } else if (classEquals(type, char.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                return intent.getCharArrayExtra(key);
            } else {
                return intent.getCharExtra(key, (char) defNum);
            }
        } else if (type == String.class) {
            if (isArrayList) {
                return intent.getStringArrayListExtra(key);
            } else if (isArray) {
                return intent.getStringArrayExtra(key);
            } else {
                return intent.getStringExtra(key);
            }
        } else if (type == Bundle.class) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                throw new CartrofitGrammarException("Can only declare Array type in" +
                        " Extra grammar for Bundle");
            } else {
                return intent.getBundleExtra(key);
            }
        } else if (Parcelable.class.isAssignableFrom(type)) {
            if (isArrayList) {
                return intent.getParcelableArrayListExtra(key);
            } else if (isArray) {
                return intent.getParcelableArrayExtra(key);
            } else {
                return intent.getParcelableExtra(key);
            }
        } else if (CharSequence.class.isAssignableFrom(type)) {
            if (isArrayList) {
                return intent.getCharSequenceArrayListExtra(key);
            } else if (isArray) {
                return intent.getCharSequenceArrayExtra(key);
            } else {
                return intent.getCharSequenceExtra(key);
            }
        } else if (Serializable.class.isAssignableFrom(type)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                throw new CartrofitGrammarException("Can only declare Array type in" +
                        " Extra grammar for Serializable");
            } else {
                return intent.getSerializableExtra(key);
            }
        } else {
            throw new CartrofitGrammarException("Extra grammar error due to invalid type " + type);
        }
    }

    public static void assembleExtraIntoIntent(Intent intent, String key,
                                               Object value, Type genericType) {
        Class<?> type;
        boolean isArrayList = false;
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type rawType = parameterizedType.getRawType();
            if (rawType != ArrayList.class) {
                throw new CartrofitGrammarException("Can only declare generic type as ArrayList:"
                        + genericType);
            }
            isArrayList = true;
            type = (Class<?>) parameterizedType.getActualTypeArguments()[0];
        } else if (genericType instanceof Class) {
            type = (Class<?>) genericType;
        } else {
            throw new CartrofitGrammarException("Invalid extra value type declaration:" + genericType);
        }
        boolean isArray = type.isArray();
        if (isArray) {
            type = type.getComponentType();
            assert type != null;
            if (Number.class.isAssignableFrom(type) || type == Boolean.class) {
                throw new CartrofitGrammarException("Must declare array as primitive type " + type);
            }
        }
        if (classEquals(type, boolean.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                intent.putExtra(key, (boolean[]) value);
            } else {
                intent.putExtra(key, (boolean) value);
            }
        } else if (classEquals(type, int.class)) {
            if (isArrayList) {
                intent.putIntegerArrayListExtra(key, (ArrayList<Integer>) value);
            } else if (isArray) {
                intent.putExtra(key, (int[]) value);
            } else {
                intent.putExtra(key, (int) value);
            }
        } else if (classEquals(type, byte.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                intent.putExtra(key, (byte[]) value);
            } else {
                intent.putExtra(key, (byte) value);
            }
        } else if (classEquals(type, float.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                intent.putExtra(key, (float[]) value);
            } else {
                intent.putExtra(key, (float) value);
            }
        } else if (classEquals(type, double.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                intent.putExtra(key, (double[]) value);
            } else {
                intent.putExtra(key, (double) value);
            }
        } else if (classEquals(type, char.class)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                intent.putExtra(key, (char[]) value);
            } else {
                intent.putExtra(key, (char) value);
            }
        } else if (type == String.class) {
            if (isArrayList) {
                intent.putStringArrayListExtra(key, (ArrayList<String>) value);
            } else if (isArray) {
                intent.putExtra(key, (String[]) value);
            } else {
                intent.putExtra(key, (String) value);
            }
        } else if (type == Bundle.class) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                throw new CartrofitGrammarException("Can only declare Array type in" +
                        " Extra grammar for Bundle");
            } else {
                intent.putExtra(key, (Bundle) value);
            }
        } else if (Parcelable.class.isAssignableFrom(type)) {
            if (isArrayList) {
                intent.putParcelableArrayListExtra(key, (ArrayList<Parcelable>) value);
            } else if (isArray) {
                intent.putExtra(key, (Parcelable[]) value);
            } else {
                intent.putExtra(key, (Parcelable) value);
            }
        } else if (CharSequence.class.isAssignableFrom(type)) {
            if (isArrayList) {
                intent.putCharSequenceArrayListExtra(key, (ArrayList<CharSequence>) value);
            } else if (isArray) {
                intent.putExtra(key, (CharSequence[]) value);
            } else {
                intent.putExtra(key, (CharSequence) value);
            }
        } else if (Serializable.class.isAssignableFrom(type)) {
            if (isArrayList) {
                throw new CartrofitGrammarException("Invalid type " + type +
                        " Can only declare ArrayList generic type in Extra grammar by" +
                        " int, String, CharSequence or Parcelable");
            } else if (isArray) {
                throw new CartrofitGrammarException("Can only declare Array type in" +
                        " Extra grammar for Serializable");
            } else {
                intent.putExtra(key, (Serializable) value);
            }
        } else {
            throw new CartrofitGrammarException("Extra grammar error due to invalid type " + type);
        }
    }

    private static class SendRequest {
        Intent intent;
        String receiverPermission;
        boolean ordered;
        boolean synced;
        UserHandle userHandle;

        SendRequest(Send send) {
            intent = new Intent();
            intent.setAction(send.action());
            receiverPermission = fromAnnotation(send.receiverPermission());
            String targetPackageName = fromAnnotation(send.targetPackage());
            String targetClassName = fromAnnotation(send.targetClass());
            if (targetPackageName != null) {
                if (targetClassName != null) {
                    intent.setClassName(targetPackageName, targetClassName);
                } else {
                    intent.setPackage(targetPackageName);
                }
            }
            ordered = send.ordered();
            synced = send.synced();
        }

        SendRequest withExtra(String key, Object value, Type genericType) {
            assembleExtraIntoIntent(intent, key, value, genericType);
            return this;
        }

        SendRequest withData(String uriPresent, String mimeType) {
            return withData(Uri.parse(uriPresent), mimeType);
        }

        SendRequest withData(Uri data, String mimeType) {
            mimeType = fromAnnotation(mimeType);
            if (mimeType != null) {
                intent.setDataAndType(data, mimeType);
            } else {
                intent.setData(data);
            }
            return this;
        }

        SendRequest withUserHandle(UserHandle userHandle) {
            this.userHandle = userHandle;
            return this;
        }
    }

    private static class SendCall extends FixedTypeCall<SendRequest, Void> {

        @SuppressLint("MissingPermission")
        @Override
        public Void onTypedInvoke(SendRequest request) throws Throwable {
            final Context androidContext = getContext().mContext;
            if (getContext().mIsLocalBroadcast) {
                LocalBroadcastManager localBroadcastManager = LocalBroadcastManager
                        .getInstance(androidContext);
                if (request.synced) {
                    localBroadcastManager.sendBroadcastSync(request.intent);
                } else {
                    localBroadcastManager.sendBroadcast(request.intent);
                }
            } else {
                if (request.ordered) {
                    androidContext.sendOrderedBroadcast(request.intent, request.receiverPermission);
                } else {
                    if (request.userHandle != null) {
                        androidContext.sendBroadcastAsUser(request.intent, request.userHandle,
                                request.receiverPermission);
                    } else {
                        androidContext.sendBroadcast(request.intent, request.receiverPermission);
                    }
                }
            }
            return null;
        }

        @Override
        public BroadcastContext getContext() {
            return (BroadcastContext) super.getContext();
        }
    }

    private static class RegisterRequest {
        String action;
        ArrayList<String> othersForCheck = new ArrayList<>();
        IntentFilter intentFilter = new IntentFilter();
        String broadcastPermission;
        Handler scheduledHandler;

        RegisterRequest(Receive receive) {
            action = receive.action();
            if (action.length() == 0) {
                throw new IllegalStateException("Invalid action by " + receive);
            }
            intentFilter.addAction(action);
            intentFilter.setPriority(receive.priority());
            for (String category : receive.category()) {
                intentFilter.addCategory(category);
                othersForCheck.add(category);
            }
            for (String dataScheme : receive.dataScheme()) {
                intentFilter.addDataScheme(dataScheme);
                othersForCheck.add(dataScheme);
            }
            for (String dataMimeType : receive.dataMimeType()) {
                try {
                    intentFilter.addDataType(dataMimeType);
                    othersForCheck.add(dataMimeType);
                } catch (IntentFilter.MalformedMimeTypeException mimeFormatError) {
                    throw new IllegalArgumentException(mimeFormatError);
                }
            }
            broadcastPermission = fromAnnotation(receive.broadcastPermission());
            if (broadcastPermission != null) {
                othersForCheck.add(broadcastPermission);
            }
        }

        RegisterRequest withHandler(Handler handler) {
            scheduledHandler = handler;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegisterRequest that = (RegisterRequest) o;
            if (!action.equals(that.action)) {
                return false;
            }
            if (!othersForCheck.equals(that.othersForCheck)) {
                return false;
            }
            return Objects.equals(scheduledHandler, that.scheduledHandler);
        }

        @Override
        public int hashCode() {
            return Objects.hash(action, othersForCheck, scheduledHandler);
        }
    }

    private static class ReceiveResponse {
        Intent intent;
        Context context;
        BroadcastReceiver receiver;

        ReceiveResponse(Context context, Intent intent, BroadcastReceiver receiver) {
            this.context = context;
            this.intent = intent;
            this.receiver = receiver;
        }
    }

    private static class ReceiveCall extends FixedTypeCall<RegisterRequest, ReceiveResponse> {

        @Override
        public Flow<ReceiveResponse> onTrackInvoke(RegisterRequest registerRequest) throws Throwable {
            BroadcastFlowSource flowSource = getContext().getOrCreateFlowSource(this, registerRequest);
            return Flow.fromSource(flowSource).takeWhile(receiveResponse -> registerRequest.action
                    .equals(receiveResponse.intent.getAction()));
        }

        @Override
        public BroadcastContext getContext() {
            return (BroadcastContext) super.getContext();
        }
    }
}
