package com.liyangbin.cartrofit.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.liyangbin.cartrofit.Cartrofit;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    private static final String TAG = "TEST";

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals("com.liyangbin.cartrofit.broadcast.test", appContext.getPackageName());

        BroadcastContext.registerAsDefault(appContext);

        TestLocalBroadcast testApi = Cartrofit.from(TestLocalBroadcast.class);
        TestLocalBroadcast.OnTestListener listener;
        testApi.registerTestAction(listener = new TestLocalBroadcast.OnTestListener() {
            @Override
            public void onReceive(Intent intent, int args) {
                Log.i(TAG, "onReceive:" + intent + " args:" + args);
            }
        });
        TestLocalBroadcast.OnTestMultiListener multiListener;
        testApi.registerMultipleReceiver(multiListener = new TestLocalBroadcast.OnTestMultiListener() {
            @Override
            public void onReceive(Intent intent, int args) {
                Log.i(TAG, "onReceive multi args:" + args);
            }

            @Override
            public void onReceiveSecond(BroadcastReceiver receiver, String args) {
                Log.i(TAG, "onReceive multi Second receiver:" + receiver + " args:" + args);
            }
        });
        Log.i(TAG, "sendTestAction");
        testApi.sendTestAction();
        Log.i(TAG, "sendTestActionWithExtra");
        testApi.sendTestActionWithExtra(10086);
        Log.i(TAG, "sendTestActionWithExtraPair");
        testApi.sendTestActionWithExtraPair("test_key", 20086);
        testApi.unregisterTestListener(listener);
        Log.i(TAG, "sendTestActionWithExtra after unregister");
        testApi.sendTestActionWithExtra(10087);
        testApi.sendTestSecondActionWithExtra("hello second");
        testApi.unregisterMultipleReceiver(multiListener);
        Log.i(TAG, "sendTestActionWithExtra after multi unregister");
        testApi.sendTestActionWithExtra(10088);
    }
}
