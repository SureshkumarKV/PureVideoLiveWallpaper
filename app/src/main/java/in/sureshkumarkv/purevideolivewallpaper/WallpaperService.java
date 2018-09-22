package in.sureshkumarkv.purevideolivewallpaper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.Surface;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import in.sureshkumarkv.renderlib.AndroidUtil;
import in.sureshkumarkv.renderlib.RbCamera;
import in.sureshkumarkv.renderlib.RbGeometry;
import in.sureshkumarkv.renderlib.RbInstance;
import in.sureshkumarkv.renderlib.RbListener;
import in.sureshkumarkv.renderlib.RbMaterial;
import in.sureshkumarkv.renderlib.RbMesh;
import in.sureshkumarkv.renderlib.RbNode;
import in.sureshkumarkv.renderlib.RbScene;
import in.sureshkumarkv.renderlib.RbShader;
import in.sureshkumarkv.renderlib.RbWorld;
import in.sureshkumarkv.renderlib.geometry.RbRectangleGeometry;
import in.sureshkumarkv.renderlib.types.RbMatrix;
import in.sureshkumarkv.renderlib.wallpaper.RbWallpaperService;

/**
 * Created by SureshkumarKV on 17/09/2018.
 */

public class WallpaperService extends RbWallpaperService {
    public WallpaperService() {
        super(WallpaperEngine.class);
    }

    public static class WallpaperEngine implements RbWallpaperEngine, SharedPreferences.OnSharedPreferenceChangeListener, SurfaceTexture.OnFrameAvailableListener{
        private static int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
        private Object LOCK = new Object();

        private Context mApplicationContext;
        private RbInstance mInstance;
        private RbCamera mCamera;

        private int mRotation;
        private MediaPlayer mMediaPlayer;
        private SurfaceTexture mSurface;
        private boolean mUpdateFlag;
        private boolean mReloadFlag;
        private float mVolume;

        @Override
        public void onCreate(GLSurfaceView glSurfaceView) {
            mApplicationContext = glSurfaceView.getContext().getApplicationContext();

            File file = new File(mApplicationContext.getFilesDir() + "/video");
            if(!file.exists()){
                try {
                    BufferedInputStream input = new BufferedInputStream(mApplicationContext.getAssets().open("video/waves.mp4"));
                    BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(mApplicationContext.getFilesDir()+"/video"));

                    int bytesRead;
                    byte[] buffer = new byte[1024];
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }

                    input.close();
                    output.close();

                }catch (Exception e){
                    e.printStackTrace();
                }
            }

            mInstance = new RbInstance();
            RbWorld world = new RbWorld();
            RbScene scene = new RbScene();
            final RbNode node = new RbNode();
            RbMesh mesh = new RbMesh();
            RbGeometry geometry = new RbRectangleGeometry(1, 1, 0, 0, 1, 1, true);
            RbMaterial mMaterial = new RbMaterial();
            final RbShader shader = new RbShader(mInstance, AndroidUtil.getAssetFile(glSurfaceView.getContext(), "shader/purevideo.vs.glsl"), AndroidUtil.getAssetFile(glSurfaceView.getContext(), "shader/purevideo.fs.glsl"));
            mCamera = new RbCamera();

            mMaterial.setShader(shader);
            mesh.setMaterial(mMaterial);
            mesh.addGeometry(geometry);
            node.addMesh(mesh);
            scene.setRootNode(node);
            scene.setCamera(mCamera);
            world.addScene(scene);
            world.setBackgroundColor(0xff0000ff);

            mInstance.setWorld(world);
            mInstance.setResolution(1);
            mInstance.setFPS(50);
            mInstance.setView(glSurfaceView);
            mInstance.setListener(new RbListener() {
                @Override
                public void onCreateWorld(RbWorld world){
                    int[] textures = new int[1];
                    if(textures[0] != 0){
                        GLES30.glDeleteTextures(1, textures, 0);
                    }
                    GLES30.glGenTextures(1, textures, 0);
                    GLES30.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textures[0]);
                    GLES30.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
                    GLES30.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);

                    mSurface = new SurfaceTexture(textures[0]);
                    mSurface.setOnFrameAvailableListener(WallpaperEngine.this);
                    Surface surface = new Surface(mSurface);
                    mMediaPlayer.setSurface(surface);
                    surface.release();

                    synchronized(LOCK) {
                        mUpdateFlag = false;
                    }
                }

                @Override
                public void onSetupWorld(boolean isRecursive, int width, int height, RbInstance instance, RbWorld world) {
                    node.getTransform().reset();
                    if(mMediaPlayer.getVideoHeight()*width/mMediaPlayer.getVideoWidth() > height){
                        node.getTransform().scale(width, width*mMediaPlayer.getVideoHeight()/mMediaPlayer.getVideoWidth(), 1);
                    }else{
                        node.getTransform().scale(height*mMediaPlayer.getVideoWidth()/mMediaPlayer.getVideoHeight(), height, 1);
                    }
                    node.getTransform().rotate(mRotation, 0,0,-1);

                    mCamera.setLookAt(0,0,5, 0,0, 0, 0,1,0);
                    mCamera.setOrthographic(width, height, 0.1f, 100);

                    synchronized(LOCK) {
                        mUpdateFlag = true;
                    }
                }

                @Override
                public void onRenderGeometry(int width, int height, RbInstance instance, RbWorld world, RbScene scene, RbNode node, RbMatrix transform, RbMesh mesh, RbGeometry geometry, RbMaterial material, long deltaTimeNanos) {
                    synchronized(LOCK) {
                        if(mReloadFlag){
                            startPlayer();
                            onCreateWorld(world);
                            onSetupWorld(false, width, height, instance, world);
                            mReloadFlag = false;
                        }

                        if (mUpdateFlag) {
                            mSurface.updateTexImage();
                            mUpdateFlag = false;
                        }
                    }
                }
            });

            handlePreferences();

            startPlayer();
        }

        private void startPlayer(){
            try {
                File file = new File(mApplicationContext.getFilesDir() + "/video");

                MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                metadataRetriever.setDataSource(file.getAbsolutePath());
                String rotationString = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                mRotation = rotationString == null ? 0 : Integer.parseInt(rotationString);

                if (mMediaPlayer != null) {
                    mMediaPlayer.release();
                }
                mMediaPlayer = MediaPlayer.create(mApplicationContext, Settings.System.DEFAULT_RINGTONE_URI);
                mMediaPlayer.reset();
                mMediaPlayer.setLooping(true);
                mMediaPlayer.setVolume(mVolume/100.0f, mVolume/100.0f);
                mMediaPlayer.setDataSource(file.getAbsolutePath());
                mMediaPlayer.prepare();
                mMediaPlayer.start();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onResize(int width, int height) {

        }

        @Override
        public void onTouch(MotionEvent event) {

        }

        @Override
        public void onOffset(float xOffset, float yOffset) {

        }

        @Override
        public void onShown() {
            mInstance.setActive(true);
            mMediaPlayer.start();
        }

        @Override
        public void onHidden() {
            mMediaPlayer.pause();
            mInstance.setActive(false);
        }

        @Override
        public void onDestroy() {
            if(mMediaPlayer != null){
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("fps".equals(key)) {
                mInstance.setFPS(5 + 55 * sharedPreferences.getInt(key, 50) / 100);

            }else if ("resolution".equals(key)) {
                mInstance.setResolution(0.1f + 0.9f * sharedPreferences.getInt(key, 100) / 100.0f);

            }else if ("source".equals(key)) {
                synchronized (LOCK){
                    mReloadFlag = true;
                }

            }else if ("volume".equals(key)) {
                synchronized (LOCK){
                    mVolume = sharedPreferences.getInt("volume", 0);
                    if(mMediaPlayer != null){
                        mMediaPlayer.setVolume(mVolume/100.0f, mVolume/100.0f);
                    }
                }

            }else if ("promotion".equals(key)) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("market://dev?id=7155163669022573453"));
                    mApplicationContext.startActivity(intent);
                }catch (Exception e){
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("https://play.google.com/store/apps/developer?id=Sureshkumar+K+V"));
                    mApplicationContext.startActivity(intent);
                }

            }else if ("reset".equals(key)) {
                PreferenceManager.getDefaultSharedPreferences(mApplicationContext).edit()
                        .putInt("fps", 50)
                        .putInt("resolution", 100)
                        .putInt("volume", 0)
                        .commit();

                try {
                    BufferedInputStream input = new BufferedInputStream(mApplicationContext.getAssets().open("video/waves.mp4"));
                    BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(mApplicationContext.getFilesDir()+"/video"));

                    int bytesRead;
                    byte[] buffer = new byte[1024];
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }

                    input.close();
                    output.close();

                }catch (Exception e){
                    e.printStackTrace();
                }

                synchronized (LOCK){
                    mReloadFlag = true;
                }
            }
        }

        private void handlePreferences() {
            SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mApplicationContext);
            mSharedPreferences.registerOnSharedPreferenceChangeListener(this);

            int fps = mSharedPreferences.getInt("fps", 50);
            int resolution = mSharedPreferences.getInt("resolution", 100);
            int volume = mSharedPreferences.getInt("volume", 0);

            mSharedPreferences.edit()
                    .putInt("fps", fps)
                    .putInt("resolution", resolution)
                    .putInt("volume", volume)
                    .commit();

            onSharedPreferenceChanged(mSharedPreferences, "fps");
            onSharedPreferenceChanged(mSharedPreferences, "resolution");
            onSharedPreferenceChanged(mSharedPreferences, "volume");
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            synchronized (LOCK){
                mUpdateFlag = true;
            }
        }
    }
}
