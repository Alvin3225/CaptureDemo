package com.alvin.capture.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;

import com.alvin.capture.R;
import com.alvin.capture.jcamera.JCameraView;
import com.alvin.capture.jcamera.listener.ClickListener;
import com.alvin.capture.jcamera.listener.ErrorListener;
import com.alvin.capture.jcamera.listener.JCameraListener;
import com.alvin.capture.jcamera.util.ContentValue;
import com.alvin.capture.jcamera.util.FileUtil;

/**
 * android5.0以下的使用
 如果是聊天界面跳转到此界面，遵循下面两个要求
 a)拍照：
 1、拍完直接发送：
 wifi：发送原图
 否则：发送标清图
 b)短视频：
 拍完直接发送。
 */
public class CaptureImageVideoActivity extends Activity {

    private JCameraView jCameraView;
    private String firstF="";//首帧图
    private String videoUrl="";//视频路径
    private String imageUrl="";//拍路径
    private boolean isPhoto = false;//是否是照片
    private int[] videoSize = new int[2];//视频尺寸
    private PowerManager.WakeLock mWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_image_video);
        initView();
        addListener();
    }
    private void initView(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);  //设置全屏
        }

        jCameraView = findViewById(R.id.jCameraView);
        jCameraView.setSaveVideoPath(ContentValue.VIDEO_PATH);
        jCameraView.setFeatures(JCameraView.BUTTON_STATE_BOTH);
        jCameraView.setMediaQuality(JCameraView.MEDIA_QUALITY_MIDDLE);
    }
    private void addListener(){
        //JCameraView监听
        jCameraView.setJCameraLisenter(new JCameraListener() {
            @Override
            public void captureSuccess(Bitmap bitmap) {//拍照成功
                isPhoto = true;
                //获取图片bitmap
                Log.i("ID", "bitmap = " + bitmap.getWidth());
                imageUrl = FileUtil.saveBitmap(bitmap);
            }

            @Override
            public void recordSuccess(String url, Bitmap firstFrame,int[] vSize) {
                isPhoto = false;
                firstF = FileUtil.saveBitmap(firstFrame);
                videoUrl = url;
                videoSize = vSize;
            }
        });
        jCameraView.setErrorLisenter(new ErrorListener() {
            @Override
            public void onError() {
                //错误监听
                Log.i("ID", "camera error");
            }

            @Override
            public void AudioPermissionError() {
                Log.i("ID", "AudioPermissionError");
            }
        });
        jCameraView.setLeftClickListener(new ClickListener() {
            @Override
            public void onClick() {
                finish();
            }
        });
        jCameraView.setConfirmClickListener(new ClickListener() {
            @Override
            public void onClick() {
                Intent intent = new Intent();
                intent.putExtra("isPhoto",isPhoto);
                if(isPhoto){
                    intent.putExtra("imageUrl", imageUrl);
                }else{
                    intent.putExtra("videoPath",videoUrl);
                    intent.putExtra("firstFrame", firstF);
                    intent.putExtra("videoWidth", videoSize[0]);
                    intent.putExtra("videoHeight", videoSize[1]);
                    intent.putExtra("totalTime", jCameraView.recordTime/1000);
                }
                setResult(RESULT_OK,intent);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(jCameraView.getScreenCnt()==0 || !jCameraView.isCapEndPreview()){
            jCameraView.onResume();
        }else if(jCameraView.getScreenCnt()>0){
            jCameraView.onScreenOn();
        }
        if (mWakeLock == null) {
            //获取唤醒锁,保持屏幕常亮
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "CaptureImageVideoActivity");
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(jCameraView.isRecording()){
            jCameraView.onRecordingScreenOff();
        }else if(jCameraView.isCapEndPreview()){
            jCameraView.onCaptureScreenOff();
        }else{
            jCameraView.onPause();
        }
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        jCameraView.onDestroy();
    }
}
