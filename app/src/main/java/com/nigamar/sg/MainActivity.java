package com.nigamar.sg;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.print.PrintHelper;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Chronometer;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextureView mTextureView;

    private static final int CAMERA_REQUEST_CODE=1;

    private static final int WRITE_REQUEST_CODE_VIDEO=2;

    private static final int WRITE_REQUEST_CODE_IMAGE=3;

    private FloatingActionButton capturePhoto, captureVideo;

    private String mCameraId;

    private Toolbar toolbar;

    private HandlerThread mBackgroundHandlerThread;

    private Handler mBackgroundHandler;

    private Size mPreviewSize;

    private File mRootDirectory;

    private File mVideoFolder;

    private String mVideoFileName;

    private File mImageFolder;

    private String mImageFileName;

    private int mTotalRotation;

    private Size mVideoSize;

    private Size mImageSize;

    private ImageReader mImageReader;

    private MediaRecorder mMediaRecorder;

    private boolean isRecording;

    private Chronometer chronometer;

    private CameraCaptureSession mPreviewCaptureSession;

    private CameraCaptureSession.CaptureCallback mOnPreviewCaptureCallback=new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            startStillCaptureRequest();
        }
    };

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() / (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener=new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(reader.acquireLatestImage()));
        }
    };

    private class ImageSaver implements Runnable{
        private final Image mImage;

        public ImageSaver(Image mImage) {
            this.mImage = mImage;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer=mImage.getPlanes()[0].getBuffer();
            byte[] bytes=new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            FileOutputStream fileOutputStream=null;
            try {
                fileOutputStream=new FileOutputStream(mImageFileName);
                fileOutputStream.write(bytes);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                mImage.close();
                Intent mediaStoreUpdateIntent=new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaStoreUpdateIntent.setData(Uri.fromFile(new File(mImageFileName)));
                sendBroadcast(mediaStoreUpdateIntent);
                if (fileOutputStream!=null){
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setUpCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraDevice mCameraDevice;

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            if (isRecording){
                try {
                    createVideoFileName();
                    startRecording();
                    mMediaRecorder.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else{
                startPreview();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private CaptureRequest.Builder mCaptureRequestBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isRecording=false;
        setContentView(R.layout.activity_main);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("Selie Geek");
        chronometer= (Chronometer) findViewById(R.id.chronometer);
        capturePhoto = (FloatingActionButton) findViewById(R.id.capturePhoto);
        captureVideo = (FloatingActionButton) findViewById(R.id.captureVideo);
        mTextureView = (TextureView) findViewById(R.id.cameraTextureView);
        createDirectory();
        createVideoFolder();
        createImageFolder();
        mMediaRecorder=new MediaRecorder();
        captureVideo.setOnClickListener(this);
        capturePhoto.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            setUpCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onClick(View v) {
        if (v==captureVideo){
            if (isRecording){
                chronometer.stop();
                chronometer.setVisibility(View.INVISIBLE);
                isRecording=false;
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                // update the media store that a new video is taken
                Intent mediaStoreUpdateIntent=new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaStoreUpdateIntent.setData(Uri.fromFile(new File(mVideoFileName)));
                sendBroadcast(mediaStoreUpdateIntent);
                captureVideo.setImageTintList(ColorStateList.valueOf(Color.BLACK));
                startPreview();
                capturePhoto.setVisibility(View.VISIBLE);
            }
            else {
                isRecording=true;
                capturePhoto.setVisibility(View.INVISIBLE);
                captureVideo.setImageTintList(ColorStateList.valueOf(Color.RED));
                checkWriteStoragePermissionVideo();
            }
        }
        else if (v==capturePhoto){
            checkWriteStoragePermissionImage();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case CAMERA_REQUEST_CODE:
                if (grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(getApplicationContext(),"The application will not run if u deny the camera permission",Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            case WRITE_REQUEST_CODE_VIDEO:
                if (grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(getApplicationContext(),"The application will not be able to save videos",Toast.LENGTH_LONG).show();
                }
                else {
                    try{
                        createVideoFileName();
                        startRecording();
                        mMediaRecorder.start();
                        chronometer.setBase(SystemClock.elapsedRealtime());
                        chronometer.setVisibility(View.VISIBLE);
                        chronometer.start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case WRITE_REQUEST_CODE_IMAGE:
                if (grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(getApplicationContext(),"The application will not be able to save videos",Toast.LENGTH_LONG).show();
                }
                else {
                   lockFocus();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_open_gallery) {
            Intent intent=new Intent(this,GalleryActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setUpCamera(int width, int height) {

        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                int totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                mVideoSize=chooseOptimalSize(map.getOutputSizes(MediaRecorder.class),rotatedWidth,rotatedHeight);
                mImageSize=chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG),rotatedWidth,rotatedHeight);
                mImageReader= ImageReader.newInstance(mImageSize.getWidth(),mImageSize.getHeight(),ImageFormat.JPEG,1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);
                // process the front facing camera
                mCameraId = cameraId;
                mTotalRotation=totalRotation;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 23){
                if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==
                        PackageManager.PERMISSION_GRANTED){
                    manager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
                }
                else{
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                        Toast.makeText(this,"Selfie geek requires access to camera ",Toast.LENGTH_LONG).show();
                    }
                    requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST_CODE);
                }
            }
            else {
                manager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startRecording(){
        try {
            setUpMediaRecorder();
            SurfaceTexture surfaceTexture=mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            Surface previewSurface=new Surface(surfaceTexture);
            Surface recordSurface=mMediaRecorder.getSurface();
            mCaptureRequestBuilder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(mCaptureRequestBuilder.build(),null,null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            },null);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void startPreview(){
        SurfaceTexture surfaceTexture=mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface previewSurface=new Surface(surfaceTexture);
        try {
            mCaptureRequestBuilder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface,mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mPreviewCaptureSession=session;
                    try {
                        mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),null,mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Toast.makeText(getApplicationContext(),"unable to set up session",Toast.LENGTH_LONG).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startStillCaptureRequest(){
        try {
            mCaptureRequestBuilder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,mTotalRotation);
            CameraCaptureSession.CaptureCallback stillCapture= new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                    try {
                        createImageFileName();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(),stillCapture,null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera(){

        if (mCameraDevice!=null){
            mCameraDevice.close();
            mCameraDevice=null;
        }
    }

    private void startBackgroundThread(){
        mBackgroundHandlerThread=new HandlerThread("SG");
        mBackgroundHandlerThread.start();
        mBackgroundHandler=new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread(){
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread=null;
            mBackgroundHandler=null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics,int deviceOrientation){
        int sensorOrientation=cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation=ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation+deviceOrientation+360) % 360 ;
    }

    private static Size chooseOptimalSize(Size[] choices,int width,int height){
        List<Size> bigEnough=new ArrayList<>();
        for (Size option:choices){
            if (option.getWidth()>=width && option.getHeight()>=height){
                bigEnough.add(option);
            }
        }
        if (bigEnough.size()>0){
            return Collections.min(bigEnough,new CompareSizeByArea());
        }
        return choices[0];
    }

    private void createDirectory(){
        mRootDirectory = new File(Environment.getExternalStorageDirectory(),CONSTANTS.DIRECTORY_NAME);
        if (!mRootDirectory.exists()){
            mRootDirectory.mkdirs();
        }
    }
    private void createVideoFolder(){
        //File movieFile= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        mVideoFolder=new File(mRootDirectory,CONSTANTS.VIDEOS_FOLDER);
        if (!mVideoFolder.exists()){
            mVideoFolder.mkdirs();
        }
    }

    private void createImageFolder(){
        //File imageFile= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFolder=new File(mRootDirectory,CONSTANTS.IMAGES_FOLDER);
        if (!mImageFolder.exists()){
            mImageFolder.mkdirs();
        }
    }

    private File createImageFileName() throws IOException {
        String timestamp=new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prefix="IMAGE_"+timestamp;
        File imageFile=File.createTempFile(prefix,".jpg",mImageFolder);
        mImageFileName=imageFile.getAbsolutePath();
        return imageFile;
    }

    private File createVideoFileName() throws IOException {
        String timestamp=new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prefix="VIDEO_"+timestamp;
        File videoFile=File.createTempFile(prefix,".mp4",mVideoFolder);
        mVideoFileName=videoFile.getAbsolutePath();
        return videoFile;
    }

    private void checkWriteStoragePermissionImage(){
        if (Build.VERSION.SDK_INT>=23){
            if (ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)==
                    PackageManager.PERMISSION_GRANTED){
                    lockFocus();
            }
            else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                    Toast.makeText(this,"App needs to save videos",Toast.LENGTH_LONG).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},WRITE_REQUEST_CODE_IMAGE);
            }
        }
        else {
                lockFocus();
        }
    }

    private void checkWriteStoragePermissionVideo(){
        if (Build.VERSION.SDK_INT>=23){
            if (ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)==
                    PackageManager.PERMISSION_GRANTED){
                try {
                    createVideoFileName();
                    startRecording();
                    mMediaRecorder.start();
                    chronometer.setBase(SystemClock.elapsedRealtime());
                    chronometer.setVisibility(View.VISIBLE);
                    chronometer.start();
                } catch (IOException e) {
                    Toast.makeText(this,"unable to record video",Toast.LENGTH_LONG).show();
                }
            }
            else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                    Toast.makeText(this,"App needs to save videos",Toast.LENGTH_LONG).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},WRITE_REQUEST_CODE_VIDEO);
            }
        }
        else {
            try {
                createVideoFileName();
                startRecording();
                mMediaRecorder.start();
                chronometer.setBase(SystemClock.elapsedRealtime());
                chronometer.setVisibility(View.VISIBLE);
                chronometer.start();
            } catch (IOException e) {
                Toast.makeText(this,"unable to record video",Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setUpMediaRecorder() throws IOException{

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mVideoFileName);
        mMediaRecorder.setVideoEncodingBitRate(1000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(),mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setOrientationHint(mTotalRotation);
        mMediaRecorder.prepare();
    }

    private void lockFocus(){
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(),mOnPreviewCaptureCallback,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
