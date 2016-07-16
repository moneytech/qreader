/*
 * Copyright (C) 2016 Nishant Srivastava
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package github.nisrulz.qreader;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import java.io.IOException;

/**
 * QREader Singleton.
 */
public class QREader {
  private static final String LOGTAG = "QREader";
  private CameraSource cameraSource = null;
  private BarcodeDetector barcodeDetector = null;

  /**
   * The constant FRONT_CAM.
   */
  public static final int FRONT_CAM = CameraSource.CAMERA_FACING_FRONT;
  /**
   * The constant BACK_CAM.
   */
  public static final int BACK_CAM = CameraSource.CAMERA_FACING_BACK;

  private boolean autofocusEnabled;
  private final int width;
  private final int height;
  private final int facing;
  private boolean cameraRunning = false;
  private final QRDataListener qrDataListener;
  private final Context context;
  private final SurfaceView surfaceView;

  private SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
    @Override public void surfaceCreated(SurfaceHolder surfaceHolder) {
      try {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
          Log.e(LOGTAG, "Permission not granted!");
          return;
        } else if (!cameraRunning && cameraSource != null && surfaceView != null) {
          cameraSource.start(surfaceView.getHolder());
          cameraRunning = true;
        }
      } catch (IOException ie) {
        Log.e(LOGTAG, ie.toString());
      }
    }

    @Override public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
      // do nothing
    }

    @Override public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
      stop();
    }
  };

  /**
   * Instantiates a new Qr eader.
   *
   * @param builder the builder
   */
  public QREader(final Builder builder) {
    this.autofocusEnabled = builder.autofocusEnabled;
    this.width = builder.width;
    this.height = builder.height;
    this.facing = builder.facing;
    this.qrDataListener = builder.qrDataListener;
    this.context = builder.context;
    this.surfaceView = builder.surfaceView;
    this.barcodeDetector = builder.barcodeDetector;
    //for better performance we should use one detector for all Reader, if builder not specify it
    if (barcodeDetector == null)
       barcodeDetector = BarcodeDetectorHolder.getBarcodeDetector(context);
  }

  /**
   * Init.
   */
  public void init() {
    if (!hasAutofocus(context)) {
      Log.e(LOGTAG, "Do not have autofocus feature, disabling autofocus feature in the library!");
      autofocusEnabled = false;
    }

    if (!hasCameraHardware(context)) {
      Log.e(LOGTAG, "Does not have camera hardware!");
      return;
    }
    if (!checkCameraPermission(context)) {
      Log.e(LOGTAG, "Do not have camera permission!");
      return;
    }



    if (barcodeDetector.isOperational()) {
      barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
        @Override public void release() {
          // Handled via public method
        }

        @Override public void receiveDetections(Detector.Detections<Barcode> detections) {
          final SparseArray<Barcode> barcodes = detections.getDetectedItems();
          if (barcodes.size() != 0 && qrDataListener != null) {
            qrDataListener.onDetected(barcodes.valueAt(0).displayValue);
          }
        }
      });
      barcodeDetector.setProcessor(new MultiProcessor.Builder<>(new MultiProcessor.Factory<Barcode>() {
        @Override public Tracker<Barcode> create(Barcode barcode) {
          return new Tracker<>();
        }
      }).build());
    } else {
      Log.e(LOGTAG, "Barcode recognition libs are not downloaded and are not operational");
    }

    // Setup Camera
    cameraSource =
        new CameraSource.Builder(context, barcodeDetector).setAutoFocusEnabled(autofocusEnabled)
            .setFacing(facing)
            .setRequestedPreviewSize(width, height)
            .build();
  }

  /**
   * Start.
   */
  public void start() {
    if (surfaceView != null && surfaceHolderCallback != null) {
      surfaceView.getHolder().addCallback(surfaceHolderCallback);
    }
  }

  /**
   * Stop camera
   */
  public void stop() {
    try {
      if (surfaceView != null && surfaceHolderCallback != null) {
        surfaceView.getHolder().removeCallback(surfaceHolderCallback);
      }
      if (cameraRunning && cameraSource != null) {
        cameraSource.stop();
        cameraRunning = false;
      }
    } catch (Exception ie) {
      Log.e(LOGTAG, ie.getMessage());
    }
  }

  /**
   * Release and cleanup qreader.
   */
  public void releaseAndCleanup() {
    stop();
    if (cameraSource != null) {
      cameraSource.release();
      cameraSource = null;
    }
  }

  private boolean checkCameraPermission(Context context) {
    String permission = Manifest.permission.CAMERA;
    int res = context.checkCallingOrSelfPermission(permission);
    return res == PackageManager.PERMISSION_GRANTED;
  }

  private boolean hasCameraHardware(Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
  }

  private boolean hasAutofocus(Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS);
  }

  /**
   * The type Builder.
   */
  public static class Builder {
    private boolean autofocusEnabled;
    private int width;
    private int height;
    private int facing;
    private QRDataListener qrDataListener;
    private Context context;
    private SurfaceView surfaceView;
    private BarcodeDetector barcodeDetector;
    /**
     * Instantiates a new Builder.
     *
     * @param context the context
     * @param surfaceView the surface view
     * @param qrDataListener the qr data listener
     */
    public Builder(Context context, SurfaceView surfaceView, QRDataListener qrDataListener) {
      this.autofocusEnabled = true;
      this.width = 800;
      this.height = 800;
      this.facing = BACK_CAM;
      this.qrDataListener = qrDataListener;
      this.context = context;
      this.surfaceView = surfaceView;
    }

    public Builder(BarcodeDetector detector, QRDataListener qrDataListener, Context context, SurfaceView surfaceView) {
      this.barcodeDetector = detector;
      this.qrDataListener = qrDataListener;
      this.context = context;
      this.surfaceView = surfaceView;
      this.autofocusEnabled = true;
      this.width = 800;
      this.height = 800;
      this.facing = BACK_CAM;
    }

    /**
     * Enable autofocus builder.
     *
     * @param autofocusEnabled the autofocus enabled
     * @return the builder
     */
    public Builder enableAutofocus(boolean autofocusEnabled) {
      this.autofocusEnabled = autofocusEnabled;
      return this;
    }

    /**
     * Width builder.
     *
     * @param width the width
     * @return the builder
     */
    public Builder width(int width) {
      this.width = width;
      return this;
    }

    /**
     * Height builder.
     *
     * @param height the height
     * @return the builder
     */
    public Builder height(int height) {
      this.height = height;
      return this;
    }

    /**
     * Facing builder.
     *
     * @param facing the facing
     * @return the builder
     */
    public Builder facing(int facing) {
      this.facing = facing;
      return this;
    }

    /**
     * Build qr eader.
     *
     * @return the qr eader
     */
    public QREader build() {
      return new QREader(this);
    }

    public void setBarcodeDetector(BarcodeDetector barcodeDetector) {
      this.barcodeDetector = barcodeDetector;
    }
  }
}

