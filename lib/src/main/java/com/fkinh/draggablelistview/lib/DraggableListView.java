package com.fkinh.draggablelistview.lib;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

public class DraggableListView extends ListView {

    private static final int speed = 30;

    private long dragResponseMS = 600;

    private boolean isDrag = false;

    private int mDownX;

    private int mDownY;

    private int moveX;

    private int moveY;

    private int mDragPosition;

    private View mStartDragItemView = null;

    private ImageView mDragImageView;

    private Vibrator mVibrator;

    private WindowManager mWindowManager;

    private WindowManager.LayoutParams mWindowLayoutParams;

    private Bitmap mDragBmp;

    private int mPoint2ItemTop;

    private int mPoint2ItemLeft;

    private int mOffset2Top;

    private int mOffset2Left;

    private int mStatusHeight;

    private Runnable mLongClickRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                isDrag = true;
                mVibrator.vibrate(50);
                mStartDragItemView.setVisibility(View.INVISIBLE);
                createDragImage(mDragBmp, mDownX, mDownY);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private int mDownScrollBorder;

    private int mUpScrollBorder;

    private OnChangeListener onChangeListener;
    private Handler mHandler = new Handler();

    private Runnable mScrollRunnable = new Runnable() {
        @Override
        public void run() {
            int scrollY;
            if (moveY > mUpScrollBorder) {
                scrollY = -speed;
                mHandler.postDelayed(mScrollRunnable, 25);
            } else if (moveY < mDownScrollBorder) {
                scrollY = speed;
                mHandler.postDelayed(mScrollRunnable, 25);
            } else {
                scrollY = 0;
                mHandler.removeCallbacks(mScrollRunnable);
            }
            onSwapItem(moveX, moveY);

            View view = getChildAt(mDragPosition - getFirstVisiblePosition());
            smoothScrollToPositionFromTop(mDragPosition, view.getTop() + scrollY);
        }
    };

    public DraggableListView(Context context) {
        this(context, null);
    }

    public DraggableListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DraggableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (!isInEditMode()) {
            mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            mStatusHeight = getStatusBarHeight(context);
        }
    }

    /**
     * get status bar height
     * @param context
     * @return
     */
    private int getStatusBarHeight(Context context) {
        int statusHeight;
        Rect localRect = new Rect();
        ((Activity) context).getWindow().getDecorView().getWindowVisibleDisplayFrame(localRect);
        statusHeight = localRect.top;
        if (0 == statusHeight) {
            Class<?> localClass;
            try {
                localClass = Class.forName("com.android.internal.R$dimen");
                Object localObject = localClass.newInstance();
                int i5 = Integer.parseInt(localClass.getField("status_bar_height").get(localObject).toString());
                statusHeight = context.getResources().getDimensionPixelSize(i5);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return statusHeight;
    }


    public void setOnChangeListener(OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    public void setDragResponseMS(long dragResponseMS) {
        this.dragResponseMS = dragResponseMS;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mHandler.postDelayed(mLongClickRunnable, dragResponseMS);

                mDownX = (int) ev.getX();
                mDownY = (int) ev.getY();

                mDragPosition = pointToPosition(mDownX, mDownY);

                if (mDragPosition == AdapterView.INVALID_POSITION) {
                    return super.dispatchTouchEvent(ev);
                }

                mStartDragItemView = getChildAt(mDragPosition - getFirstVisiblePosition());

                mPoint2ItemTop = mDownY - mStartDragItemView.getTop();
                mPoint2ItemLeft = mDownX - mStartDragItemView.getLeft();

                mOffset2Top = (int) (ev.getRawY() - mDownY);
                mOffset2Left = (int) (ev.getRawX() - mDownX);

                mDownScrollBorder = getHeight() / 4;
                mUpScrollBorder = getHeight() * 3 / 4;

                mStartDragItemView.setDrawingCacheEnabled(true);
                mDragBmp = Bitmap.createBitmap(mStartDragItemView.getDrawingCache());
                mStartDragItemView.destroyDrawingCache();
                break;
            case MotionEvent.ACTION_MOVE:
                int moveX = (int) ev.getX();
                int moveY = (int) ev.getY();

                if (!isTouchInItem(mStartDragItemView, moveX, moveY)) {
                    mHandler.removeCallbacks(mLongClickRunnable);
                }
                break;
            case MotionEvent.ACTION_UP:
                mHandler.removeCallbacks(mLongClickRunnable);
                mHandler.removeCallbacks(mScrollRunnable);
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isTouchInItem(View dragView, int x, int y) {
        try {
            int leftOffset = dragView.getLeft();
            int topOffset = dragView.getTop();
            return !(x < leftOffset || x > leftOffset + dragView.getWidth()) && !(y < topOffset || y > topOffset + dragView.getHeight());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isDrag && mDragImageView != null) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    moveX = (int) ev.getX();
                    moveY = (int) ev.getY();
                    onDragItem(moveX, moveY);
                    break;
                case MotionEvent.ACTION_UP:
                    onStopDrag();
                    isDrag = false;
                    break;
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }

    private void createDragImage(Bitmap bitmap, int downX, int downY) {
        mWindowLayoutParams = new WindowManager.LayoutParams();
        mWindowLayoutParams.format = PixelFormat.TRANSLUCENT;
        mWindowLayoutParams.gravity = Gravity.TOP | Gravity.START;
        mWindowLayoutParams.x = downX - mPoint2ItemLeft + mOffset2Left;
        mWindowLayoutParams.y = downY - mPoint2ItemTop + mOffset2Top - mStatusHeight;
        mWindowLayoutParams.alpha = 0.35f;
        mWindowLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        mDragImageView = new ImageView(getContext());
        mDragImageView.setImageBitmap(bitmap);
        mWindowManager.addView(mDragImageView, mWindowLayoutParams);
    }

    private void removeDragImage() {
        if (mDragImageView != null) {
            mWindowManager.removeView(mDragImageView);
            mDragImageView = null;
        }
    }

    private void onDragItem(int moveX, int moveY) {
        mWindowLayoutParams.x = moveX - mPoint2ItemLeft + mOffset2Left;
        mWindowLayoutParams.y = moveY - mPoint2ItemTop + mOffset2Top - mStatusHeight;
        mWindowManager.updateViewLayout(mDragImageView, mWindowLayoutParams);
        onSwapItem(moveX, moveY);
        mHandler.post(mScrollRunnable);
    }

    private void onSwapItem(int moveX, int moveY) {
        int tempPosition = pointToPosition(moveX, moveY);
        if (tempPosition != mDragPosition && tempPosition != AdapterView.INVALID_POSITION) {
            getChildAt(tempPosition - getFirstVisiblePosition()).setVisibility(View.INVISIBLE);
            getChildAt(mDragPosition - getFirstVisiblePosition()).setVisibility(View.VISIBLE);

            if (onChangeListener != null) {
                onChangeListener.onChange(mDragPosition, tempPosition);
            }
            mDragPosition = tempPosition;
        }
    }

    private void onStopDrag() {
        getChildAt(mDragPosition - getFirstVisiblePosition()).setVisibility(View.VISIBLE);
        removeDragImage();
    }

    public interface OnChangeListener {
        void onChange(int form, int to);
    }

}
