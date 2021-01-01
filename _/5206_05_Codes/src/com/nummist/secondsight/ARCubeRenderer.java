package com.nummist.secondsight;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import com.nummist.secondsight.adapters.CameraProjectionAdapter;
import com.nummist.secondsight.filters.ar.ARFilter;

import android.opengl.GLSurfaceView;

public class ARCubeRenderer implements GLSurfaceView.Renderer {
    
    public ARFilter filter;
    public CameraProjectionAdapter cameraProjectionAdapter;
    public float scale = 100f;
    
    private static final ByteBuffer VERTICES;
    private static final ByteBuffer COLORS;
    private static final ByteBuffer TRIANGLE_FAN_0;
    private static final ByteBuffer TRIANGLE_FAN_1;
    
    static {
        VERTICES = ByteBuffer.allocateDirect(96);
        VERTICES.order(ByteOrder.nativeOrder());
        VERTICES.asFloatBuffer().put(new float[] {
                -1f,  1f,  1f,
                 1f,  1f,  1f,
                 1f, -1f,  1f,
                -1f, -1f,  1f,
                
                -1f,  1f, -1f,
                 1f,  1f, -1f,
                 1f, -1f, -1f,
                -1f, -1f, -1f
        });
        VERTICES.position(0);
        
        COLORS = ByteBuffer.allocateDirect(32);
        COLORS.put(new byte[] {
                Byte.MAX_VALUE, Byte.MAX_VALUE, 0, Byte.MAX_VALUE, // yellow
                0, Byte.MAX_VALUE, Byte.MAX_VALUE, Byte.MAX_VALUE, // cyan
                0, 0, 0, Byte.MAX_VALUE, // black
                Byte.MAX_VALUE, 0, Byte.MAX_VALUE, Byte.MAX_VALUE, // magenta
                
                Byte.MAX_VALUE, 0, 0, Byte.MAX_VALUE, // red
                0, Byte.MAX_VALUE, 0, Byte.MAX_VALUE, // green
                0, 0, Byte.MAX_VALUE, Byte.MAX_VALUE, // blue
                0, 0, 0, Byte.MAX_VALUE // black
        });
        COLORS.position(0);
        
        TRIANGLE_FAN_0 = ByteBuffer.allocate(18);
        TRIANGLE_FAN_0.put(new byte[] {
                1, 0, 3,
                1, 3, 2,
                1, 2, 6,
                1, 6, 5,
                1, 5, 4,
                1, 4, 0
        });
        TRIANGLE_FAN_0.position(0);
        
        TRIANGLE_FAN_1 = ByteBuffer.allocate(18);
        TRIANGLE_FAN_1.put(new byte[] {
                7, 4, 5,
                7, 5, 6,
                7, 6, 2,
                7, 2, 3,
                7, 3, 0,
                7, 0, 4
        });
        TRIANGLE_FAN_1.position(0);
    }
    
    @Override
    public void onDrawFrame(final GL10 gl) {
        
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT |
                GL10.GL_DEPTH_BUFFER_BIT);
        gl.glClearColor(0f, 0f, 0f, 0f); // transparent
        
        if (filter == null) {
            return;
        }
        
        if (cameraProjectionAdapter == null) {
            return;
        }
        
        float[] pose = filter.getGLPose();
        if (pose == null) {
            return;
        }
        
        gl.glMatrixMode(GL10.GL_PROJECTION);
        float[] projection =
                cameraProjectionAdapter.getProjectionGL();
        gl.glLoadMatrixf(projection, 0);
        
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadMatrixf(pose, 0);
        gl.glTranslatef(0f, 0f, 1f);
        gl.glScalef(scale, scale, scale);
        
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        
        gl.glVertexPointer(3, GL11.GL_FLOAT, 0, VERTICES);
        gl.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 0, COLORS);
        
        gl.glDrawElements(GL10.GL_TRIANGLE_FAN, 18,
                GL10.GL_UNSIGNED_BYTE, TRIANGLE_FAN_0);
        gl.glDrawElements(GL10.GL_TRIANGLE_FAN, 18,
                GL10.GL_UNSIGNED_BYTE, TRIANGLE_FAN_1);
    }
    
    @Override
    public void onSurfaceChanged(final GL10 gl, final int width,
            final int height) {
    }
    
    @Override
    public void onSurfaceCreated(final GL10 arg0,
            final EGLConfig config) {
    }
}
