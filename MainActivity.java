package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.util.CameraUtil;
import com.example.myapplication.util.FileUtil;
import com.example.myapplication.util.RecorderStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.List;


public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
    Socket socket;
    private static final String TAG = "MainActivity";
    //视图控件
    private TextureView mTextureV;
    private SurfaceTexture mSurfaceTexture;
    private Chronometer chronometer;

    //存储文件
    private Camera mCamera;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int mRotationDegree;

    //录制相关参数
    private MediaRecorder mMediaRecorder;
    private int mFps;//帧率
    private RecorderStatus mStatus = RecorderStatus.RELEASED;//录制状态

    //录制出错的回调
    private MediaRecorder.OnErrorListener onErrorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            try {
                if (mMediaRecorder != null) {
                    mMediaRecorder.reset();
                }
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        hideStatusBarAndNavBar(this);
        FileUtil.init(this);
        initView();
    }

    /**
     * 隐藏导航栏和状态栏
     */
    public static void hideStatusBarAndNavBar(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }

    private void initView() {
        mTextureV = findViewById(R.id.surface);
        mTextureV.setSurfaceTextureListener(this);
        chronometer = findViewById(R.id.record_time);
        chronometer.setFormat("%s");
    }


    /**
     * 开始录制和停止录制
     *
     * @param v
     */
    public void control(View v) {
        if (mStatus == RecorderStatus.RECORDING) {
            stopRecord();
            sendmp4();

        } else {

            startRecord();
        }
    }

    /**
     * 开始录制
     */
    private void sendmp4(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    socket = new Socket("192.168.137.1", 8000);

                    File f= new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +"aaamedia"+"/"+"zxq.mp4");
                    long size = f.length();
                    String size_s = String.valueOf(size);
                    size_s = size_s + "+";

                    OutputStream out = socket.getOutputStream();
                    out.write(size_s.getBytes("UTF-8"));
//                    out.close();

//                    OutputStream outStream = null;
//                    outStream = socket.getOutputStream();

                    RandomAccessFile fileOutStream = new RandomAccessFile(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +"aaamedia"+"/"+"zxq.mp4", "r");
                    fileOutStream.seek(0);
                    byte[] buffer = new byte[1024];
                    int len = -1;
                    while (((len = fileOutStream.read(buffer)) != -1)) {
                        out.write(buffer, 0, len);   // 这里进行字节流的传输
                    }
                    fileOutStream.close();
                    out.close();

                    socket.close();
                } catch (Exception e) {

                    // TODO: handle exception

                } finally {

                    //关闭Socket
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (Exception e) {
                    }
                }
            }




        }).start();
    }

    private void startRecord() {
        initCamera();
        mCamera.unlock();
        initMediaRecorder();
        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
        mStatus = RecorderStatus.RECORDING;
    }

    /**
     * 停止录制
     */
    private void stopRecord() {
        releaseMediaRecorder();
        releaseCamera();

    }

    @Override
    public void onSurfaceTextureAvailable(final SurfaceTexture surface, int width, int height) {
        mSurfaceTexture = surface;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        //缓冲区大小变化时回调，该方法不需要用户自己处理
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        stopRecord();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        //当每一帧数据可用时回调，不做处理
    }

//    ==============================================================================

    /**
     * 初始化相机
     */
    private void initCamera() {
        if (mSurfaceTexture == null) return;
        if (mCamera != null) {
            releaseCamera();
        }

        mCamera = Camera.open(mCameraId);
        if (mCamera == null) {
            Toast.makeText(this, "没有可用相机", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            mCamera.setPreviewTexture(mSurfaceTexture);
            mRotationDegree = CameraUtil.getCameraDisplayOrientation(this, mCameraId);
            mCamera.setDisplayOrientation(mRotationDegree);
            setCameraParameter(mCamera);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 设置相机的参数
     *
     * @param camera
     */
    private void setCameraParameter(Camera camera) {
        if (camera == null) return;
        Camera.Parameters parameters = camera.getParameters();
        //获取相机支持的>=20fps的帧率，用于设置给MediaRecorder
        //因为获取的数值是*1000的，所以要除以1000
        List<int[]> previewFpsRange = parameters.getSupportedPreviewFpsRange();
        for (int[] ints : previewFpsRange) {
            if (ints[0] >= 20000) {
                mFps = ints[0] / 1000;
                break;
            }
        }
        //设置聚焦模式
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }


        //设置预览尺寸,因为预览的尺寸和最终是录制视频的尺寸无关，所以我们选取最大的数值
        //通常最大的是手机的分辨率，这样可以让预览画面尽可能清晰并且尺寸不变形，前提是TextureView的尺寸是全屏或者接近全屏
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        parameters.setPreviewSize(supportedPreviewSizes.get(0).width, supportedPreviewSizes.get(0).height);
        //缩短Recording启动时间
        parameters.setRecordingHint(true);
        //是否支持影像稳定能力，支持则开启
        if (parameters.isVideoStabilizationSupported())
            parameters.setVideoStabilization(true);
        camera.setParameters(parameters);
    }


    /**
     * 释放相机
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }


    /**
     * 初始化MediaRecorder
     */
    private void initMediaRecorder() {
        //如果是处于release状态，那么只有重新new一个进入initial状态
        //否则其他状态都可以通过reset()方法回到initial状态
        if (mStatus == RecorderStatus.RELEASED) {
            mMediaRecorder = new MediaRecorder();
        } else {
            mMediaRecorder.reset();
        }
        //设置选择角度，顺时针方向，因为默认是逆向90度的，这样图像就是正常显示了,这里设置的是观看保存后的视频的角度
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setOrientationHint(mRotationDegree);
        mMediaRecorder.setOnErrorListener(onErrorListener);
        //采集声音来源、mic是麦克风
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        //采集图像来源、
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        //设置编码参数
//        setProfile();
        setConfig();

        mMediaRecorder.setPreviewDisplay(new Surface(mSurfaceTexture));
        //设置输出的文件路径
        mMediaRecorder.setOutputFile(FileUtil.newMp4File().getAbsolutePath());
    }


    /**
     * 释放MediaRecorder
     */
    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            if (mStatus == RecorderStatus.RELEASED) return;
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setPreviewDisplay(null);
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            mStatus = RecorderStatus.RELEASED;
            //停止计时
            chronometer.stop();
        }
    }


    /**
     * 通过系统的CamcorderProfile设置MediaRecorder的录制参数
     */
    private void setProfile() {
        CamcorderProfile profile = null;
        if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
            profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
        } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
            profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
        } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
            profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);

        }
        if (profile != null) {
            mMediaRecorder.setProfile(profile);
        }
    }


    /**
     * 自定义MediaRecorder的录制参数
     */
    private void setConfig() {
        //设置封装格式 默认是MP4
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        //音频编码
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        //图像编码
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //声道
        mMediaRecorder.setAudioChannels(1);
        //设置最大录像时间 单位：毫秒
        mMediaRecorder.setMaxDuration(60 * 1000);
        //设置最大录制的大小60M 单位，字节
        mMediaRecorder.setMaxFileSize(60 * 1024 * 1024);
        //再用44.1Hz采样率
        mMediaRecorder.setAudioEncodingBitRate(22050);
        //设置帧率，该帧率必须是硬件支持的，可以通过Camera.CameraParameter.getSupportedPreviewFpsRange()方法获取相机支持的帧率
        mMediaRecorder.setVideoFrameRate(mFps);
        //设置码率
        mMediaRecorder.setVideoEncodingBitRate(500 * 1024 * 8);
        //设置视频尺寸，通常搭配码率一起使用，可调整视频清晰度
        mMediaRecorder.setVideoSize(1280, 720);
    }
}
