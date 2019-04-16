package meh.floatingimage;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;

import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;

import android.net.Uri;
import android.os.CountDownTimer;
import android.os.IBinder;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;

import java.io.File;

import static java.lang.Math.abs;


public class FloatingImageService extends Service {
    private WindowManager mWindowManager;
    private Point screenSize = new Point();

    private View mFloatingView;
    private View mFloatingViewMax;
    private View mFloatingViewCfg;

    private final WindowManager.LayoutParams paramsMini = new WindowManager.LayoutParams();
    private final WindowManager.LayoutParams paramsMax = new WindowManager.LayoutParams();
    private final WindowManager.LayoutParams paramsCfg = new WindowManager.LayoutParams();

    private float movedDistance;

    private String PREFERENCE_NAME = "FLOATING_IMAGE_PREFERENCE";
    private SharedPreferences preferences;
    private int currentImageX;
    private int currentImageY;
    private String imagePath;
    private float currentAlpha;
    private int currentSize;
    private boolean isLocked;


    public FloatingImageService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        preferences = getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE);

        Notification notification = new Notification();
        startForeground(1, notification);

        initializeService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String endCommand = intent.getStringExtra("Command");
        if (endCommand != null){
            if ( endCommand.equals("end_service")){
                stopSelf();
            } else if (endCommand.equals("unlock_image")){
                paramsMini.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                mWindowManager.updateViewLayout(mFloatingView, paramsMini);
                isLocked = false;
            }
            return START_STICKY;
        }

        String newImagePath = intent.getStringExtra("ImagePath");

        if (newImagePath != null){
            imagePath = newImagePath;
        }

        Uri imageUri = Uri.parse(new File(imagePath).toString());
        ImageView img = mFloatingView.findViewById(R.id.image_small);
        ImageView imgMax = mFloatingViewMax.findViewById(R.id.image_max);

        img.setImageURI(imageUri);
        imgMax.setImageURI(imageUri);

        return START_STICKY;
    }

    private void initializeService(){
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.getDefaultDisplay().getSize(screenSize);

        mFloatingView = LayoutInflater.from(this).inflate(R.layout.floating_image, null);
        mFloatingView.setVisibility(View.VISIBLE);

        mFloatingViewMax = LayoutInflater.from(this).inflate(R.layout.floating_image_maximize, null);
        mFloatingViewMax.setVisibility(View.GONE);

        mFloatingViewCfg = LayoutInflater.from(this).inflate(R.layout.floating_image_config, null);
        mFloatingViewCfg.setVisibility(View.GONE);

        loadSetting();

        mFloatingView.setAlpha(currentAlpha);

        if (imagePath.length() > 0){
            Uri imageUri = Uri.parse(new File(imagePath).toString());
            ImageView img = mFloatingView.findViewById(R.id.image_small);
            ImageView imgMax = mFloatingViewMax.findViewById(R.id.image_max);

            img.setImageURI(imageUri);
            imgMax.setImageURI(imageUri);
        }

        handleMiniView();
        handleMaxView();
        handleCfgView();
    }

    private void handleMiniView(){
        //Prepare param for small view.
        paramsMini.width    = currentSize;
        paramsMini.height   = WindowManager.LayoutParams.WRAP_CONTENT;
        paramsMini.type     = WindowManager.LayoutParams.TYPE_PHONE;

        paramsMini.format   = PixelFormat.TRANSLUCENT;

        if (isLocked){
            paramsMini.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            paramsMini.flags    = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        //Specify the view position
        paramsMini.gravity = Gravity.TOP | Gravity.START;        //Initially view will be added to top-left corner
        paramsMini.x = currentImageX;
        paramsMini.y = currentImageY;

        //Add the view to the window
        mWindowManager.addView(mFloatingView, paramsMini);

        //Drag and move floating view using user's touch action.
        mFloatingView.findViewById(R.id.image_small).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            private float startTime;
            private float endTime;

            private CountDownTimer longHoldTimer = new CountDownTimer(400, 1000) {
                @Override
                public void onTick(long l) {
                }

                @Override
                public void onFinish() {
                    if (movedDistance < 20){
                        mFloatingViewCfg.setVisibility(View.VISIBLE);
                    }
                }
            };


            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        movedDistance = 0;

                        //remember the initial position.
                        initialX = paramsMini.x;
                        initialY = paramsMini.y;

                        //get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();

                        //Record time
                        startTime = event.getEventTime();

                        longHoldTimer.start();
                        return true;

                    case MotionEvent.ACTION_UP:
                        endTime = event.getEventTime();
                        longHoldTimer.cancel();

                        if (movedDistance < 20 && (endTime - startTime < 100)){
                            mFloatingView.setVisibility(View.GONE);
                            mFloatingViewMax.setVisibility(View.VISIBLE);
                            v.performClick();
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        //get the touch location
                        int Xdiff = (int) (event.getRawX() - initialTouchX);
                        int Ydiff = (int) (event.getRawY() - initialTouchY);

                        movedDistance = Math.abs(Xdiff) + Math.abs(Ydiff);

                        //Calculate the X and Y coordinates of the view.
                        currentImageX = Math.min(Math.max(initialX + Xdiff, 0), screenSize.x - currentSize);
                        currentImageY = Math.min(Math.max(initialY + Ydiff, 0), screenSize.y - currentSize);

                        paramsMini.x = currentImageX;
                        paramsMini.y = currentImageY;

                        //Update the layout with new X & Y coordinate
                        mWindowManager.updateViewLayout(mFloatingView, paramsMini);
                        return true;
                }
                return false;
            }
        });
    }

    private void handleMaxView(){
        paramsMax.width    = WindowManager.LayoutParams.MATCH_PARENT;
        paramsMax.height   = WindowManager.LayoutParams.WRAP_CONTENT;
        paramsMax.type     = WindowManager.LayoutParams.TYPE_PHONE;
        paramsMax.flags    = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        paramsMax.format   = PixelFormat.TRANSLUCENT;

        //Add the view to the window
        mWindowManager.addView(mFloatingViewMax, paramsMax);

        ImageView imageMaximize = mFloatingViewMax.findViewById(R.id.image_max);
        imageMaximize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mFloatingView.setVisibility(View.VISIBLE);
                mFloatingViewMax.setVisibility(View.GONE);
            }
        });
    }

    private void handleCfgView(){
        paramsCfg.width    = WindowManager.LayoutParams.MATCH_PARENT;
        paramsCfg.height   = screenSize.y/4;
        paramsCfg.type     = WindowManager.LayoutParams.TYPE_PHONE;
        paramsCfg.flags    = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        paramsCfg.format   = PixelFormat.TRANSLUCENT;
        paramsCfg.gravity = Gravity.BOTTOM;

        //Add the view to the window
        mWindowManager.addView(mFloatingViewCfg, paramsCfg);


        //Get cfg GUI element
        ImageButton cfgChooseBtn = mFloatingViewCfg.findViewById(R.id.cfg_choose_btn);
        ImageButton cfgLockBtn = mFloatingViewCfg.findViewById(R.id.cfg_lock_btn);
        ImageButton cfgCloseBtn = mFloatingViewCfg.findViewById(R.id.cfg_close_btn);

        final SeekBar cfgSizeBar = mFloatingViewCfg.findViewById(R.id.cfg_size_bar);
        SeekBar cfgAlphaBar = mFloatingViewCfg.findViewById(R.id.cfg_alpha_bar);

        //Adjust bar to correspond with setting
        cfgSizeBar.setProgress(currentSize*cfgSizeBar.getMax()/screenSize.x);
        cfgAlphaBar.setProgress((int) (currentAlpha*cfgAlphaBar.getMax()));

        //Handle GUI element
        cfgChooseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Uri imageUri = Uri.parse(new File(imagePath).toString());

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(imageUri, "image/*");
                startActivity(intent);

                mFloatingViewCfg.setVisibility(View.GONE);
            }
        });

        cfgChooseBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Intent intent = new Intent(FloatingImageService.this, MainActivity.class);
                intent.putExtra("Command", "pick_image");
                startActivity(intent);
                return true;
            }
        });


        cfgLockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                paramsMini.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                mWindowManager.updateViewLayout(mFloatingView, paramsMini);
                isLocked = true;
            }
        });


        cfgLockBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                paramsMini.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                mWindowManager.updateViewLayout(mFloatingView, paramsMini);
                isLocked = false;
                return true;
            }
        });


        cfgCloseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mFloatingViewCfg.setVisibility(View.GONE);
            }
        });

        cfgCloseBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                stopSelf();
                return true;
            }
        });

        cfgAlphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                float alpha = (float)i/seekBar.getMax();
                mFloatingView.setAlpha(alpha);

                currentAlpha = alpha;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        cfgSizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                ViewGroup.LayoutParams params = mFloatingView.getLayoutParams();
                params.width = screenSize.x * i/cfgSizeBar.getMax();
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;

                mWindowManager.updateViewLayout(mFloatingView, params);

                currentSize = params.width;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }


    private void loadSetting(){
        currentImageX = preferences.getInt("x", 400);
        currentImageY = preferences.getInt("y", 100);
        imagePath = preferences.getString("imageUri", "");
        currentAlpha = preferences.getFloat("alpha", 1);
        currentSize = preferences.getInt("size", screenSize.x/3);
        isLocked = preferences.getBoolean("isLocked", false);
    }


    private void saveSetting(){
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("x", currentImageX);
        editor.putInt("y", currentImageY);
        editor.putString("imageUri", imagePath);
        editor.putFloat("alpha", currentAlpha);
        editor.putInt("size", currentSize);
        editor.putBoolean("isLocked", isLocked);
        editor.apply();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        saveSetting();

        if (mFloatingView != null) mWindowManager.removeView(mFloatingView);
        if (mFloatingViewMax != null) mWindowManager.removeView(mFloatingViewMax);
        if (mFloatingViewCfg != null) mWindowManager.removeView(mFloatingViewCfg);
    }
}