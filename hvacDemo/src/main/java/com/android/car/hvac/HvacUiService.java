/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.hvac;

import android.car.Car;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.android.car.hvac.controllers.HvacPanelController;
import com.android.car.hvac.databinding.HvacPanelBinding;
import com.android.car.hvac.ui.TemperatureBarOverlay;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.Command;
import com.liyangbin.cartrofit.Interceptor;

import java.util.ArrayList;
import java.util.List;


/**
 * Creates a sliding panel for HVAC controls and adds it to the window manager above SystemUI.
 */
public class HvacUiService extends AppCompatActivity {
    public static final String CAR_INTENT_ACTION_TOGGLE_HVAC_CONTROLS =
            "android.car.intent.action.TOGGLE_HVAC_CONTROLS";
    private static final String TAG = "HvacUiService";

    private final List<View> mAddedViews = new ArrayList<>();

    private WindowManager mWindowManager;

    private View mContainer;

    private int mNavBarHeight;
    private int mPanelCollapsedHeight;
    private int mPanelFullExpandedHeight;
    private int mScreenBottom;
    private int mScreenWidth;
    // This is to compensate for the difference between where the y coordinate origin is and that
    // of the actual bottom of the screen.
    private int mInitialYOffset = 0;
    private DisplayMetrics mDisplayMetrics;

    private int mTemperatureSideMargin;
    private int mTemperatureOverlayWidth;
    private int mTemperatureOverlayHeight;

    private HvacPanelController mHvacPanelController;
    private HvacController mHvacController;

    // we need both a expanded and collapsed version due to a rendering bug during window resize
    // thus instead we swap between the collapsed window and the expanded one before/after they
    // are needed.
    private TemperatureBarOverlay mDriverTemperatureBar;
    private TemperatureBarOverlay mPassengerTemperatureBar;
    private TemperatureBarOverlay mDriverTemperatureBarCollapsed;
    private TemperatureBarOverlay mPassengerTemperatureBarCollapsed;
    private FrameLayout mContent;
    private HvacPanelBinding mBinding;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(mContent = new FrameLayout(this));
        Resources res = getResources();
        boolean showCollapsed = res.getBoolean(R.bool.config_showCollapsedBars);
        mPanelCollapsedHeight = res.getDimensionPixelSize(R.dimen.car_hvac_panel_collapsed_height);
        mPanelFullExpandedHeight
                = res.getDimensionPixelSize(R.dimen.car_hvac_panel_full_expanded_height);

        mTemperatureSideMargin = res.getDimensionPixelSize(R.dimen.temperature_side_margin);
        mTemperatureOverlayWidth =
                res.getDimensionPixelSize(R.dimen.temperature_bar_width_expanded);
        mTemperatureOverlayHeight
                = res.getDimensionPixelSize(R.dimen.car_hvac_panel_full_expanded_height);

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mDisplayMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(mDisplayMetrics);
        mScreenBottom = mDisplayMetrics.heightPixels;
        mScreenWidth = mDisplayMetrics.widthPixels;

        int identifier = res.getIdentifier("navigation_bar_height_car_mode", "dimen", "android");
        mNavBarHeight = (identifier > 0 && showCollapsed) ?
                res.getDimensionPixelSize(identifier) : 0;

        // There does not exist a way to get the current state of the system ui visibility from
        // inside a Service thus we place something that's full screen and check it's final
        // measurements as a hack to get that information. Once we have the initial state  we can
        // safely just register for the change events from that point on.
        Log.i(TAG, "onCreate");
        layoutHvacUi();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CAR_INTENT_ACTION_TOGGLE_HVAC_CONTROLS);
        // Register receiver such that any user with climate control permission can call it.
        registerReceiver(mBroadcastReceiver, filter,
                Car.PERMISSION_CONTROL_CAR_CLIMATE, null);
    }

    private WindowManagerSelf mSelfWm = new WindowManagerSelf();

    private class WindowManagerSelf implements WindowManager {

        @Override
        public Display getDefaultDisplay() {
            return null;
        }

        @Override
        public void removeViewImmediate(View view) {
            removeView(view);
        }

        @Override
        public void addView(View view, ViewGroup.LayoutParams params) {
            FrameLayout.LayoutParams lp = convert((LayoutParams) params);
            mContent.addView(view, lp);
        }

        @Override
        public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
            view.setLayoutParams(params);
            view.requestLayout();
        }

        @Override
        public void removeView(View view) {
            mContent.removeView(view);
        }
    }

    private FrameLayout.LayoutParams convert(WindowManager.LayoutParams lp) {
        FrameLayout.LayoutParams result = new FrameLayout.LayoutParams(lp.width, lp.height);
        result.gravity = lp.gravity;
        if ((lp.gravity & Gravity.BOTTOM) != 0) {
            result.bottomMargin = lp.y;
        } else {
            result.topMargin = lp.y;
        }
        if ((lp.gravity & (Gravity.RIGHT | Gravity.END)) != 0) {
            result.rightMargin = lp.x;
        } else {
            result.leftMargin = lp.x;
        }
        return result;
    }

    /**
     * Called after the mInitialYOffset is determined. This does a layout of all components needed
     * for the HVAC UI. On start the all the windows need for the collapsed view are visible whereas
     * the expanded view's windows are created and sized but are invisible.
     */
    private void layoutHvacUi() {
        Log.i(TAG, "layoutHvacUi");
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        & ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        params.packageName = this.getPackageName();

        params.width = mScreenWidth;
        params.height = mScreenBottom;
        params.setTitle("HVAC Container");
        disableAnimations(params);
        // required of the sysui visiblity listener is not triggered.
//        params.hasSystemUiListeners = true;

        mBinding = DataBindingUtil
                .inflate(inflater, R.layout.hvac_panel, null, false);
        mContainer = mBinding.getRoot();
        mContainer.setLayoutParams(params);
//        mContainer.setOnSystemUiVisibilityChangeListener(visibility -> {
//            boolean systemUiVisible = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
//            int y = 0;
//            if (systemUiVisible) {
//                // when the system ui is visible the windowing systems coordinates start with
//                // 0 being above the system navigation bar. Therefore if we want to get the the
//                // actual bottom of the screen we need to set the y value to negative value of the
//                // navigation bar height.
//                y = -mNavBarHeight;
//            }
//            setYPosition(mDriverTemperatureBar, y);
//            setYPosition(mPassengerTemperatureBar, y);
//            setYPosition(mDriverTemperatureBarCollapsed, y);
//            setYPosition(mPassengerTemperatureBarCollapsed, y);
//            setYPosition(mContainer, y);
//        });

        // The top padding should be calculated on the screen height and the height of the
        // expanded hvac panel. The space defined by the padding is meant to be clickable for
        // dismissing the hvac panel.
//        int topPadding = mScreenBottom - mPanelFullExpandedHeight;
//        mContainer.setPadding(0, topPadding, 0, 0);

        mContainer.setFocusable(false);
        mContainer.setFocusableInTouchMode(false);

        addViewToWindowManagerAndTrack(mContainer, params);

        createTemperatureBars(inflater);
        mHvacPanelController = new HvacPanelController(this /* context */, mContainer,
                mSelfWm, mDriverTemperatureBar, mPassengerTemperatureBar,
                mDriverTemperatureBarCollapsed, mPassengerTemperatureBarCollapsed);
        mBinding.setController(mHvacPanelController);
        Intent bindIntent = new Intent(this /* context */, HvacController.class);
        if (!bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to connect to HvacController.");
        }
    }

    private void addViewToWindowManagerAndTrack(View view, WindowManager.LayoutParams params) {
        mSelfWm.addView(view, params);
        mAddedViews.add(view);
    }

    private void setYPosition(View v, int y) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
        lp.y = y;
        mWindowManager.updateViewLayout(v, lp);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(CAR_INTENT_ACTION_TOGGLE_HVAC_CONTROLS)){
                mHvacPanelController.toggleHvacUi();
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (View view : mAddedViews) {
            mSelfWm.removeView(view);
        }
        mAddedViews.clear();
        if(mHvacController != null){
            unbindService(mServiceConnection);
        }
        unregisterReceiver(mBroadcastReceiver);
        mBinding.unbind();
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mHvacController = ((HvacController.LocalBinder) service).getService();
            final Context context = HvacUiService.this;

            final Runnable r = () -> {
                // Once the hvac controller has refreshed its values from the vehicle,
                // bind all the values.
                mHvacPanelController.updateHvacController(mHvacController);
            };


            if (mHvacController != null) {
                mHvacController.requestRefresh(r, new Handler(context.getMainLooper()));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mHvacController = null;
            mHvacPanelController.updateHvacController(null);
            //TODO: b/29126575 reconnect to controller if it is restarted
        }
    };

    private void createTemperatureBars(LayoutInflater inflater) {
        mDriverTemperatureBarCollapsed = createTemperatureBarOverlay(inflater,
                "HVAC Driver Temp collapsed",
                mNavBarHeight,
                Gravity.BOTTOM | Gravity.LEFT);

        mPassengerTemperatureBarCollapsed = createTemperatureBarOverlay(inflater,
                "HVAC Passenger Temp collapsed",
                mNavBarHeight,
                Gravity.BOTTOM | Gravity.RIGHT);

        mDriverTemperatureBar = createTemperatureBarOverlay(inflater,
                "HVAC Driver Temp",
                mTemperatureOverlayHeight,
                Gravity.BOTTOM | Gravity.LEFT);

        mPassengerTemperatureBar = createTemperatureBarOverlay(inflater,
                "HVAC Passenger Temp",
                mTemperatureOverlayHeight,
                Gravity.BOTTOM | Gravity.RIGHT);
    }

    private TemperatureBarOverlay createTemperatureBarOverlay(LayoutInflater inflater,
            String windowTitle, int windowHeight, int gravity) {
        WindowManager.LayoutParams params = createTemperatureBarLayoutParams(
                windowTitle, windowHeight, gravity);
        TemperatureBarOverlay button = (TemperatureBarOverlay) inflater
                .inflate(R.layout.hvac_temperature_bar_overlay, null);
        button.setLayoutParams(params);
        addViewToWindowManagerAndTrack(button, params);
        return button;
    }

    // note the window manager does not copy the layout params but uses the supplied object thus
    // you need a new copy for each window or change 1 can effect the others
    private WindowManager.LayoutParams createTemperatureBarLayoutParams(String windowTitle,
            int windowHeight, int gravity) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.x = mTemperatureSideMargin;
        lp.y = mInitialYOffset;
        lp.width = mTemperatureOverlayWidth;
        disableAnimations(lp);
        lp.setTitle(windowTitle);
        lp.height = windowHeight;
        lp.gravity = gravity;
        return lp;
    }

    /**
     * Disables animations when window manager updates a child view.
     */
    private void disableAnimations(WindowManager.LayoutParams params) {
        try {
            int currentFlags = (Integer) params.getClass().getField("privateFlags").get(params);
            params.getClass().getField("privateFlags").set(params, currentFlags | 0x00000040);
        } catch (Exception e) {
            Log.e(TAG, "Error disabling animation");
        }
    }
}
