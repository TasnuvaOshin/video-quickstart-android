package com.twilio.video.examples.uvccameracapturer;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.twilio.video.LocalVideoTrack;

/**
 * Simple activity that renders UVCCameraCapturer frames to a SurfaceView.
 */
public class UVCCameraCapturerActivity extends AppCompatActivity {
    private SurfaceView uvcCameraVideoView;
    private Button toggleCameraButton;
    private UVCCameraCapturer uvcCameraCapturer;
    private LocalVideoTrack uvcCameraVideoTrack;
    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            uvcCameraCapturer = new UVCCameraCapturer(UVCCameraCapturerActivity.this,
                    surfaceHolder.getSurface());
            uvcCameraVideoTrack = LocalVideoTrack.create(UVCCameraCapturerActivity.this,
                    true,
                    uvcCameraCapturer);
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        }
    };
    private final View.OnClickListener toggleCamera = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (uvcCameraVideoTrack != null) {
                uvcCameraVideoTrack.release();
                uvcCameraVideoTrack = null;
            } else {
                uvcCameraVideoTrack = LocalVideoTrack.create(UVCCameraCapturerActivity.this,
                        true,
                        uvcCameraCapturer);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uvccamera_capturer);
        uvcCameraVideoView = (SurfaceView) findViewById(R.id.uvccamera_video_view);
        toggleCameraButton = (Button) findViewById(R.id.toggle_uvc_camera_button);
        uvcCameraVideoView.getHolder().addCallback(surfaceCallback);
        toggleCameraButton.setOnClickListener(toggleCamera);
    }

    @Override
    protected void onDestroy() {
        if (uvcCameraVideoTrack != null) {
            uvcCameraVideoTrack.release();
        }

        super.onDestroy();
    }
}
