package com.alvin.capture.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.alvin.capture.R;
import com.alvin.capture.camera2.AutoFitTextureView;
import com.alvin.capture.camera2.CaptureLayout2;
import com.alvin.capture.camera2.CaptureListener2;
import com.alvin.capture.camera2.CustomVideoView;
import com.alvin.capture.camera2.util.BitmapDecoder;
import com.alvin.capture.camera2.util.BitmapSize;
import com.alvin.capture.camera2.util.Camera2Config;
import com.alvin.capture.camera2.util.Camera2Util;
import com.alvin.capture.camera2.util.CompareSizeByArea;
import com.alvin.capture.jcamera.listener.ClickListener;
import com.alvin.capture.jcamera.listener.TypeListener;
import com.alvin.capture.jcamera.util.ContentValue;
import com.alvin.capture.jcamera.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class VideoCameraActivity extends Activity {

    private static final String TAG = "VideoCameraActivity";
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private AutoFitTextureView mTextureView;
    private CaptureLayout2 mCaptureLayout;

    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private boolean mIsRecordingVideo= false;//是否正在录制视频
    private boolean isStop = false;//是否停止过了MediaRecorder
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    //摄像头相关
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest mCaptureRequest;
    private CaptureRequest.Builder mPreviewBuilder;
    private ImageReader mImageReader;//捕获图片
    private CameraCharacteristics characteristics;

    private String picSavePath;//图片存储地址
    public int widthPixels;
    public int heightPixels;
    private int width;//TextureView的宽
    private int height;//TextureView的高
    private String mCameraId;//后置摄像头ID
    private String mCameraIdFront;//前置摄像头ID
    private boolean isCameraFront = false;//当前是否是前置摄像头

    private MediaRecorder mMediaRecorder;

    private Size mPreviewSize;//预览的Size
    private Size mCaptureSize;//拍照Size
    private Size mVideoSize;//视频size

    private String mNextVideoAbsolutePath;//视频路径

    private PowerManager.WakeLock mWakeLock;
    //手指按下的点为(x1, y1)手指离开屏幕的点为(x2, y2)
    float finger_spacing;
    int zoom_level = 0;
    private Rect zoom;

    private RelativeLayout rl_preview;
    private ImageView iv_preview,iv_switch;
    private CustomVideoView video_preview;
    private Intent resultIntent;

    //旋转角度
    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_camera);
        init();
        initView();
        initListener();
    }
    private void init(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);  //设置全屏
        }
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        widthPixels = dm.widthPixels;
        heightPixels = dm.heightPixels;
    }

    private void initView(){
        mTextureView = findViewById(R.id.texture);
        mCaptureLayout = findViewById(R.id.capture_layout);
        int maxRecordTime = 10 * 1000;
        mCaptureLayout.setDuration(maxRecordTime);
        mCaptureLayout.setIconSrc(0, 0);

        rl_preview = findViewById(R.id.rl_preview);
        iv_preview = findViewById(R.id.iv_preview);//展示拍照
        iv_switch = findViewById(R.id.iv_switch);//切换摄像头
        video_preview = findViewById(R.id.video_preview);
    }

    private void initListener(){
        //拍照 录像
        mCaptureLayout.setCaptureLisenter(new CaptureListener2() {
            //拍照
            @Override
            public void takePictures() {
                mIsRecordingVideo = false;
                //判断是否需要拍照功能
                iv_switch.setVisibility(View.GONE);
                capture();
            }

            //开始录像
            @Override
            public void recordStart() {
                iv_switch.setVisibility(View.GONE);
                mIsRecordingVideo = true;
                startRecordingVideo();
                mMediaRecorder.start();
                isStop = false;
            }

            //录像太短
            @Override
            public void recordShort(final long time) {
                iv_switch.setVisibility(View.GONE);
                mIsRecordingVideo = false;
                Toast.makeText(VideoCameraActivity.this,"录制时间太短，请重新录制", Toast.LENGTH_SHORT).show();
                FileUtil.delFile(mNextVideoAbsolutePath);
                mNextVideoAbsolutePath = null;
                invokeResetDelay();
            }

            //录像结束
            @Override
            public void recordEnd(long time) {
                iv_switch.setVisibility(View.GONE);
                mIsRecordingVideo = false;
                if (!isStop) {
                    stopRecordingVideo(false);
                }
                mCaptureLayout.startAlphaAnimation();
                mCaptureLayout.startTypeBtnAnimator();
            }

            //缩放
            @Override
            public void recordZoom(Rect zoom) {
                mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                try {
                    mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void recordError() {

            }
        });
        //确认 取消
        mCaptureLayout.setTypeLisenter(new TypeListener() {
            @Override
            public void cancel() {
                iv_switch.setVisibility(View.VISIBLE);
                rl_preview.setVisibility(View.INVISIBLE);
                iv_preview.setVisibility(View.GONE);
                video_preview.stopPlayback();
                //重置录制界面
                showResetCameraLayout();
            }

            @Override
            public void confirm() {
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
        //返回监听事件
        mCaptureLayout.setLeftClickListener(new ClickListener() {
            @Override
            public void onClick() {
                finish();
            }
        });
        //拍摄完成后当前界面预览视频
        video_preview.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();//循环播放
                mp.setLooping(true);
            }
        });
        video_preview.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG,"MediaPlayer--play error");
                return false;
            }
        });
        //切换摄像头
        iv_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });
        //拍照时支持双指缩放
        mTextureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                //两指缩放
                changeZoom(event);
                return true;
            }
        });
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int w, int h) {
            //当SurefaceTexture可用的时候，设置相机参数并打开相机
            width = w;
            height = h;
            setupCamera(w, h);
            openCamera(mCameraId);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,int w, int h) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        if (mWakeLock == null) {
            //获取唤醒锁,保持屏幕常亮
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
            mWakeLock.acquire();
        }
        //每次开启预览默认是后置摄像头
        isCameraFront = false;
    }

    @Override
    public void onPause() {
        if(mIsRecordingVideo){
            stopRecordingVideo(true);
        }
        closeCamera();
        stopBackgroundThread();
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

	/*private void closePreviewSession() {
		if (mPreviewSession != null) {
			mPreviewSession.close();
			mPreviewSession = null;
		}
	}*/

    /**
     * **********************************************切换摄像头**************************************
     */
    public void switchCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (isCameraFront) {
            isCameraFront = false;
            setupCamera(width, height);
            openCamera(mCameraId);
        } else {
            isCameraFront = true;
            setupCamera(width, height);
            openCamera(mCameraIdFront);
        }
    }

    /**
     * ***************************** 重置界面和摄像头 *****************************************
     */
    public void invokeResetDelay(){
        mCaptureLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                showResetCameraLayout();
            }
        },500);
    }
    public void showResetCameraLayout() {
        resetCamera();
        rl_preview.setVisibility(View.INVISIBLE);
        video_preview.stopPlayback();
    }
    //重新配置打开相机
    public void resetCamera() {
        if (TextUtils.isEmpty(mCameraId)) {
            return;
        }
        closeCamera();
        mCaptureLayout.resetCaptureLayout();
        setupCamera(width, height);
        if(isCameraFront){
            openCamera(mCameraIdFront);
        }else{
            openCamera(mCameraId);
        }
    }
    /**
     * ******************************** 配置摄像头参数 *********************************
     * Tries to open a {@link CameraDevice}. The result is listened by mStateCallback`.
     */
    private void setupCamera(int width, int height) {

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //0表示后置摄像头,1表示前置摄像头
            mCameraId = manager.getCameraIdList()[0];
            mCameraIdFront = manager.getCameraIdList()[1];

            //前置摄像头和后置摄像头的参数属性不同，所以这里要做下判断
            if (isCameraFront) {
                characteristics = manager.getCameraCharacteristics(mCameraIdFront);
            } else {
                characteristics = manager.getCameraCharacteristics(mCameraId);
            }
            mCaptureLayout.setCharacteristics(characteristics);

            // Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
            //noinspection ConstantConditions
            int mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            boolean swappedDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
                default:
                    Log.e(TAG, "Display rotation is invalid: " + displayRotation);
            }

            Point displaySize = new Point();
            getWindowManager().getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;
            if (swappedDimensions) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }
            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }
            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            // Choose the sizes for camera preview and video recording
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            //Integer mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class),width, height);

            //获取相机支持的最大拍照尺寸
            mCaptureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizeByArea());
            mPreviewSize = Camera2Util.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, mCaptureSize);

            configureTransform(width, height);

            //此ImageReader用于拍照所需
            setupImageReader();

            mMediaRecorder = new MediaRecorder();
        } catch (CameraAccessException e) {
            //UIUtil.toastByText("Cannot access the camera.", Toast.LENGTH_SHORT);
            Toast.makeText(this,"无法使用摄像头.", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            //UIUtil.toastByText("This device doesn't support Camera2 API.", Toast.LENGTH_SHORT);
            Toast.makeText(this,"设备不支持 Camera2 API.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    /**
     * *********************************配置ImageReader,用于图片处理****************************
     */
    @SuppressLint("HandlerLeak")
    private void setupImageReader() {
        //2代表ImageReader中最多可以获取两帧图像流
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(), ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image mImage = reader.acquireNextImage();
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                FileUtil.createSavePath(ContentValue.IMAGE_PATH);//判断有没有这个文件夹，没有的话需要创建
                picSavePath = ContentValue.IMAGE_PATH + "IMG_" + System.currentTimeMillis() + ".jpg";
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(picSavePath);
                    fos.write(data, 0, data.length);//保存图片

                    Message msg = new Message();
                    msg.what = 0;
                    msg.obj = picSavePath;
                    mBackgroundHandler.sendMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                mImage.close();
            }
        }, mBackgroundHandler);

        mBackgroundHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 0:
                        //这里拍照保存完成，可以进行相关的操作
                        rl_preview.setVisibility(View.VISIBLE);
                        //展示图片
                        try {
                            File captureImage = new File(picSavePath);
                            Bitmap bitmap = BitmapDecoder.decodeSampledBitmapFromFile(captureImage, new BitmapSize(widthPixels, heightPixels), Bitmap.Config.RGB_565);
                            iv_preview.setImageBitmap(bitmap);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        iv_preview.setVisibility(View.VISIBLE);
                        video_preview.setVisibility(View.GONE);
                        //设置发送图片参数
                        resultIntent = new Intent();
                        resultIntent.putExtra("isPhoto",true);
                        resultIntent.putExtra("imageUrl",picSavePath);

                        mCaptureLayout.startAlphaAnimation();
                        mCaptureLayout.startTypeBtnAnimator();

                        Log.d(TAG,"保存图片成功");
                        break;
                }
            }
        };
    }
    /**
     * ******************************openCamera(打开Camera)*****************************************
     */
    private void openCamera(String CameraId) {
        //获取摄像头的管理者CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //检查权限
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //打开相机，第一个参数指示打开哪个摄像头，第二个参数stateCallback为相机的状态回调接口，第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
            manager.openCamera(CameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    //摄像头状态回调
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    /**
     * ************************************* 启动预览. **********************************
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if(texture==null){
            return;
        }
        try {
            //closePreviewSession();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface,mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    //创建捕获请求
                    mCaptureRequest = mPreviewBuilder.build();
                    mPreviewSession = cameraCaptureSession;
                    //不停的发送获取图像请求，完成连续预览
                    try {
                        mPreviewSession.setRepeatingRequest(mCaptureRequest, null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(VideoCameraActivity.this,"Failed",Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * ***************************** 关闭摄像头 ******************************
     */
    private void closeCamera() {
        try {
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ********************************************拍照*********************************************
     */
    private void capture() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            CaptureRequest.Builder mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //获取屏幕方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            //isCameraFront是自定义的一个boolean值，用来判断是不是前置摄像头，是的话需要旋转180°，不然拍出来的照片会歪了
            if (isCameraFront) {
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, DEFAULT_ORIENTATIONS.get(Surface.ROTATION_180));
            } else {
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, DEFAULT_ORIENTATIONS.get(rotation));
            }

            //锁定焦点
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

            //判断预览的时候是否两指缩放过,是的话需要保持当前的缩放比例
            mCaptureBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    //拍完照unLockFocus
                    unLockFocus();
                }
            };
            mPreviewSession.stopRepeating();
            //咔擦拍照
            mPreviewSession.capture(mCaptureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void unLockFocus() {
        try {
            // 构建失能AF的请求
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //闪光灯重置为未开启状态
            mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            //继续开启预览
            mPreviewSession.setRepeatingRequest(mCaptureRequest, null, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ***************************** 录像 部分 START *******************************************
     */
    //开始录像
    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            Toast.makeText(this,"录制失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        try {
            //closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface mRecorderSurface = mMediaRecorder.getSurface();
            surfaces.add(mRecorderSurface);
            mPreviewBuilder.addTarget(mRecorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCaptureRequest = mPreviewBuilder.build();
                    mPreviewSession = cameraCaptureSession;
                    try {
                        mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(VideoCameraActivity.this,"Failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //结束录像
    private void stopRecordingVideo(boolean shortTime) {
        mIsRecordingVideo = false;
        try {
            if(mPreviewSession!=null){
                mPreviewSession.stopRepeating();
                mPreviewSession.abortCaptures();
            }
            mBackgroundHandler.removeCallbacksAndMessages(null);
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            mMediaRecorder.setPreviewDisplay(null);
            mMediaRecorder.stop();
            // Stop recording
            mMediaRecorder.reset();
            isStop = true;

        } catch (Exception e) {
            e.printStackTrace();
        }
        if(!shortTime) {
            Log.d(TAG, "录制成功");
            resultIntent = new Intent();
            resultIntent.putExtra("videoPath", mNextVideoAbsolutePath);
            boolean isOK= FileUtil.getVideoWH(mNextVideoAbsolutePath,resultIntent);
            if(isOK){
                video_preview.setVisibility(View.VISIBLE);
                iv_preview.setVisibility(View.GONE);
                video_preview.setVideoPath(mNextVideoAbsolutePath);
                video_preview.requestFocus();
                video_preview.start();
                rl_preview.setVisibility(View.VISIBLE);
            }else{
                Toast.makeText(this,"录制失败，需要重启手机才能进行录制", Toast.LENGTH_SHORT).show();
                invokeResetDelay();
            }
        }else {//录制时间过短，变成拍照
            Toast.makeText(this,"录制时间太短，请重新录制", Toast.LENGTH_SHORT).show();
            FileUtil.delFile(mNextVideoAbsolutePath);
            mNextVideoAbsolutePath = null;
            invokeResetDelay();
        }
    }
    /**
     * 录像配置
     * @throws IOException
     */
    private void setUpMediaRecorder() throws IOException {
        mMediaRecorder.reset();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath();
        }

        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncodingBitRate(1200*1280);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);

        //判断是不是前置摄像头,是的话需要旋转对应的角度
        if (isCameraFront) {
            mMediaRecorder.setOrientationHint(270);
        } else {
            mMediaRecorder.setOrientationHint(90);
        }

        mMediaRecorder.prepare();
    }
    /**
     * ***************************** 录像 部分 END *******************************************
     */

	/*private void updatePreview() {
		if (null == mCameraDevice) {
			return;
		}
		try {
			setUpCaptureRequestBuilder(mPreviewBuilder);
			if(mBackgroundHandler!=null&&!isClose) {
				HandlerThread thread = new HandlerThread("CameraPreview");
				thread.start();
				mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
			}
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
		builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
	}*/

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            if (mBackgroundHandler != null) {
                mBackgroundHandler.removeCallbacksAndMessages(null);
            }
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static Size chooseVideoSize(Size[] choices, int width, int height) {
        for (Size size : choices) {
            float ft=(float)size.getWidth()/(float)size.getHeight();
            if (size.getWidth() <= 1300 && size.getWidth() <= height && ft > 1.5 && ft < 1.9) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * 预览界面的一些画面角度配置
     * @param viewWidth 预览界面宽度
     * @param viewHeight 预览界面高度
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize ) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * *********************************** 两指缩放 *********************************
     */
    public void changeZoom(MotionEvent event) {
        try {
            //活动区域宽度和作物区域宽度之比和活动区域高度和作物区域高度之比的最大比率
            float maxZoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)) * 10;
            Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            int action = event.getAction();
            float current_finger_spacing;
            //判断当前屏幕的手指数
            if (event.getPointerCount() > 1) {
                //计算两个触摸点的距离
                current_finger_spacing = getFingerSpacing(event);

                if (finger_spacing != 0) {
                    if (current_finger_spacing > finger_spacing && maxZoom > zoom_level) {
                        zoom_level++;

                    } else if (current_finger_spacing < finger_spacing && zoom_level > 1) {
                        zoom_level--;
                    }

                    int minW = (int) (m.width() / maxZoom);
                    int minH = (int) (m.height() / maxZoom);
                    int difW = m.width() - minW;
                    int difH = m.height() - minH;
                    int cropW = difW / 100 * (int) zoom_level;
                    int cropH = difH / 100 * (int) zoom_level;
                    cropW -= cropW & 3;
                    cropH -= cropH & 3;
                    zoom = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);
                    mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                }
                finger_spacing = current_finger_spacing;
            } else {
                if (action == MotionEvent.ACTION_UP) {
                    //single touch logic,可做点击聚焦操作
                }
            }
            try {
                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                            }
                        },
                        null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            throw new RuntimeException("can not access camera.", e);
        }
    }

    //计算两个触摸点的距离
    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private String getVideoFilePath() {
        return FileUtil.createFilePath(ContentValue.VIDEO_PATH, null, Long.toString(System.currentTimeMillis()));
    }

}
