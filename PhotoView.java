package com.septem.firstapp.product;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by septem on 2016/10/9.
 */

public class PhotoView extends View {
    //final variable FIELD
    private final int BLOCK_SIZE = 2048;
    private final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.RGB_565;

    //private variable FIELD
    private Uri uri;
    private Activity activity;
    private Toast toast;
    private TouchEventManager touchEventManager;
    private int inSampleSize = 1;
    private int inSampleSizeMax = 1;
    private List<ReLoadBlockTask> reLoadList = new ArrayList<>();
    private Matrix mMatrix = new Matrix();         //全局变换用matrix
    private Matrix bitmapMatrix = new Matrix();    //绘制block用matrix
    private PointF imageCenter = new PointF();     //永远指向图像中心
    private Point screenCenter = new Point();      //屏幕中心
    private Point imageSize;                       //图像原始长宽分辨率
    private Point screenSize = new Point();        //屏幕分辨率
    private int orientation = 0;                   //图像旋转角度，记录在exif中
    private float zoomRatio = 1.0f;                //当前缩放倍率
    private float maxZoom = 4.0f;                  //最大缩放倍率
    private float widthFitRatio = 0;               //图像width适应屏幕width的图像缩放倍率
    private float heightFitRatio = 0;              //图像height适应屏幕height的图像缩放倍率
    private float flipRate = 300;                  //控制flip速度的比率
    private List<Float> ratioList =
            new ArrayList<>();              //存放(widthFitRatio,heightFitRatio,1f),用于sizeFitLoop

    private Paint mPaint = null;
    private TimeInterpolator zoomInterpolator = new DecelerateInterpolator();
    private TimeInterpolator moveInterpolator = new DecelerateInterpolator();
    private int animDuration = 300;
    private boolean isImageReady = false;          //图像是否读取完毕
    private Block block;
    private List<Block> blockList = new ArrayList();//保存block列表

    //constructor
    public PhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if(isInEditMode())return;

        activity = (Activity)context;
        toast = Toast.makeText(activity," ",Toast.LENGTH_SHORT);
        touchEventManager = new TouchEventManager(context);
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getSize(screenSize);
        screenCenter.x = screenSize.x/2;
        screenCenter.y = screenSize.y/2;

        setTouchListeners();
    }

    //public FIELD

    /**
     * 用bitmap创建用于显示的图像。
     * @param bm 图像的原始Bitmap
     */
    public void setBitmap(Bitmap bm) {
        LoadBitmapTask loadBitmapTask = new LoadBitmapTask(bm);
        loadBitmapTask.execute();
    }

    /**
     * 用uri创建用于显示的图像
     * @param uri 用于被显示的图像的uri，可以是content：也可以是file：
     */
    public void setUri(Uri uri) {
        this.uri = uri;
        LoadBitmapTask loadBitmapTask = new LoadBitmapTask(uri);
        loadBitmapTask.execute();
    }

    /**
     * 设置最大放大比例,必须设置成大于0的float值。原图1像素对应屏幕1个像素，放大率=1；
     * 如果屏幕2个像素对应原图1个像素，放大率=2。以此类推。这样设置是因为类似一些相片处理程序的百分比模式。
     * @param maxZoom 最大放大比例，如果设置值小于等于0，则不变。
     */
    public void setMaxZoom(float maxZoom) {
        if(maxZoom>0)
            this.maxZoom = maxZoom;
    }

    /**
     * 刷新显示和VIEW
     */
    public void resetImage() {
        postTranslate(screenCenter.x-imageCenter.x,screenCenter.y-imageCenter.y);
        postScale(widthFitRatio<heightFitRatio?widthFitRatio:heightFitRatio);
        invalidate();
    }

    /**
     * 重置图像到屏幕正中，并且大小适应屏幕大小
     */
    public void initImage() {
        postTranslate(screenCenter.x-imageCenter.x,screenCenter.y-imageCenter.y);
        float ratioX = (float)screenSize.x/imageSize.x;
        float ratioY = (float)screenSize.y/imageSize.y;
        float ratio = ratioX<ratioY?ratioX:ratioY;
        postScale(ratio);
        isImageReady = true;
        invalidate();
        refreshBlocks();
    }

    /**
     * 改变图像的显示方向，横向的变成纵向，纵向的变成横向。同时也改变长宽数据对应改变以后的方向。
     * 最后显示的时候会重置图像到适应屏幕的大小。
     */
    public void changeDirection() {
        int r = 0;
        switch (orientation) {
            case 0:
                r = 90;
                orientation = 90;
                break;
            case 180:
                r = 90;
                orientation = 270;
                break;
            case 90:
                r = -90;
                orientation = 0;
                break;
            case 270:
                r= -90;
                orientation = 180;
                break;
        }
        mMatrix.postRotate(r,imageCenter.x,imageCenter.y);
        int tmp = imageSize.x;
        imageSize.x = imageSize.y;
        imageSize.y = tmp;
        getRatios();
        postScale(ratioList.get(0)/zoomRatio);
        postTranslate(screenCenter.x-imageCenter.x,screenCenter.y-imageCenter.y);
        invalidate();
        refreshBlocks();
    }

    //private FIELD
    private void toasts(String msg) {
        toast.setText(msg);
        toast.show();
    }

    /**
     * 移动图像，并且也移动图像中心点坐标以适应新图像。之后需要在合适的地方调用invalidate()
     * @param xOffset
     * @param yOffset
     */
    private void postTranslate(float xOffset,float yOffset) {
        mMatrix.postTranslate(xOffset,yOffset);
        imageCenter.offset(xOffset,yOffset);
    }

    /**
     * 以图像的中心点为坐标缩放图像，也改变zoomRatio值，这个值代表图像当前缩放比例。
     * 如果需要当前长宽值，只需要用原始图像长宽值乘以zoomRatio。
     * 之后需要在合适的地方调用invalidate()
     * @param ratio 缩放的比例
     */
    private void postScale(float ratio) {
        mMatrix.postScale(ratio,ratio,imageCenter.x,imageCenter.y);
        zoomRatio *= ratio;
    }

    /**
     * 缩放图像，需要设置缩放的中心点，也改变zoomRatio值，这个值代表图像当前缩放比例。
     * 因为缩放中心不在图像中心，所以也需要改变图像中心的坐标。
     * 如果需要当前长宽值，只需要用原始图像长宽值乘以zoomRatio。
     * 之后需要在合适的地方调用invalidate()
     * @param ratio 缩放的比例
     * @param x 缩放中心的x坐标
     * @param y 缩放中心的y坐标
     */
    private void postScale(float ratio,int x,int y) {
        mMatrix.postScale(ratio,ratio,x,y);
        zoomRatio *= ratio;
        float Xoffset = (ratio-1)*(imageCenter.x-x);
        float Yoffset = (ratio-1)*(imageCenter.y-y);
        imageCenter.offset(Xoffset,Yoffset);
    }

    /**
     * 以图像中心点为中心旋转图像。这个方法只用作把图像旋转以适应屏幕方向，所以会根据角度改变图像的长宽数值。
     * 如果是直接旋转，就把degrees和orientation设置成一样的。如果是从某个中间角度旋转，就把orientation设置
     * 成目标值，然后把degrees设置成 （目标orientation-当前角度）。
     * 之后需要在合适的地方调用invalidate()
     * @param degrees 旋转的角度
     * @param orientation 旋转以后图像的orientation值
     */
    private void postRotateFitScreen(float degrees,int orientation) {
        mMatrix.postRotate(degrees,imageCenter.x,imageCenter.y);
        if(orientation==90||orientation==270) {
            int tmp = imageSize.x;
            imageSize.x = imageSize.y;
            imageSize.y = tmp;
        }
        getRatios();
    }

    /**
     * 旋转图像，需要设置旋转的中心点。因为不知道是否改变图像方位，所以并没有改变图像的长宽数值。
     * 之后需要在合适的地方调用invalidate()
     * @param degrees 旋转的角度
     * @param x 旋转的中心点x
     * @param y 旋转的中心点y
     */
    private void postRotate(float degrees,int x,int y) {
        mMatrix.postRotate(degrees,x,y);
    }

    /**
     * 定义触摸事件监听器，方法是设置touchEventManager.setOnTouchAdapter,然后覆盖里面的
     * 针对不同触摸事件的方法
     */
    private void setTouchListeners() {
        touchEventManager.setOnTouchAdapter(new TouchEventManager.OnTouchAdapter(){
            Point midPoint = new Point();
            Point p1 = new Point();
            Point p2 = new Point();
            Point fromPoint = new Point();
            Point toPoint = new Point();
            double distanceStart = 0;
            double distanceNow = 0;

            private void doMove() {
                if(exactFloat(zoomRatio)<=exactFloat(widthFitRatio))
                    toPoint.x = fromPoint.x;
                if(exactFloat(zoomRatio)<=exactFloat(heightFitRatio))
                    toPoint.y = fromPoint.y;
                postTranslate(toPoint.x- fromPoint.x, toPoint.y- fromPoint.y);
                fromPoint.set(toPoint.x,toPoint.y);
            }

            @Override
            public boolean onDown(MotionEvent event) {
                fromPoint.set((int)event.getX(),(int)event.getY());
                return super.onDown(event);
            }

            @Override
            public boolean onMove(MotionEvent event) {
                if(isImageReady)
                    refreshBlocks();
                toPoint.x = (int)event.getX();
                toPoint.y = (int)event.getY();
                doMove();
                invalidate();
                return super.onMove(event);
            }

            @Override
            public boolean onMultiDown(MotionEvent event) {
                p1.x = (int) event.getX(0);
                p1.y = (int) event.getY(0);
                p2.x = (int) event.getX(1);
                p2.y = (int) event.getY(1);
                int x = p1.x-p2.x;
                int y = p1.y-p2.y;
                distanceStart = Math.sqrt(x*x+y*y);
                midPoint.set((p1.x+p2.x)/2,(p1.y+p2.y)/2);
                fromPoint.set(midPoint.x,midPoint.y);
                return super.onMultiDown(event);
            }

            @Override
            public boolean onZoom(MotionEvent event) {
                if(isImageReady)
                    refreshBlocks();

                p1.x = (int) event.getX(0);
                p1.y = (int) event.getY(0);
                p2.x = (int) event.getX(1);
                p2.y = (int) event.getY(1);
                int x = p1.x-p2.x;
                int y = p1.y-p2.y;
                distanceNow = Math.sqrt(x*x+y*y);
                midPoint.set((p1.x+p2.x)/2,(p1.y+p2.y)/2);
                float ratio = (float)(distanceNow/distanceStart);
                toPoint.set(midPoint.x,midPoint.y);
                if(zoomRatio<=maxZoom||ratio<1) {
                    postScale(ratio, midPoint.x, midPoint.y);
                }
                doMove();
                distanceStart = distanceNow;
                invalidate();
                return super.onZoom(event);
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                sizeFitLoop(new Point((int)event.getX(),(int)event.getY()));
                return super.onDoubleTap(event);
            }

            @Override
            public boolean onUp(MotionEvent event) {
                float topOffset = imageCenter.y - zoomRatio*imageSize.y/2;
                float bottomOffset =screenSize.y - (imageCenter.y + zoomRatio*imageSize.y/2);
                float leftOffset = imageCenter.x - zoomRatio*imageSize.x/2;
                float rightOffset = screenSize.x - (imageCenter.x + zoomRatio*imageSize.x/2);

                if(zoomRatio<ratioList.get(0))
                {
                    ValueAnimator zAnim = makeZoomAnimator(ratioList.get(0),null);
                    ValueAnimator mAnim = makeMoveAnimator(screenCenter);
                    AnimatorSet animSet = new AnimatorSet();
                    animSet.playTogether(zAnim,mAnim);
                    animSet.start();
                }
                else {
                    Point targetPoint = new Point();
                    float xOffset;
                    float yOffset;

                    if(topOffset<=0&&bottomOffset<=0)
                        targetPoint.y = (int)imageCenter.y;
                    else{
                        yOffset = topOffset>bottomOffset ? -topOffset:bottomOffset;
                        yOffset = Math.abs(yOffset)<Math.abs(screenCenter.y-imageCenter.y)
                                ?yOffset:(screenCenter.y-imageCenter.y);
                        targetPoint.y = (int)(imageCenter.y + yOffset);
                    }


                    if(leftOffset<=0&&rightOffset<=0)
                        targetPoint.x = (int)imageCenter.x;
                    else {
                        xOffset = leftOffset>rightOffset ? -leftOffset:rightOffset;
                        xOffset = Math.abs(xOffset)<Math.abs(screenCenter.x-imageCenter.x)
                                ?xOffset:(screenCenter.x-imageCenter.x);
                        targetPoint.x = (int)(imageCenter.x + xOffset);
                    }

                    if(targetPoint.x!=(int)imageCenter.x||targetPoint.y!=(int)imageCenter.y)
                        makeMoveAnimator(targetPoint).start();

                }

                return super.onUp(event);
            }

            @Override
            public boolean onFlip(final int xOffset, final int yOffset,
                                  double speed, long interval,final MotionEvent event) {

                float left = 0 - (imageCenter.x-zoomRatio*imageSize.x/2);
                float right = screenSize.x - (imageCenter.x+zoomRatio*imageSize.x/2);
                left = left>0?xOffset:0;
                right = right<0?xOffset: 0;

                float top = 0 - (imageCenter.y-zoomRatio*imageSize.y/2);
                float bottom = screenSize.y -(imageCenter.y+zoomRatio*imageSize.y/2);
                top = top>0?yOffset:0;
                bottom = bottom<0?yOffset:0;

                float x,y;
                x = xOffset>0?left:right;
                y = yOffset>0?top:bottom;

                ValueAnimator anim =
                makeMoveAnimator(new Point((int)imageCenter.x+(int)(x*flipRate/interval),
                                           (int)imageCenter.y+(int)(y*flipRate/interval)));
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onUp(event);
                    }
                });
                anim.start();

                return super.onFlip(xOffset, yOffset, speed, interval, event);
            }
        });
    }

    /**
     * 双击屏幕以后，在三个缩放比例中循环，分别是widthFitRatio,heightFitRatio,1.0f
     * @param center
     */
    private void sizeFitLoop(Point center) {
        Point zoomCenter = center;
        float targetZoom = zoomRatio;
        boolean getIt = false;
        for(float r :ratioList)
            if(exactFloat(r)>exactFloat(targetZoom)) {
                targetZoom = r;
                getIt = true;
                break;
            }
        if(!getIt)
            targetZoom = ratioList.get(0);

        if(targetZoom<=heightFitRatio)
            zoomCenter.y = screenCenter.y;
        if(targetZoom<=widthFitRatio)
            zoomCenter.x = screenCenter.x;
        if(targetZoom<=widthFitRatio&&targetZoom<=heightFitRatio)
        {
            ValueAnimator zAnim = makeZoomAnimator(targetZoom,zoomCenter);
            ValueAnimator mAnim = makeMoveAnimator(screenCenter);
            AnimatorSet animSet = new AnimatorSet();
            animSet.playTogether(zAnim,mAnim);
            animSet.start();
        }
        else
            makeZoomAnimator(targetZoom,zoomCenter).start();
    }

    /**
     * 创建一个图像移动变换动画。fromPoint就是imageCenter。
     * 如果需要Point就手动转换一下。
     * @param toPoint 移动的目标点
     * @return
     */
    private ValueAnimator makeMoveAnimator(Point toPoint) {
        PropertyValuesHolder xMoveHolder =
                PropertyValuesHolder.ofInt("xMove", (int)imageCenter.x,toPoint.x);
        PropertyValuesHolder yMoveHolder =
                PropertyValuesHolder.ofInt("yMove", (int)imageCenter.y,toPoint.y);
        ValueAnimator anim = ValueAnimator.ofPropertyValuesHolder(xMoveHolder,yMoveHolder);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                postTranslate((int)animation.getAnimatedValue("xMove")-imageCenter.x,
                        (int)animation.getAnimatedValue("yMove")-imageCenter.y);
                refreshBlocks();
                invalidate();
            }
        });
        anim.setDuration(animDuration);
        anim.setInterpolator(moveInterpolator);
        return anim;
    }

    /**
     * 创建一个图像缩放动画。因为fromRatio就是zoomRatio值，所以只需要设置toRatio。
     * 如果center设置为null，表示以以imageCenter为中心缩放，所以采用不设置缩放中心的postScale()方法。
     * 注意在需要和makeMoveAnimator一起用的时候，需要center设置为null,否则会产生imageCenter的不到位.
     * @param toRatio 缩放的目标比例，也就是目标zoomRatio值
     * @param center 缩放的中心点坐标。null表示以imageCenter为中心点
     * @return
     */
    private ValueAnimator makeZoomAnimator(float toRatio,@Nullable final Point center) {
        ValueAnimator anim = ValueAnimator.ofFloat(zoomRatio,toRatio);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if(center==null)
                    postScale((float)animation.getAnimatedValue()/zoomRatio);
                else
                    postScale((float)animation.getAnimatedValue()/zoomRatio,center.x,center.y);
                refreshBlocks();
                invalidate();
            }
        });
        anim.setDuration(animDuration);
        anim.setInterpolator(zoomInterpolator);
        return anim;
    }


    /**
     * 把float精确到小数点后两位并且四舍五入
     * @param input
     * @return
     */
    private float exactFloat(float input) {
        return (float)Math.round(input*100)/100;
    }

    /**
     * 获取图像的orientation信息，将来或许会改成获取所有exif信息
     * @param uri
     */
    private void getOrientation(Uri uri) {
        String path = null;
        if(uri==null)
            return ;
        String scheme = uri.getScheme();
        if(scheme==null||scheme.equals(ContentResolver.SCHEME_FILE))
            path = uri.getPath();
        else if(scheme.equals(ContentResolver.SCHEME_CONTENT)) {
            Cursor cursor = activity.getContentResolver().query(
                    uri,
                    new String[]{MediaStore.Images.Media.DATA},
                    null,
                    null,
                    null
            );
            if(cursor!=null&&cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(
                        MediaStore.Images.Media.DATA
                ));
            }
        }
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(path);
            int oriInfo = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,0);
            switch (oriInfo) {
                case 6:
                    orientation = 90;
                    break;
                case 3:
                    orientation = 180;
                    break;
                case 8:
                    orientation = 270;
                    break;
                default:
                    orientation = 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获得图像的各种ratio信息，并存入ratioList
     */
    private void getRatios() {
        ratioList.clear();
        widthFitRatio = (float)screenSize.x/imageSize.x;
        heightFitRatio = (float)screenSize.y/imageSize.y;
        ratioList.add(1.0f);
        ratioList.add(widthFitRatio);
        ratioList.add(heightFitRatio);
        Collections.sort(ratioList);
        for(float r :ratioList) {
            if(r>maxZoom)
                ratioList.remove(r);
        }
    }

    /**
     * 监测x方向的边界是否在屏幕以内
     * @return
     */
    private boolean xEdgeInScreen() {
        return ((imageCenter.x+zoomRatio*imageSize.x/2)<=screenSize.x
                ||(imageCenter.x-zoomRatio*imageSize.x/2)>=0);

    }

    /**
     * 计算inSampleSize值
     * @return
     */
    private int calculateInSampleSize(float ratio) {
        int inSampleSize = 1;
        while(1/ratio/inSampleSize>=2){
            inSampleSize *=2;
        }
        return inSampleSize;
    }

    /**
     * 设置block的visible值
     */
    private void setVisibilities() {
        if(orientation==0) {
            float left, top, right, bottom;
            float xOrigin = imageCenter.x - zoomRatio * imageSize.x / 2;
            float yOrigin = imageCenter.y - zoomRatio * imageSize.y / 2;
            for (int i = 0; i < blockList.size(); i++) {
                Block b = blockList.get(i);
                left = b.left * zoomRatio + xOrigin;
                right = b.right * zoomRatio + xOrigin;
                top = b.top * zoomRatio + yOrigin;
                bottom = b.bottom * zoomRatio + yOrigin;
                if (right < 0 || left > screenSize.x || top > screenSize.y || bottom < 0)
                    b.visible = false;
                else b.visible = true;
            }
        }
        else if(orientation ==90){
            float left, top, right, bottom;
            float tmpTop,tmpBottom,tmpRight,tmpLeft;
            float xOrigin = imageCenter.x - zoomRatio * imageSize.x / 2;
            float yOrigin = imageCenter.y - zoomRatio * imageSize.y / 2;
            for (int i = 0; i < blockList.size(); i++) {
                Block b = blockList.get(i);

                tmpTop = b.left;
                tmpLeft = imageSize.x - b.bottom;
                tmpBottom = b.right;
                tmpRight = imageSize.x - b.top;

                left = tmpLeft * zoomRatio + xOrigin;
                right = tmpRight * zoomRatio + xOrigin;
                top = tmpTop * zoomRatio + yOrigin;
                bottom = tmpBottom * zoomRatio + yOrigin;
                if (right < 0 || left > screenSize.x || top > screenSize.y || bottom < 0)
                    b.visible = false;
                else b.visible = true;
            }
        }
        else if(orientation == 180) {

            float left, top, right, bottom;
            float tmpTop,tmpBottom,tmpRight,tmpLeft;
            float xOrigin = imageCenter.x - zoomRatio * imageSize.x / 2;
            float yOrigin = imageCenter.y - zoomRatio * imageSize.y / 2;
            for (int i = 0; i < blockList.size(); i++) {
                Block b = blockList.get(i);

                tmpTop = imageSize.y - b.bottom;
                tmpLeft = imageSize.x - b.right;
                tmpBottom = imageSize.y - b.top;
                tmpRight = imageSize.x - b.left;

                left = tmpLeft * zoomRatio + xOrigin;
                right = tmpRight * zoomRatio + xOrigin;
                top = tmpTop * zoomRatio + yOrigin;
                bottom = tmpBottom * zoomRatio + yOrigin;
                if (right < 0 || left > screenSize.x || top > screenSize.y || bottom < 0)
                    b.visible = false;
                else b.visible = true;
            }
        }
        else //orientation == 270
        {

            float left, top, right, bottom;
            float tmpTop,tmpBottom,tmpRight,tmpLeft;
            float xOrigin = imageCenter.x - zoomRatio * imageSize.x / 2;
            float yOrigin = imageCenter.y - zoomRatio * imageSize.y / 2;
            for (int i = 0; i < blockList.size(); i++) {
                Block b = blockList.get(i);

                tmpTop = imageSize.y - b.right;
                tmpLeft = b.top;
                tmpBottom = imageSize.y  - b.left;
                tmpRight = b.bottom;

                left = tmpLeft * zoomRatio + xOrigin;
                right = tmpRight * zoomRatio + xOrigin;
                top = tmpTop * zoomRatio + yOrigin;
                bottom = tmpBottom * zoomRatio + yOrigin;
                if (right < 0 || left > screenSize.x || top > screenSize.y || bottom < 0)
                    b.visible = false;
                else b.visible = true;
            }
        }
    }

    /**
     * 刷新block的visibility和inSampleSize,如果block在屏幕外，visible=false.
     * inSampleSize根据当前zoomRatio设置，以适应当前缩放倍率.
     */
    private void refreshBlocks() {
        setVisibilities();

        if(calculateInSampleSize(zoomRatio)!=inSampleSize&&
                calculateInSampleSize(zoomRatio)<=inSampleSizeMax) {
            inSampleSize = calculateInSampleSize(zoomRatio);
            for(ReLoadBlockTask t :reLoadList) {
                t.cancel(true);
            }
            reLoadList.clear();
            for(int i=0;i<blockList.size();i++) {
                if(blockList.get(i).visible) {
                    ReLoadBlockTask task = new ReLoadBlockTask(uri);
                    reLoadList.add(task);
                    task.execute(i);
                }
            }
        }
        else {
            for (ReLoadBlockTask t : reLoadList) {
                t.cancel(true);
            }
            reLoadList.clear();
            for(int i=0;i<blockList.size();i++) {
            if (blockList.get(i).visible &&
                    blockList.get(i).inSampleSize != inSampleSize) {
                ReLoadBlockTask task = new ReLoadBlockTask(uri);
                reLoadList.add(task);
                task.execute(i);
            }
        }
        }
    }

    //override FIELD
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //如果x方向长度大于屏幕x方向宽度，那么ViewPager就不会切换页面
        if(zoomRatio*imageSize.x<=screenSize.x)
            getParent().requestDisallowInterceptTouchEvent(false);
        else getParent().requestDisallowInterceptTouchEvent(true);
        return touchEventManager.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(blockList!=null&&isImageReady) {
            for (Block block : blockList) {
                bitmapMatrix.reset();
                bitmapMatrix.postTranslate(block.left,block.top);
                bitmapMatrix.postScale(block.inSampleSize,block.inSampleSize,block.left,block.top);
                bitmapMatrix.postConcat(mMatrix);
                if(block.visible&&block.bitmap!=null)
                    canvas.drawBitmap(
                            block.bitmap,
                            bitmapMatrix,
                            mPaint);
            }
        }
/* 中心点测试，暂时不用了
        Paint sPaint = new Paint();
        Paint iPaint = new Paint();
        iPaint.setColor(Color.RED);
        iPaint.setStyle(Paint.Style.FILL);
        sPaint.setColor(Color.GREEN);
        sPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(screenCenter.x,screenCenter.y,20,sPaint);
        canvas.drawCircle(imageCenter.x,imageCenter.y,20,iPaint);
       */
    }


    //class and interface FIELD

    /**
     * 定义小块的内容
     */
    private class Block {
        private Bitmap bitmap;
        private int left;
        private int top;
        private int right;
        private int bottom;
        private int inSampleSize;
        private boolean visible;

        private Block(Bitmap bitmap,int left,int top) {
            this.bitmap = bitmap;
            this.left = left;
            this.top = top;
        }
        private Block(int left,int top,int right,int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /**
     * 创建用于显示在view上的数据。因为硬件加速的限制，每一个块不能大于4096x4096
     * （部分硬件是2048X2048）。所以划分成很多个块，然后存储在LIST中，在绘制的时候遍历没一个块然后分别绘制
     * 这样就不会因为限制的问题显示不出来。数据内容包括每一块的bitmap，左边界，上边界。
     * 如果直接设置bitmap，可能无法获得orientation数据
     */
    private class LoadBitmapTask extends AsyncTask<Void,Void,Void> {
        private Uri uri = null;
        private Bitmap bitmap = null;

        private LoadBitmapTask(Uri uri) {
            this.uri = uri;
        }
        private LoadBitmapTask(Bitmap bm) {
            this.bitmap = bm;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if(bitmap==null&&uri!=null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                options.inPreferredConfig = BITMAP_CONFIG;
                options.inJustDecodeBounds = true;
                try {
                    BitmapFactory.decodeStream(activity.getContentResolver().openInputStream(uri),
                                null,
                                options);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                imageSize = new Point(options.outWidth,options.outHeight);
                imageCenter.set(imageSize.x/2,imageSize.y/2);
                getOrientation(uri);
                getRatios();
                //设置blocks
                int left = 0;
                int top = 0;
                while (top < imageSize.y) {
                    left = 0;
                    while (left < imageSize.x) {
                        int width = BLOCK_SIZE < (imageSize.x - left) ? BLOCK_SIZE : imageSize.x - left;
                        int height = BLOCK_SIZE < (imageSize.y - top) ? BLOCK_SIZE : imageSize.y - top;
                        block = new Block(left,top,left+width,top+height);
                        block.inSampleSize = calculateInSampleSize(ratioList.get(0));
                        if(imageSize.x>screenSize.x||imageSize.y>screenSize.y)
                            block.inSampleSize *= 2;
                        block.visible = true;
                        blockList.add(block);
                        left += BLOCK_SIZE;
                    }
                    top += BLOCK_SIZE;
                }
                inSampleSize = blockList.get(0).inSampleSize;
                inSampleSizeMax = blockList.get(0).inSampleSize;

                //开始decode bitmap
                options.inJustDecodeBounds = false;
                if(blockList.size()==1)
                {
                    try {
                        blockList.get(0).bitmap =
                                BitmapFactory.decodeStream(activity.getContentResolver().openInputStream(uri),
                                        null,
                                        options);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    try {
                        BitmapRegionDecoder regionDecoder = BitmapRegionDecoder.newInstance(
                                activity.getContentResolver().openInputStream(uri),
                                false
                        );
                        for (Block b : blockList) {
                            options.inSampleSize = b.inSampleSize;
                            b.bitmap = regionDecoder.decodeRegion(
                                    new Rect(b.left, b.top, b.right, b.bottom),
                                    options
                            );
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                postRotateFitScreen(orientation,orientation);
                return null;
            }
            if(bitmap!=null) {
                imageSize = new Point(bitmap.getWidth(),bitmap.getHeight());
                imageCenter.set(imageSize.x/2,imageSize.y/2);
                int left = 0;
                int top = 0;
                while (top < imageSize.y) {
                    left = 0;
                    while (left < imageSize.x) {
                        int width = BLOCK_SIZE < (imageSize.x - left) ? BLOCK_SIZE : imageSize.x - left;
                        int height = BLOCK_SIZE < (imageSize.y - top) ? BLOCK_SIZE : imageSize.y - top;
                        block = new Block(
                                Bitmap.createBitmap(bitmap,left,top,width,height),
                                left,
                                top
                        );
                        blockList.add(block);
                        left += BLOCK_SIZE;
                    }
                    top += BLOCK_SIZE;
                }
                postRotateFitScreen(orientation,orientation);
            }
            bitmap = null;

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            initImage();
        }
    }

    /**
     * 用于刷新block数据，在需要的时候
     */
    private class ReLoadBlockTask extends AsyncTask<Integer,Void,Integer> {
        private Uri uri;
        private Bitmap bitmap;
        private int size;

        public ReLoadBlockTask(Uri uri) {
            this.uri = uri;
        }

        @Override
        protected Integer doInBackground(final Integer... params) {
            Block b = blockList.get(params[0]);
            size = calculateInSampleSize(zoomRatio);
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = size;
                options.inPreferredConfig = BITMAP_CONFIG;
                BitmapRegionDecoder regionDecoder = BitmapRegionDecoder.newInstance(
                        activity.getContentResolver().openInputStream(uri),
                        false
                );

                bitmap = regionDecoder.decodeRegion(
                        new Rect(b.left,b.top,b.right,b.bottom),
                        options
                );

                //test
                /*
                post(new Runnable() {
                    @Override
                    public void run() {
                        toasts("reload "+params[0]);
                    }
                });
                */

            } catch (IOException e) {
                e.printStackTrace();
            }
            return params[0];
        }

        @Override
        protected void onPostExecute(Integer index) {
            blockList.get(index).bitmap = this.bitmap;
            blockList.get(index).inSampleSize = this.size;
            invalidate();
        }
    }
}
