package meh.floatingimage;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import static java.lang.Math.max;
import static java.lang.Math.min;


public class ExtendedImageView extends AppCompatImageView {
    private Matrix mTransformMatrix = new Matrix();
    private Matrix mTemporaryMatrix = new Matrix();

    private Drawable mCurrentDrawable;

    private RectF mCurrentDrawableRect = new RectF();
    private RectF mTemporaryDrawableRect = new RectF();
    private RectF mViewRect = new RectF();

    private int mCurrentPointerCount = 0;
    private int mInitTouchId = 0;
    private float mInitTouchX = 0;
    private float mInitTouchY = 0;
    private boolean isScaling = false;
    private boolean isMoveToScale = false;


    //Custom listener
    private OnDoubleTapListener onDoubleTapListener;
    public interface OnDoubleTapListener {
        boolean onDoubleTap();
    }

    public void setOnDoubleTapListener(OnDoubleTapListener listener) {
        onDoubleTapListener = listener;
    }


    //Constructors
    public ExtendedImageView(Context context) {
        super(context);
    }

    public ExtendedImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtendedImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    //Gesture detector
    private ScaleGestureDetector mScaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.OnScaleGestureListener() {
        float initialFocusX;
        float initialFocusY;
        Matrix originalMatrix = new Matrix();

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            initialFocusX = detector.getFocusX();
            initialFocusY = detector.getFocusY();
            originalMatrix.set(mTransformMatrix);

            isScaling = true;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {

        }


        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();

            mTransformMatrix.set(originalMatrix);
            mTransformMatrix.postScale(scale, scale, initialFocusX, initialFocusY);

            applyMatrixTransform();
            return false;
        }
    });

    private GestureDetector mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener(){
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            super.onDoubleTap(e);
            return onDoubleTapListener.onDoubleTap();
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            super.onSingleTapConfirmed(e);
            applyQuickScaleTransform();
            return performClick();
        }

        @Override
        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);
            performLongClick();
        }
    });

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);

        //Reinitialize initial touch point when a pointer is gone
        if (mCurrentPointerCount == 2 && event.getPointerCount() == 1){
            setInitTouchPoint(event);
        }
        mCurrentPointerCount = event.getPointerCount();


        if (isScaling && !mScaleDetector.isInProgress() && mCurrentPointerCount == 1){
            setInitTouchPoint(event);
            isScaling = false;
            isMoveToScale = true;
        }

        if (isScaling || mScaleDetector.isInProgress()) {
            return true;
        }
        else{
            //Drag image
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    setInitTouchPoint(event);
                    mTemporaryDrawableRect.set(mCurrentDrawableRect);
                    return true;

                case MotionEvent.ACTION_UP:
                    applyCorrection();
                    isMoveToScale = false;
                    return true;


                case MotionEvent.ACTION_MOVE:
                    //On the pointer that is used in InitTouch is considered for move
                    if (getPointerId(event) == mInitTouchId){
                        mTransformMatrix.set(mTemporaryMatrix);

                        float XDiff = event.getX(0) - mInitTouchX;
                        float YDiff = event.getY(0) - mInitTouchY;

                        if (!isMoveToScale) {
                            //Restrict drag to bound if it did not directly follow a scaling
                            float dx_max = max(mViewRect.left - mTemporaryDrawableRect.left, 0);
                            float dx_min = min(mViewRect.right - mTemporaryDrawableRect.right, 0);
                            float dy_max = max(mViewRect.top - mTemporaryDrawableRect.top, 0);
                            float dy_min = min(mViewRect.bottom - mTemporaryDrawableRect.bottom, 0);

                            XDiff = clamp(XDiff, dx_min, dx_max);
                            YDiff = clamp(YDiff, dy_min, dy_max);
                        }

                        mTransformMatrix.postTranslate(XDiff, YDiff);
                        applyMatrixTransform();
                    }
                    return true;
            }
        }
        return false;
    }


    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);

        if (drawable != null){
            mCurrentDrawable = drawable;
            applyDefaultTransform();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mViewRect = new RectF(getLeft(), getTop(), getRight(), getBottom());

        if (mCurrentDrawable != null){
            applyDefaultTransform();
        }
    }


    //Macro operations
    private void applyMatrixTransform(){
        setImageMatrix(mTransformMatrix);
        mTransformMatrix.mapRect(mCurrentDrawableRect, getOriginalDrawableRect());
    }

    //TODO: Animation for correction.
    private void applyCorrection(){
        //Get offset from image's edge to view edge, only take value that is relevant.
        float offset_left    = min(mViewRect.left - mCurrentDrawableRect.left, 0);
        float offset_top     = min(mViewRect.top - mCurrentDrawableRect.top, 0);
        float offset_right   = max(mViewRect.right - mCurrentDrawableRect.right, 0);
        float offset_bot     = max(mViewRect.bottom - mCurrentDrawableRect.bottom, 0);

        Matrix correctionMatrix = new Matrix();

        //Process width, x axis
        if (mViewRect.width() > mCurrentDrawableRect.width()){
            //Image width is smaller than view, need scale and center.
            float scale = mViewRect.width() / mCurrentDrawableRect.width();
            float dist = mViewRect.centerX() - mCurrentDrawableRect.centerX();
            correctionMatrix.postScale(scale, scale, mCurrentDrawableRect.centerX(), mCurrentDrawableRect.centerY());
            correctionMatrix.postTranslate(dist, 0);
        } else{
            // No need for scaling. Translate image to cover any empty view space.
            // If image's width is larger, both offsets cannot be non-zero at the same time.
            float dist = offset_left + offset_right;
            correctionMatrix.postTranslate(dist, 0);
        }


        //Process height, y axis
        if (mViewRect.height() > mCurrentDrawableRect.height()){
            //Image height is smaller, just need to translate to center of screen.
            float dist = mViewRect.centerY() - mCurrentDrawableRect.centerY();
            correctionMatrix.postTranslate(0, dist);
        }
        else{
            //Image height is larger, translate to fill empty space
            // If image's height is larger, both offsets cannot be non-zero at the same time.
            float dist = offset_top + offset_bot;
            correctionMatrix.postTranslate(0, dist);
        }


        //Apply final correction matrix
        mTransformMatrix.postConcat(correctionMatrix);
        applyMatrixTransform();
    }


    private void applyDefaultTransform(){
        mTransformMatrix.setRectToRect(getOriginalDrawableRect(), mViewRect, Matrix.ScaleToFit.CENTER);
        applyMatrixTransform();
    }


    private void applyQuickScaleTransform(){
        if (mCurrentDrawableRect.height() >= mViewRect.height()){
            applyDefaultTransform();
        } else{
            float scale = mViewRect.height() / mCurrentDrawableRect.height();
            RectF intersect = new RectF();
            intersect.setIntersect(mViewRect, mCurrentDrawableRect);
            mTransformMatrix.postScale(scale, scale, intersect.centerX(), intersect.centerY());
            applyMatrixTransform();
        }
    }


    private void setInitTouchPoint(MotionEvent event){
        mInitTouchId = getPointerId(event);
        mInitTouchX = event.getX();
        mInitTouchY = event.getY();
        mTemporaryMatrix.set(mTransformMatrix);
    }


    //Convenience
    private RectF getOriginalDrawableRect(){
        return new RectF(mCurrentDrawable.getBounds());
    }

    private int getPointerId(MotionEvent event){
        int index = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        return event.getPointerId(index);
    }

    private float getRectArea(RectF r){
        return r.width() * r.height();
    }

    private float clamp(float value, float min_value, float max_value){
        return max(min(value, max_value), min_value);
    }

    //Correctly handling click
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
