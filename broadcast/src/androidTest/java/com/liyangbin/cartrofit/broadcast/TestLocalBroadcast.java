package com.liyangbin.cartrofit.broadcast;

import android.content.BroadcastReceiver;
import android.content.Intent;

import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Unregister;

@Broadcast(isLocal = true)
@GenerateId
public interface TestLocalBroadcast {

    @Send(action = "action.test", synced = true)
    void sendTestAction();

    @Send(action = "action.test", synced = true)
    void sendTestActionWithExtra(@Extra(key = "test_key") int args);

    @Send(action = "action.test", synced = true)
    void sendTestActionWithExtraPair(@ExtraPair String key, int args);

    @Receive(action = "action.test")
    void registerTestAction(OnTestListener listener);

    @Unregister(TestLocalBroadcastId.registerTestAction)
    void unregisterTestListener(OnTestListener listener);

    interface OnTestListener {
        void onReceive(Intent intent, @Extra(key = "test_key", defNumber = 10000) int args);
    }

    @Send(action = "action.test.second", synced = true)
    void sendTestSecondActionWithExtra(@Extra(key = "test_key_second") String args);

    @Register
    void registerMultipleReceiver(OnTestMultiListener listener);

    interface OnTestMultiListener {
        @Receive(action = "action.test")
        void onReceive(Intent intent, @Extra(key = "test_key", defNumber = 10000) int args);

        @Receive(action = "action.test.second")
        void onReceiveSecond(BroadcastReceiver receiver, @Extra(key = "test_key_second") String args);
    }

    @Unregister(TestLocalBroadcastId.registerMultipleReceiver)
    void unregisterMultipleReceiver(OnTestMultiListener listener);
}
