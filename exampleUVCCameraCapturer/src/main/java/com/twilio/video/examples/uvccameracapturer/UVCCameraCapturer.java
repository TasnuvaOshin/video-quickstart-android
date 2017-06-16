package com.twilio.video.examples.uvccameracapturer;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.twilio.video.VideoCapturer;
import com.twilio.video.VideoDimensions;
import com.twilio.video.VideoFormat;
import com.twilio.video.VideoFrame;
import com.twilio.video.VideoPixelFormat;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An implementation of VideoCapturer that allows for frames to be captured for a LocalVideoTrack.
 */
public class UVCCameraCapturer implements VideoCapturer {
    private static final String TAG = "UVCCameraCapturer";

    private final Context context;
    private final Surface surface;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private HandlerThread uvcCameraThread;
    private Handler uvcCameraHandler;
    private USBMonitor usbMonitor;
    private UVCCamera uvcCamera;
    private VideoCapturer.Listener videoCapturerListener;
    private VideoFormat videoFormat;

    /*
     * Frame callback that forwards camera frames to Twilio VideoCapturer.Listener if the
     * capturer is running.
     */
    private final IFrameCallback frameCallback = new IFrameCallback() {
        @Override
        public void onFrame(ByteBuffer frame) {
            if (running.get()) {
                byte[] buffer = new byte[frame.capacity()];
                frame.get(buffer);
                VideoFrame videoFrame = new VideoFrame(buffer,
                        videoFormat.dimensions,
                        VideoFrame.RotationAngle.ROTATION_0,
                        TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime()));
                videoCapturerListener.onFrameCaptured(videoFrame);
            }
        }
    };

    /*
     * Handles USB callbacks.
     */
    private final USBMonitor.OnDeviceConnectListener deviceConnectListener =
            new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            uvcCameraHandler.post(new Runnable() {
                @Override
                public void run() {
                    openCameraUsbDeviceOnCameraThread(device);
                }
            });
        }

        @Override
        public void onConnect(final UsbDevice device,
                              final USBMonitor.UsbControlBlock ctrlBlock,
                              final boolean createNew) {

            uvcCameraHandler.post(new Runnable() {
                @Override
                public void run() {
                    startUvcCameraPreviewOnCameraThread(ctrlBlock);
                }
            });
        }

        @Override
        public void onDisconnect(final UsbDevice device,
                                 final USBMonitor.UsbControlBlock ctrlBlock) {
            stopCapture();
        }

        @Override
        public void onDettach(final UsbDevice device) {
            stopCapture();
        }

        @Override
        public void onCancel(final UsbDevice device) {
            stopCapture();
        }
    };

    public UVCCameraCapturer(Context context, Surface surface) {
        this.context = context;
        this.surface = surface;
    }

    /*
     * Returns list of supported formats. For now the capturer only supports the default
     * UVCCamera format with RGBA frames.
     */
    @Override
    public List<VideoFormat> getSupportedFormats() {
        VideoDimensions dimensions = new VideoDimensions(UVCCamera.DEFAULT_PREVIEW_WIDTH,
                UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        VideoFormat videoFormat = new VideoFormat(dimensions,
                UVCCamera.DEFAULT_PREVIEW_MAX_FPS,
                VideoPixelFormat.RGBA_8888);

        return Collections.singletonList(videoFormat);
    }

    @Override
    public boolean isScreencast() {
        return false;
    }

    @Override
    public void startCapture(final VideoFormat videoFormat, final Listener listener) {
        if (running.compareAndSet(false, true)) {
            this.uvcCameraThread = new HandlerThread(TAG);
            this.uvcCameraThread.start();
            this.uvcCameraHandler = new Handler(uvcCameraThread.getLooper());
            uvcCameraHandler.post(new Runnable() {
                @Override
                public void run() {
                    startCaptureOnCameraThread(videoFormat, listener);
                }
            });
        }
    }

    @Override
    public void stopCapture() {
        /*
         * Capturing must be stopped synchronously.
         */
        if (running.compareAndSet(true, false)) {
            final CountDownLatch capturerStopped = new CountDownLatch(1);
            uvcCameraHandler.postAtFrontOfQueue(new Runnable() {
                @Override
                public void run() {
                    stopCaptureOnCameraThread();
                    capturerStopped.countDown();
                }
            });
            try {
                capturerStopped.await(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop UVCCameraCapturer");
            }
            uvcCameraThread.quit();
            uvcCameraThread = null;
            uvcCameraHandler = null;
        }
    }

    private void startCaptureOnCameraThread(VideoFormat videoFormat, Listener listener) {
        checkIsOnCameraThread();
        this.videoCapturerListener = listener;
        this.videoFormat = videoFormat;

        /*
         * Register USB monitor and wait until device is attached.
         */
        usbMonitor = new USBMonitor(context, deviceConnectListener);
        usbMonitor.register();
    }

    private void openCameraUsbDeviceOnCameraThread(UsbDevice usbDevice) {
        checkIsOnCameraThread();
        /*
         * Requests to connect to USB camera.
         */
        usbMonitor.requestPermission(usbDevice);
    }

    private void startUvcCameraPreviewOnCameraThread(final USBMonitor.UsbControlBlock ctrlBlock) {
        checkIsOnCameraThread();
        /*
         * Start the UVC Camera preview.
         */
        uvcCamera = new UVCCamera();
        uvcCamera.open(ctrlBlock);
        uvcCamera.setPreviewSize(videoFormat.dimensions.width,
                videoFormat.dimensions.height,
                UVCCamera.FRAME_FORMAT_YUYV);
        uvcCamera.setPreviewDisplay(surface);
        uvcCamera.setFrameCallback(frameCallback, UVCCamera.PIXEL_FORMAT_RGBX);
        uvcCamera.startPreview();

        /*
         * Notify the capturer API that the capturer has started.
         */
        videoCapturerListener.onCapturerStarted(true);
    }

    private void stopCaptureOnCameraThread() {
        checkIsOnCameraThread();
        /*
         * Teardown UVC Camera and USB monitor.
         */
        if (uvcCamera != null) {
            uvcCamera.stopPreview();
            uvcCamera.close();
            uvcCamera.destroy();
            uvcCamera = null;

        }
        if (usbMonitor != null) {
            usbMonitor.unregister();
            usbMonitor.destroy();
            usbMonitor = null;
        }
    }

    private void checkIsOnCameraThread() {
        if (Thread.currentThread() != uvcCameraHandler.getLooper().getThread()) {
            throw new IllegalStateException("Wrong thread");
        }
    }
}
