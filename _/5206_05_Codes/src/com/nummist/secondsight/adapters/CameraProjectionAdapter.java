package com.nummist.secondsight.adapters;

import org.opencv.core.CvType;
import org.opencv.core.MatOfDouble;

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.opengl.Matrix;

public class CameraProjectionAdapter {
    
    float mFOVY = 43.6f; // 30mm equivalent
    float mFOVX = 65.4f; // 30mm equivalent
    int mHeightPx = 640;
    int mWidthPx = 480;
    float mNear = 1f;
    float mFar = 10000f;
    
    final float[] mProjectionGL = new float[16];
    boolean mProjectionDirtyGL = true;
    
    MatOfDouble mProjectionCV;
    boolean mProjectionDirtyCV = true;
    
    public void setCameraParameters(Parameters parameters) {
        mFOVY = parameters.getVerticalViewAngle();
        mFOVX = parameters.getHorizontalViewAngle();
        
        Size pictureSize = parameters.getPictureSize();
        mHeightPx = pictureSize.height;
        mWidthPx = pictureSize.width;
        
        mProjectionDirtyGL = true;
        mProjectionDirtyCV = true;
    }
    
    public void setClipDistances(float near, float far) {
        mNear = near;
        mFar = far;
        mProjectionDirtyGL = true;
    }
    
    public float[] getProjectionGL() {
        if (mProjectionDirtyGL) {
            final float top =
                    (float)Math.tan(mFOVY * Math.PI / 360f) * mNear;
            final float right =
                    (float)Math.tan(mFOVX * Math.PI / 360f) * mNear;
            Matrix.frustumM(mProjectionGL, 0,
                    -right, right, -top, top, mNear, mFar);
            mProjectionDirtyGL = false;
        }
        return mProjectionGL;
    }
    
    public MatOfDouble getProjectionCV() {
        if (mProjectionDirtyCV) {
            if (mProjectionCV == null) {
                mProjectionCV = new MatOfDouble();
                mProjectionCV.create(3, 3, CvType.CV_64FC1);
            }
            
            double diagonalPx = Math.sqrt(
                    (Math.pow(mWidthPx, 2.0) +
                    Math.pow(mHeightPx, 2.0)));
            double diagonalFOV = Math.sqrt(
                    (Math.pow(mFOVX, 2.0) +
                    Math.pow(mFOVY, 2.0)));
            double focalLengthPx = diagonalPx /
                    (2.0 * Math.tan(0.5 * diagonalFOV));
            
            mProjectionCV.put(0, 0, focalLengthPx);
            mProjectionCV.put(0, 1, 0.0);
            mProjectionCV.put(0, 2, 0.5 * mWidthPx);
            mProjectionCV.put(1, 0, 0.0);
            mProjectionCV.put(1, 1, focalLengthPx);
            mProjectionCV.put(1, 2, 0.5 * mHeightPx);
            mProjectionCV.put(2, 0, 0.0);
            mProjectionCV.put(2, 1, 0.0);
            mProjectionCV.put(2, 2, 0.0);
        }
        return mProjectionCV;
    }
}
