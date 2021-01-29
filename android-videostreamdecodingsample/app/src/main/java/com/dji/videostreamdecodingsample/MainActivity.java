package com.dji.videostreamdecodingsample;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.videostreamdecodingsample.media.DJIVideoStreamDecoder;
import com.dji.videostreamdecodingsample.media.NativeHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import dji.common.airlink.PhysicalSource;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.sdk.airlink.OcuSyncLink;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.thirdparty.afinal.core.AsyncTask;

public class MainActivity extends Activity implements DJICodecManager.YuvDataCallback {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MSG_WHAT_SHOW_TOAST = 0;
    private static final int MSG_WHAT_UPDATE_TITLE = 1;
    private SurfaceHolder.Callback surfaceCallback;
    private enum DemoType { USE_TEXTURE_VIEW, USE_SURFACE_VIEW, USE_SURFACE_VIEW_DEMO_DECODER}
    private static DemoType demoType = DemoType.USE_TEXTURE_VIEW;
    private VideoFeeder.VideoFeed standardVideoFeeder;


    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;
    private TextView titleTv;
    public Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WHAT_SHOW_TOAST:
                    Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_WHAT_UPDATE_TITLE:
                    if (titleTv != null) {
                        titleTv.setText((String) msg.obj);
                    }
                    break;
                default:
                    break;
            }
        }
    };
    private TextureView videostreamPreviewTtView;
    private SurfaceView videostreamPreviewSf;
    private SurfaceHolder videostreamPreviewSh;
    private Camera mCamera;
    private DJICodecManager mCodecManager;
    private TextView savePath;
    private Button screenShot;
    private StringBuilder stringBuilder;
    private int videoViewWidth;
    private int videoViewHeight;
    private int count;

    @Override
    protected void onResume() {
        super.onResume();
        initSurfaceOrTextureView();
        notifyStatusChange();
    }

    private void initSurfaceOrTextureView(){
        switch (demoType) {
            case USE_SURFACE_VIEW:
                initPreviewerSurfaceView();
                break;
            case USE_SURFACE_VIEW_DEMO_DECODER:
                /**
                 * we also need init the textureView because the pre-transcoded video steam will display in the textureView
                 */
                initPreviewerTextureView();

                /**
                 * we use standardVideoFeeder to pass the transcoded video data to DJIVideoStreamDecoder, and then display it
                 * on surfaceView
                 */
                initPreviewerSurfaceView();
                break;
            case USE_TEXTURE_VIEW:
                initPreviewerTextureView();
                break;
        }
    }

    @Override
    protected void onPause() {
        if (mCamera != null) {
            if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(mReceivedVideoDataListener);
            }
            if (standardVideoFeeder != null) {
                standardVideoFeeder.removeVideoDataListener(mReceivedVideoDataListener);
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager.destroyCodec();
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        initUi();
        if (isM300Product()) {
            OcuSyncLink ocuSyncLink = VideoDecodingApplication.getProductInstance().getAirLink().getOcuSyncLink();
            // If your MutltipleLensCamera is set at right or top, you need to change the PhysicalSource to RIGHT_CAM or TOP_CAM.
            ocuSyncLink.assignSourceToPrimaryChannel(PhysicalSource.LEFT_CAM, PhysicalSource.FPV_CAM, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    if (error == null) {
                        showToast("assignSourceToPrimaryChannel success.");
                    } else {
                        showToast("assignSourceToPrimaryChannel fail, reason: "+ error.getDescription());
                    }
                }
            });
        }
    }

    public static boolean isM300Product() {
        if (DJISDKManager.getInstance().getProduct() == null) {
            return false;
        }
        Model model = DJISDKManager.getInstance().getProduct().getModel();
        return model == Model.MATRICE_300_RTK;
    }

    private void showToast(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_SHOW_TOAST, s)
        );
    }

    private void updateTitle(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_UPDATE_TITLE, s)
        );
    }

    private void initUi() {
        savePath = (TextView) findViewById(R.id.activity_main_save_path);
        screenShot = (Button) findViewById(R.id.activity_main_screen_shot);
        screenShot.setSelected(false);

        titleTv = (TextView) findViewById(R.id.title_tv);
        videostreamPreviewTtView = (TextureView) findViewById(R.id.livestream_preview_ttv);
        videostreamPreviewSf = (SurfaceView) findViewById(R.id.livestream_preview_sf);
        videostreamPreviewSf.setClickable(true);
        videostreamPreviewSf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float rate = VideoFeeder.getInstance().getTranscodingDataRate();
                showToast("current rate:" + rate + "Mbps");
                if (rate < 10) {
                    VideoFeeder.getInstance().setTranscodingDataRate(10.0f);
                    showToast("set rate to 10Mbps");
                } else {
                    VideoFeeder.getInstance().setTranscodingDataRate(3.0f);
                    showToast("set rate to 3Mbps");
                }
            }
        });
        updateUIVisibility();
    }

    private void updateUIVisibility(){
        switch (demoType) {
            case USE_SURFACE_VIEW:
                videostreamPreviewSf.setVisibility(View.VISIBLE);
                videostreamPreviewTtView.setVisibility(View.GONE);
                break;
            case USE_SURFACE_VIEW_DEMO_DECODER:
                /**
                 * we need display two video stream at the same time, so we need let them to be visible.
                 */
                videostreamPreviewSf.setVisibility(View.VISIBLE);
                videostreamPreviewTtView.setVisibility(View.VISIBLE);
                break;

            case USE_TEXTURE_VIEW:
                videostreamPreviewSf.setVisibility(View.GONE);
                videostreamPreviewTtView.setVisibility(View.VISIBLE);
                break;
        }
    }
    private long lastupdate;
    private void notifyStatusChange() {

        final BaseProduct product = VideoDecodingApplication.getProductInstance();

        Log.d(TAG, "notifyStatusChange: " + (product == null ? "Disconnect" : (product.getModel() == null ? "null model" : product.getModel().name())));
        if (product != null && product.isConnected() && product.getModel() != null) {
            updateTitle(product.getModel().name() + " Connected " + demoType.name());
        } else {
            updateTitle("Disconnected");
        }

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (System.currentTimeMillis() - lastupdate > 1000) {
                    Log.d(TAG, "camera recv video data size: " + size);
                    lastupdate = System.currentTimeMillis();
                }
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        if (mCodecManager != null) {
                            mCodecManager.sendDataToDecoder(videoBuffer, size);
                        }
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        /**
                         we use standardVideoFeeder to pass the transcoded video data to DJIVideoStreamDecoder, and then display it
                         * on surfaceView
                         */
                        DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
                        break;

                    case USE_TEXTURE_VIEW:
                        if (mCodecManager != null) {
                            mCodecManager.sendDataToDecoder(videoBuffer, size);
                        }
                        break;
                }

            }
        };

        if (null == product || !product.isConnected()) {
            mCamera = null;
            showToast("Disconnected");
        } else {
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                mCamera = product.getCamera();
                mCamera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            showToast("can't change mode of camera, error:"+djiError.getDescription());
                        }
                    }
                });

                //When calibration is needed or the fetch key frame is required by SDK, should use the provideTranscodedVideoFeed
                //to receive the transcoded video feed from main camera.
                if (demoType == DemoType.USE_SURFACE_VIEW_DEMO_DECODER && isTranscodedVideoFeedNeeded()) {
                    standardVideoFeeder = VideoFeeder.getInstance().provideTranscodedVideoFeed();
                    standardVideoFeeder.addVideoDataListener(mReceivedVideoDataListener);
                    return;
                }
                if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                    VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
                }

            }
        }
    }

    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera
     */
    private void initPreviewerTextureView() {
        videostreamPreviewTtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "real onSurfaceTextureAvailable");
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable: width " + videoViewWidth + " height " + videoViewHeight);
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
                    //For M300RTK, you need to actively request an I frame.
                    mCodecManager.resetKeyFrame();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable2: width " + videoViewWidth + " height " + videoViewHeight);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mCodecManager != null) {
                    mCodecManager.cleanSurface();
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * Init a surface view for the DJIVideoStreamDecoder
     */
    private void initPreviewerSurfaceView() {
        videostreamPreviewSh = videostreamPreviewSf.getHolder();
        surfaceCallback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "real onSurfaceTextureAvailable");
                videoViewWidth = videostreamPreviewSf.getWidth();
                videoViewHeight = videostreamPreviewSf.getHeight();
                Log.d(TAG, "real onSurfaceTextureAvailable3: width " + videoViewWidth + " height " + videoViewHeight);
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        if (mCodecManager == null) {
                            mCodecManager = new DJICodecManager(getApplicationContext(), holder, videoViewWidth,
                                                                videoViewHeight);
                        }
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        // This demo might not work well on P3C and OSMO.
                        NativeHelper.getInstance().init();
                        DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), holder.getSurface());
                        DJIVideoStreamDecoder.getInstance().resume();
                        break;
                }

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable4: width " + videoViewWidth + " height " + videoViewHeight);
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        //mCodecManager.onSurfaceSizeChanged(videoViewWidth, videoViewHeight, 0);
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        DJIVideoStreamDecoder.getInstance().changeSurface(holder.getSurface());
                        break;
                }

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        if (mCodecManager != null) {
                            mCodecManager.cleanSurface();
                            mCodecManager.destroyCodec();
                            mCodecManager = null;
                        }
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        DJIVideoStreamDecoder.getInstance().stop();
                        NativeHelper.getInstance().release();
                        break;
                }

            }
        };

        videostreamPreviewSh.addCallback(surfaceCallback);
    }


    @Override
    public void onYuvDataReceived(MediaFormat format, final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
        //In this demo, we test the YUV data by saving it into JPG files.
        //DJILog.d(TAG, "onYuvDataReceived " + dataSize);
        if (count++ % 30 == 0 && yuvFrame != null) {
            final byte[] bytes = new byte[dataSize];
            yuvFrame.get(bytes);
            //DJILog.d(TAG, "onYuvDataReceived2 " + dataSize);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    // two samples here, it may has other color format.
                    int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                    switch (colorFormat) {
                        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                            //NV12
                            if (Build.VERSION.SDK_INT <= 23) {
                                oldSaveYuvDataToJPEG(bytes, width, height);
                            } else {
                                newSaveYuvDataToJPEG(bytes, width, height);
                            }
                            break;
                        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                            //YUV420P
                            newSaveYuvDataToJPEG420P(bytes, width, height);
                            break;
                        default:
                            break;
                    }

                }
            });
        }
    }

    // For android API <= 23
    private void oldSaveYuvDataToJPEG(byte[] yuvFrame, int width, int height){
        if (yuvFrame.length < width * height) {
            //DJILog.d(TAG, "yuvFrame size is too small " + yuvFrame.length);
            return;
        }

        byte[] y = new byte[width * height];
        byte[] u = new byte[width * height / 4];
        byte[] v = new byte[width * height / 4];
        byte[] nu = new byte[width * height / 4]; //
        byte[] nv = new byte[width * height / 4];

        System.arraycopy(yuvFrame, 0, y, 0, y.length);
        for (int i = 0; i < u.length; i++) {
            v[i] = yuvFrame[y.length + 2 * i];
            u[i] = yuvFrame[y.length + 2 * i + 1];
        }
        int uvWidth = width / 2;
        int uvHeight = height / 2;
        for (int j = 0; j < uvWidth / 2; j++) {
            for (int i = 0; i < uvHeight / 2; i++) {
                byte uSample1 = u[i * uvWidth + j];
                byte uSample2 = u[i * uvWidth + j + uvWidth / 2];
                byte vSample1 = v[(i + uvHeight / 2) * uvWidth + j];
                byte vSample2 = v[(i + uvHeight / 2) * uvWidth + j + uvWidth / 2];
                nu[2 * (i * uvWidth + j)] = uSample1;
                nu[2 * (i * uvWidth + j) + 1] = uSample1;
                nu[2 * (i * uvWidth + j) + uvWidth] = uSample2;
                nu[2 * (i * uvWidth + j) + 1 + uvWidth] = uSample2;
                nv[2 * (i * uvWidth + j)] = vSample1;
                nv[2 * (i * uvWidth + j) + 1] = vSample1;
                nv[2 * (i * uvWidth + j) + uvWidth] = vSample2;
                nv[2 * (i * uvWidth + j) + 1 + uvWidth] = vSample2;
            }
        }
        //nv21test
        byte[] bytes = new byte[yuvFrame.length];
        System.arraycopy(y, 0, bytes, 0, y.length);
        for (int i = 0; i < u.length; i++) {
            bytes[y.length + (i * 2)] = nv[i];
            bytes[y.length + (i * 2) + 1] = nu[i];
        }
        Log.d(TAG,
              "onYuvDataReceived: frame index: "
                  + DJIVideoStreamDecoder.getInstance().frameIndex
                  + ",array length: "
                  + bytes.length);
        screenShot(bytes, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
    }

    private void newSaveYuvDataToJPEG(byte[] yuvFrame, int width, int height){
        if (yuvFrame.length < width * height) {
            //DJILog.d(TAG, "yuvFrame size is too small " + yuvFrame.length);
            return;
        }
        int length = width * height;

        byte[] u = new byte[width * height / 4];
        byte[] v = new byte[width * height / 4];
        for (int i = 0; i < u.length; i++) {
            v[i] = yuvFrame[length + 2 * i];
            u[i] = yuvFrame[length + 2 * i + 1];
        }
        for (int i = 0; i < u.length; i++) {
            yuvFrame[length + 2 * i] = u[i];
            yuvFrame[length + 2 * i + 1] = v[i];
        }
        screenShot(yuvFrame,Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
    }

    private void newSaveYuvDataToJPEG420P(byte[] yuvFrame, int width, int height) {
        if (yuvFrame.length < width * height) {
            return;
        }
        int length = width * height;

        byte[] u = new byte[width * height / 4];
        byte[] v = new byte[width * height / 4];

        for (int i = 0; i < u.length; i ++) {
            u[i] = yuvFrame[length + i];
            v[i] = yuvFrame[length + u.length + i];
        }
        for (int i = 0; i < u.length; i++) {
            yuvFrame[length + 2 * i] = v[i];
            yuvFrame[length + 2 * i + 1] = u[i];
        }
        screenShot(yuvFrame, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
    }

    /**
     * Save the buffered data into a JPG image file
     */
    private void screenShot(byte[] buf, String shotDir, int width, int height) {
        File dir = new File(shotDir);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }
        YuvImage yuvImage = new YuvImage(buf,
                ImageFormat.NV21,
                width,
                height,
                null);
        OutputStream outputFile;
        final String path = dir + "/ScreenShot_" + System.currentTimeMillis() + ".jpg";
        try {
            outputFile = new FileOutputStream(new File(path));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "test screenShot: new bitmap output file error: " + e);
            return;
        }
        if (outputFile != null) {
            yuvImage.compressToJpeg(new Rect(0,
                    0,
                    width,
                    height), 100, outputFile);
        }
        try {
            outputFile.close();
        } catch (IOException e) {
            Log.e(TAG, "test screenShot: compress yuv image error: " + e);
            e.printStackTrace();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                displayPath(path);
            }
        });
    }


    public void onClick(View v) {

        if (v.getId() == R.id.activity_main_screen_shot) {
            handleYUVClick();
        } else {
            DemoType newDemoType = null;
            if (v.getId() == R.id.activity_main_screen_texture) {
                newDemoType = DemoType.USE_TEXTURE_VIEW;
            } else if (v.getId() == R.id.activity_main_screen_surface) {
                newDemoType = DemoType.USE_SURFACE_VIEW;
            } else if (v.getId() == R.id.activity_main_screen_surface_with_own_decoder) {
                newDemoType = DemoType.USE_SURFACE_VIEW_DEMO_DECODER;
            }

            if (newDemoType != null && newDemoType != demoType) {
                // Although finish will trigger onDestroy() is called, but it is not called before OnCreate of new activity.
                if (mCodecManager != null) {
                    mCodecManager.cleanSurface();
                    mCodecManager.destroyCodec();
                    mCodecManager = null;
                }
                demoType = newDemoType;
                finish();
                overridePendingTransition(0, 0);
                startActivity(getIntent());
                overridePendingTransition(0, 0);
            }
        }
    }

    private void handleYUVClick() {
        if (screenShot.isSelected()) {
            screenShot.setText("YUV Screen Shot");
            screenShot.setSelected(false);

            switch (demoType) {
                case USE_SURFACE_VIEW:
                case USE_TEXTURE_VIEW:
                    mCodecManager.enabledYuvData(false);
                    mCodecManager.setYuvDataCallback(null);
                    // ToDo:
                    break;
                case USE_SURFACE_VIEW_DEMO_DECODER:
                    DJIVideoStreamDecoder.getInstance().changeSurface(videostreamPreviewSh.getSurface());
                    DJIVideoStreamDecoder.getInstance().setYuvDataListener(null);
                    break;
            }
            savePath.setText("");
            savePath.setVisibility(View.INVISIBLE);
            stringBuilder = null;
        } else {
            screenShot.setText("Live Stream");
            screenShot.setSelected(true);

            switch (demoType) {
                case USE_TEXTURE_VIEW:
                case USE_SURFACE_VIEW:
                    mCodecManager.enabledYuvData(true);
                    mCodecManager.setYuvDataCallback(this);
                    break;
                case USE_SURFACE_VIEW_DEMO_DECODER:
                    DJIVideoStreamDecoder.getInstance().changeSurface(null);
                    DJIVideoStreamDecoder.getInstance().setYuvDataListener(MainActivity.this);
                    break;
            }
            savePath.setText("");
            savePath.setVisibility(View.VISIBLE);
        }
    }

    private void displayPath(String path) {
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
        }

        path = path + "\n";
        stringBuilder.append(path);
        savePath.setText(stringBuilder.toString());
    }

    private boolean isTranscodedVideoFeedNeeded() {
        if (VideoFeeder.getInstance() == null) {
            return false;
        }

        return VideoFeeder.getInstance().isFetchKeyFrameNeeded() || VideoFeeder.getInstance()
                                                                               .isLensDistortionCalibrationNeeded();
    }
}
