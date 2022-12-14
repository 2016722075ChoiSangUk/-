package com.plateocr;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.Rect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CameraActivity extends AppCompatActivity {

    OcrRunner ocrRunner;

    // 화면 각도 상수
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};
    Bitmap bitmap = null;
    private boolean isHttpRuning = false;
    private boolean isCapturing = false;

    // 권한 관련 변수 값
    private int REQUEST_CODE_PERMISSIONS = 1001;
    // 뷰 객체
    private TextureView textureView;
    private Button button;
    private ImageView imageView;
    private TextView plateText;
    private TextView infoText;
    private TextView summaryText;

    // 카메라2 변수 공간
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest captureRequest;
    private CaptureRequest.Builder captureRequestBuilder;
    // 이미지 저장 변수 공간
    private Size imageDimensions;
    private ImageReader imageReader;
    private File file;
    private Handler mBackgroundHandler;

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            try {
                createCameraPreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    private HandlerThread mBackgroundThread;
    private TextView textView;
    // 리스너 콜백 함수
    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            try {
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        String guardId = getIntent().getStringExtra("guardId");
        String guardName = getIntent().getStringExtra("guardName");
        int apartmentId = getIntent().getIntExtra("apartmentId", -1);
        String apartmentName = getIntent().getStringExtra("apartmentName");

        textureView = (TextureView) findViewById(R.id.textureView);
        button = (Button) findViewById(R.id.startBtn);
        textView = findViewById(R.id.resultText);
        imageView = findViewById(R.id.imgView);
        plateText = findViewById(R.id.plateText);
        infoText = findViewById(R.id.info);
        summaryText = findViewById(R.id.result_summary);

        textureView.setSurfaceTextureListener(textureListener);
        infoText.setText(apartmentName+" / "+guardName+"님");


        ocrRunner = new OcrRunner(this, (str, bmp) -> {
            Log.d(">>>", "found:"+str);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    imageView.setImageBitmap(bmp);
                    plateText.setText(str);
                }
            });
            if(!isHttpRuning) {
                HttpHelper.checkPlateNumber(str, new HttpHelper.HttpListener() {
                    @Override
                    public void onSuccess(String data) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if("notfound".equals(data)) {
                                    textView.setText("["+str + "]은 등록된 차량이 아닙니다.");
                                    summaryText.setBackgroundResource(R.color.red);
                                    summaryText.setText("미등록 차량");
                                } else {
                                    // ex: 김철수,둘리 아파트,102동 103호,54가0639,N,2021-08-23 18:26:08
                                    String ss[] = data.split(",");
                                    textView.setText("소유주 : "+ss[0]+"\n주소 : "+ss[1]+" "+ss[2]+"\n차량등록 : "+ss[4]+"\n등록일시 : "+ss[5]);
                                    summaryText.setBackgroundResource(R.color.green);
                                    summaryText.setText("등록 차량");
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(String e) {
                        Log.d(">>>", e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText(e.toString());
                            }
                        });

                    }

                    @Override
                    public void onComplete() {
                        isHttpRuning = false;
                    }
                }).start();
            }
        });



        button.setOnClickListener(new View.OnClickListener() {
            Thread thread;
            @Override
            public void onClick(View v) {
                if(isCapturing) {
                    button.setText("시작");
                    if(thread!=null)
                        thread.interrupt();
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    isCapturing = false;
                } else {
                    button.setText("멈춤");
                    thread = new Thread(()->{
                        isCapturing = true;
                        while(!thread.isInterrupted()) {
                            captureAndOcr();
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                break;
                            }
                        }
                    });
                    thread.start();
                }
            }
        });

    }

    private void openCamera() throws CameraAccessException {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        cameraId = manager.getCameraIdList()[0];
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        imageDimensions = map.getOutputSizes(SurfaceTexture.class)[0];

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            manager.openCamera(cameraId, stateCallback, null);
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void takePicture() throws CameraAccessException {
        if (cameraDevice == null)
            return;

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
        Size[] jpegSizes = null;

        jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);

        int width = 640;
        int height = 480;

        if (jpegSizes != null && jpegSizes.length > 0) {
            width = jpegSizes[0].getWidth();
            height = jpegSizes[0].getHeight();
        }

        ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        List<Surface> outputSurfaces = new ArrayList<>(2);
        outputSurfaces.add(reader.getSurface());

        outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

        final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureBuilder.addTarget(reader.getSurface());
        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

        Long tsLong = System.currentTimeMillis() / 1000;
        String ts = tsLong.toString();

        file = new File(this.getFilesDir() + "/" + ts + ".jpg");

        Log.d(">>>", file.getAbsolutePath());
        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;

                image = reader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                try {
                    save(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }
        };

        reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                try {
                    createCameraPreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        };

        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                try {
                    session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            }
        }, mBackgroundHandler);

    }

    private void createCameraPreview() throws CameraAccessException {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        texture.setDefaultBufferSize(imageDimensions.getWidth(), imageDimensions.getHeight());
        Surface surface = new Surface(texture);

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        captureRequestBuilder.addTarget(surface);

        cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                if (cameraDevice == null) {
                    return;
                }

                cameraCaptureSession = session;
                try {
                    updatePreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Toast.makeText(getApplicationContext(), "Configuration Changed", Toast.LENGTH_LONG).show();
            }
        }, null);
    }

    private void updatePreview() throws CameraAccessException {
        if (cameraDevice == null) {
            return;
        }

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);

    }

    private void save(byte[] bytes) throws IOException {
        OutputStream outputStream = null;
        outputStream = new FileOutputStream(file);
        outputStream.write(bytes);
        outputStream.close();
    }

    private Bitmap getLastPicture() throws FileNotFoundException {
        return BitmapFactory.decodeStream(new FileInputStream(file));
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() throws InterruptedException {
        mBackgroundThread.quitSafely();
        mBackgroundThread.join();
        mBackgroundThread = null;
        mBackgroundHandler = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        if (textureView.isAvailable()) {
            try {
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            stopBackgroundThread();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void captureAndOcr() {
        Bitmap orig = textureView.getBitmap();
        bitmap = orig;
        float density = getResources().getDisplayMetrics().density;
        int regionHeight = Math.round((float) 150 * density);;
        int hb = (bitmap.getHeight() - regionHeight) / 2;
        Bitmap croped = Bitmap.createBitmap(bitmap, 0, hb, bitmap.getWidth(), regionHeight);
        bitmap = croped;

        ocrRunner.find(bitmap);
    }
}