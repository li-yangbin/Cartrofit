package com.saicmotor.hmi.aroundview;

import android.app.Activity;
import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;

import com.android.car.hvac.R;

public class AroundViewActivity extends Activity implements CompoundButton.OnCheckedChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new EffectView(this));

//        getWindow().getDecorView().setBackground(null);
//
//
//        this.<ToggleButton>findViewById(R.id.left1).setOnCheckedChangeListener(this);
//        this.<ToggleButton>findViewById(R.id.left2).setOnCheckedChangeListener(this);
//        this.<ToggleButton>findViewById(R.id.left3).setOnCheckedChangeListener(this);
//        this.<ToggleButton>findViewById(R.id.left4).setOnCheckedChangeListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final int visibility = isChecked ? View.VISIBLE : View.GONE;
        switch (buttonView.getId()) {
            case R.id.left1:
                findViewById(R.id.big_star_on).setVisibility(visibility);
                break;
            case R.id.left2:
                findViewById(R.id.big_star_off).setVisibility(visibility);
                break;
            case R.id.left3:
                findViewById(R.id.small_star_on).setVisibility(visibility);
                break;
            case R.id.left4:
                findViewById(R.id.small_star_off).setVisibility(visibility);
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            finish();
        }
        return true;
    }

    private static class EffectView extends View {

        private Drawable core;
        private int size;
        private float mMotionX;
        private float mMotionY;
        private Camera mCamera = new Camera();
        private Matrix mMatrix = new Matrix();

        private float pivotX;
        private float rotationX;

        public EffectView(Context context) {
            super(context);
            core = getResources().getDrawable(R.mipmap.ic_launcher);
            this.size = core.getIntrinsicWidth();
            core.setBounds(0 ,0 , size, size);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mMotionX = event.getX();
                    mMotionY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float motionX = event.getX();
                    float motionY = event.getY();

                    pivotX += motionX - mMotionX;
                    pivotX = Math.max(-3 * size, Math.min(3 * size, pivotX));

                    rotationX += (motionY - mMotionY) / (size * 2f) * 45f;
                    rotationX = Math.max(-60f, Math.min(60f, rotationX));

                    mMotionX = motionX;
                    mMotionY = motionY;

                    invalidate();
                    break;
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {

            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.translate(-pivotX, 0);

            canvas.save();
            mCamera.save();
            mCamera.translate(pivotX, 0, 0);
            mCamera.rotateX(-90 + rotationX - 10);
            mCamera.getMatrix(mMatrix);
            mCamera.restore();

            canvas.concat(mMatrix);
            core.draw(canvas);
            canvas.restore();

            canvas.save();
            mCamera.save();
            mCamera.translate(pivotX, 0, 0);
            mCamera.rotateX(-90 + rotationX + 10);
            mCamera.getMatrix(mMatrix);
            mCamera.restore();

            canvas.concat(mMatrix);
            core.draw(canvas);
            canvas.restore();
            canvas.restore();
        }
    }
}
