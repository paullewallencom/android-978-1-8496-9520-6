package com.nummist.secondsight.filters.ar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.KeyPoint;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.content.Context;

import com.nummist.secondsight.adapters.CameraProjectionAdapter;

public class ImageDetectionFilter implements ARFilter {
    
    private final Mat mReferenceImage;
    private final MatOfKeyPoint mReferenceKeypoints =
            new MatOfKeyPoint();
    private final Mat mReferenceDescriptors = new Mat();
    private final Mat mReferenceCorners =
            new Mat(4, 1, CvType.CV_32FC2);
    
    private final MatOfKeyPoint mSceneKeypoints =
            new MatOfKeyPoint();
    private final Mat mSceneDescriptors = new Mat();
    
    private final Mat mGraySrc = new Mat();
    private final MatOfDMatch mMatches = new MatOfDMatch();
    
    private final FeatureDetector mFeatureDetector =
            FeatureDetector.create(FeatureDetector.STAR);
    private final DescriptorExtractor mDescriptorExtractor =
            DescriptorExtractor.create(DescriptorExtractor.FREAK);
    private final DescriptorMatcher mDescriptorMatcher =
            DescriptorMatcher.create(
                    DescriptorMatcher.BRUTEFORCE_HAMMING);
    
    private final MatOfDouble mDistCoeffs = new MatOfDouble(
            0.0, 0.0, 0.0, 0.0);
    
    private final CameraProjectionAdapter mCameraProjectionAdapter;
    private final MatOfDouble mRVec = new MatOfDouble();
    private final MatOfDouble mTVec = new MatOfDouble();
    private final MatOfDouble mRotation = new MatOfDouble();
    private final float[] mGLPose = new float[16];
    
    private boolean mTargetFound = false;
    
    public ImageDetectionFilter(final Context context,
            final int referenceImageResourceID,
            final CameraProjectionAdapter cameraProjectionAdapter)
                    throws IOException {
        
        mReferenceImage = Utils.loadResource(context,
                referenceImageResourceID,
                Highgui.CV_LOAD_IMAGE_COLOR);
        
        final Mat referenceImageGray = new Mat();
        Imgproc.cvtColor(mReferenceImage, referenceImageGray,
                Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(mReferenceImage, mReferenceImage,
                Imgproc.COLOR_BGR2RGBA);
        
        mReferenceCorners.put(0, 0,
                new double[] {0.0, 0.0});
        mReferenceCorners.put(1, 0,
                new double[] {referenceImageGray.cols(), 0.0});
        mReferenceCorners.put(2, 0,
                new double[] {referenceImageGray.cols(),
                        referenceImageGray.rows()});
        mReferenceCorners.put(3, 0,
                new double[] {0.0, referenceImageGray.rows()});
        
        mFeatureDetector.detect(referenceImageGray,
                mReferenceKeypoints);
        mDescriptorExtractor.compute(referenceImageGray,
                mReferenceKeypoints, mReferenceDescriptors);
        
        mCameraProjectionAdapter = cameraProjectionAdapter;
    }
    
    @Override
    public float[] getGLPose() {
        return (mTargetFound ? mGLPose : null);
    }
    
    @Override
    public void apply(final Mat src, final Mat dst) {
        Imgproc.cvtColor(src, mGraySrc, Imgproc.COLOR_RGBA2GRAY);
        
        mFeatureDetector.detect(mGraySrc, mSceneKeypoints);
        mDescriptorExtractor.compute(mGraySrc, mSceneKeypoints,
                mSceneDescriptors);
        mDescriptorMatcher.match(mSceneDescriptors,
                mReferenceDescriptors, mMatches);
        
        findPose();
        draw(src, dst);
    }
    
    private void findPose() {
        
        List<DMatch> matchesList = mMatches.toList();
        if (matchesList.size() < 4) {
            // There are too few matches to find the pose.
            return;
        }
        
        List<KeyPoint> referenceKeypointsList =
                mReferenceKeypoints.toList();
        List<KeyPoint> sceneKeypointsList =
                mSceneKeypoints.toList();
        
        // Calculate the max and min distances between keypoints.
        double maxDist = 0.0;
        double minDist = Double.MAX_VALUE;
        for(DMatch match : matchesList) {
            double dist = match.distance;
            if (dist < minDist) {
                minDist = dist;
            }
            if (dist > maxDist) {
                maxDist = dist;
            }
        }
        
        if (minDist > 50.0) {
            // The target is completely lost.
            mTargetFound = false;
            return;
        } else if (minDist > 25.0) {
            // The target is lost but maybe it is still close.
            // Keep using any previously found pose.
            return;
        }
        
        // Identify "good" keypoints based on match distance.
        List<Point3> goodReferencePointsList =
                new ArrayList<Point3>();
        ArrayList<Point> goodScenePointsList =
                new ArrayList<Point>();
        double maxGoodMatchDist = 1.75 * minDist;
        for(DMatch match : matchesList) {
            if (match.distance < maxGoodMatchDist) {
                Point point =
                        referenceKeypointsList.get(match.trainIdx).pt;
                Point3 point3 = new Point3(point.x, point.y, 0.0);
                goodReferencePointsList.add(point3);
                goodScenePointsList.add(
                        sceneKeypointsList.get(match.queryIdx).pt);
            }
        }
        
        if (goodReferencePointsList.size() < 4 ||
                goodScenePointsList.size() < 4) {
            // There are too few good points to find the pose.
            return;
        }
        
        MatOfPoint3f goodReferencePoints = new MatOfPoint3f();
        goodReferencePoints.fromList(goodReferencePointsList);
        
        MatOfPoint2f goodScenePoints = new MatOfPoint2f();
        goodScenePoints.fromList(goodScenePointsList);
        
        MatOfDouble projection =
                mCameraProjectionAdapter.getProjectionCV();
        Calib3d.solvePnP(goodReferencePoints, goodScenePoints,
                projection, mDistCoeffs, mRVec, mTVec);
        
        double[] rVecArray = mRVec.toArray();
        rVecArray[1] *= -1.0;
        rVecArray[2] *= -1.0;
        mRVec.fromArray(rVecArray);
        
        Calib3d.Rodrigues(mRVec, mRotation);
        
        double[] tVecArray = mTVec.toArray();
        
        mGLPose[0]  =  (float)mRotation.get(0, 0)[0];
        mGLPose[1]  =  (float)mRotation.get(1, 0)[0];
        mGLPose[2]  =  (float)mRotation.get(2, 0)[0];
        mGLPose[3]  =  0f;
        mGLPose[4]  =  (float)mRotation.get(0, 1)[0];
        mGLPose[5]  =  (float)mRotation.get(1, 1)[0];
        mGLPose[6]  =  (float)mRotation.get(2, 1)[0];
        mGLPose[7]  =  0f;
        mGLPose[8]  =  (float)mRotation.get(0, 2)[0];
        mGLPose[9]  =  (float)mRotation.get(1, 2)[0];
        mGLPose[10] =  (float)mRotation.get(2, 2)[0];
        mGLPose[11] =  0f;
        mGLPose[12] =  (float)tVecArray[0];
        mGLPose[13] = -(float)tVecArray[1];
        mGLPose[14] = -(float)tVecArray[2];
        mGLPose[15] =  1f;
        
        mTargetFound = true;
    }
    
    protected void draw(Mat src, Mat dst) {
        
        if (dst != src) {
            src.copyTo(dst);
        }
        
        if (!mTargetFound) {
            // The target has not been found.
            
            // Draw a thumbnail of the target in the upper-left
            // corner so that the user knows what it is.
            
            int height = mReferenceImage.height();
            int width = mReferenceImage.width();
            int maxDimension = Math.min(dst.width(),
                    dst.height()) / 2;
            double aspectRatio = width / (double)height;
            if (height > width) {
                height = maxDimension;
                width = (int)(height * aspectRatio);
            } else {
                width = maxDimension;
                height = (int)(width / aspectRatio);
            }
            Mat dstROI = dst.submat(0, height, 0, width);
            Imgproc.resize(mReferenceImage, dstROI, dstROI.size(),
                    0.0, 0.0, Imgproc.INTER_AREA);
        }
    }
}
