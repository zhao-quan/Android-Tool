package com.septem.firstapp.product;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于获取touchEvent，然后设置针对不同的触摸模式的监听器。使用方法是，先获取对象，然后在onTouchEvent里面
 * 设置成"return touchEventManager.onTouchEvent(event);"。在合适的地方设置监听器。这里提供了一个adapter，
 * 已经实现了监听器interface的方法，所以，设置这个adapter覆盖里面需要的方法即可。
 *
 * <p>
 * <br>--onDoubleTap,单指双击，如果有其他触点，双击将不起作用
 * <br>--onUp,双击的时候第二下不会触发，其他情况下会触发（未严格测试）
 * <br>--onMove，单触点移动的时候触发
 * <br>--onZoom，多触点移动的时候触发，如果要监测多触电移动，也需要在这里设置
 * <br>--onDown，单触点接触
 * <br>--onMultiDown，多触点接触
 * <br>--onFlip，快速划动，触发这个以后并不会触发onUp
 * <p>
 * Created by septem on 2016/10/9.
 */

public class TouchEventManager {
    private final int NONE = 0;
    private final int ZOOM = 1;
    private final int MOVE = 2;

    private int tapIntervalMax = 300;
    private double tapDistanceMax = 20;
    private int downUpIntervalMax = 150;
    private double flipDistanceMax = 20;
    private int flipIntervalMax = 100;
    private OnTouchAdapter mOnTouchAdapter = null;
    private Activity activity;

    //这些变量是自用的，不需要设置
    private int mAction = NONE;
    private Point downPoint;
    private long downTime = 0;
    private long upTime = 0;
    private long lastTap = 0;
    private long tap = 0;
    private double speed = 0;
    private long interval = 0;
    private double distance = 0;
    private List<Point> pointList = new ArrayList<>();
    private List<Long> timeList = new ArrayList<>();

    public TouchEventManager(Context context) {
        this.activity = (Activity) context;
    }

    private double distanceOf(Point p1,Point p2) {
        return Math.sqrt((p1.x-p2.x)*(p1.x-p2.x)+(p1.y-p2.y)*(p1.y-p2.y));
    }

    public boolean onTouchEvent(MotionEvent event){
        int pointCount = event.getPointerCount();
        Point p = new Point((int)event.getX(),(int)event.getY());
        if(mOnTouchAdapter!=null)
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mAction = MOVE;
                    downTime = System.currentTimeMillis();
                    downPoint = p;
                    interval = 0;
                    distance = 0;
                    speed = 0;
                    timeList.add(System.currentTimeMillis());
                    pointList.add(p);
                    return mOnTouchAdapter.onDown(event);
                case MotionEvent.ACTION_POINTER_DOWN:
                    mAction = ZOOM;
                    return mOnTouchAdapter.onMultiDown(event);
                case MotionEvent.ACTION_MOVE:
                    if(mAction==MOVE) {
                        timeList.add(System.currentTimeMillis());
                        pointList.add(p);
                        return mOnTouchAdapter.onMove(event);
                    }
                    if(mAction==ZOOM)
                        return mOnTouchAdapter.onZoom(event);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                    upTime = System.currentTimeMillis();
                    //计算double tap
                    if(upTime - downTime<=downUpIntervalMax&&
                            distanceOf(downPoint,p)<=tapDistanceMax) {
                        tap += 1;
                        if(upTime-lastTap>tapIntervalMax) {
                            tap = 1;
                        }
                        lastTap = upTime;
                    }
                    if(tap==2) {
                        tap = 0;
                        if(pointCount==1)
                            mOnTouchAdapter.onDoubleTap(event);
                    }
                    //计算flip
                    if(mAction==MOVE) {
                        for (int i = timeList.size() - 1;
                             upTime - timeList.get(i) < flipIntervalMax && i > 0;
                             i--) {
                            interval = upTime - timeList.get(i);
                            distance = distanceOf(p, pointList.get(i));
                            speed = distance / interval;
                            if (distance > flipDistanceMax&&speed>1.5)
                                return mOnTouchAdapter.onFlip(
                                        p.x-pointList.get(i).x,
                                        p.y-pointList.get(i).y,
                                        speed,
                                        interval,
                                        event);
                        }
                    }

                    timeList.clear();
                    pointList.clear();
                    mAction = NONE;
                    return mOnTouchAdapter.onUp(event);
                case MotionEvent.ACTION_CANCEL:
                    mAction = NONE;
                    return mOnTouchAdapter.onUp(event);
            }
        return false;
    }

    public void setOnTouchAdapter(OnTouchAdapter adapter) {
        this.mOnTouchAdapter = adapter;
    }

    //Adapter
    public static class OnTouchAdapter implements OnTouchListener {
        @Override
        public boolean onFlip(int xOffset, int yOffset,
                              double speed, long interval, MotionEvent event) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onMultiDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onMove(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onZoom(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onUp(MotionEvent event) {
            return true;
        }
    }

    //Listener
    public interface OnTouchListener {
        boolean onDoubleTap(MotionEvent event);
        boolean onMultiDown(MotionEvent event);
        boolean onMove(MotionEvent event);
        boolean onZoom(MotionEvent event);
        boolean onDown(MotionEvent event);
        boolean onUp(MotionEvent event);
        boolean onFlip(int xOffset,int yOffset,double speed,long interval, MotionEvent event);
    }
}
