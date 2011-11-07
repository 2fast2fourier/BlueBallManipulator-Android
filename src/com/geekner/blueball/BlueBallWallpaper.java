/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.geekner.blueball;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.geekner.blueball.GifDecoder.GifFrame;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowManager;

public class BlueBallWallpaper extends WallpaperService {
	private static final String TAG = "BlueBallWall";
    public static final String SHARED_PREFS_NAME="blueballsettings";
    private GifFrame[][] dA;
    private int length;
    private int maxImg;
    
    private AsyncLoad proc;
    
    private boolean randomize;

    @Override
    public void onCreate() {
        super.onCreate();
        loadImages();
    }
    
    private void loadImages(){
    	PreferenceManager.setDefaultValues(this, SHARED_PREFS_NAME, 0, R.xml.blueball_settings, false);
        SharedPreferences pref = getSharedPreferences(SHARED_PREFS_NAME, 0);
        randomize = pref.getBoolean("blueball_randomize", true);
        maxImg = pref.getInt("blueball_max", 0);
        WallpaperManager wm = WallpaperManager.getInstance(this);
        int sx,sy;
        if(wm.getDesiredMinimumWidth() <= 0 || wm.getDesiredMinimumHeight() <=0){
        	Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        	sx = (display.getWidth()/100);
        	sy = (display.getHeight()/100);
        }else{
        	sx = (wm.getDesiredMinimumWidth()/100);
            sy = (wm.getDesiredMinimumHeight()/100);
        }
        dA = new GifFrame[sx*sy][];
        Log.v(TAG,"Target Size: "+sx+" x "+sy);
        if(proc != null){
        	proc.cancel(false);
        }
        proc = new AsyncLoad();
        if(maxImg >0){
            proc.execute(Math.min(maxImg,sx*sy));//if they specify more than a screen will hold, only use what you need.
        }else{
        	proc.execute(sx*sy);
        }
    }
    
    private class AsyncLoad extends AsyncTask<Integer, GifFrame[], Void>{

		@Override
		protected Void doInBackground(Integer... params) {
	        AssetManager am = getAssets();
	        String[] list;
			try {
				list = am.list("balls");
				List<String> imageList = Arrays.asList(list);
				if(randomize){
					Collections.shuffle(imageList);
				}
		        Log.v(TAG, "files: "+imageList.size());
		        int max = params[0].intValue();
				for(int x = 0; x < imageList.size() && x < max && !isCancelled();x++){
					String st = imageList.get(x);
			        Log.v(TAG, "loading: "+st);
			        GifFrame[] img = GifDecoder.decodeGif(am.open("balls/"+st), Bitmap.Config.ARGB_4444);
			        if(img != null){
			        	publishProgress(img);
			        }else{
			        	break;
			        }
		        }
			} catch (Exception e) {
				Log.e(TAG, Log.getStackTraceString(e));
			}
			return null;
		}
		
		@Override
		protected void onProgressUpdate(GifFrame[]... progress) {
			if(!isCancelled() && length != dA.length && progress != null && progress.length > 0 && progress[0] != null){
		        dA[length] = progress[0];
				length++;
			}
	    }
		
		@Override
		protected void onPostExecute(Void result){
			if(!isCancelled()){
				proc = null;
			}
		}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public Engine onCreateEngine() {
        return new BallEngine();
    }

    class BallEngine extends Engine 
        implements SharedPreferences.OnSharedPreferenceChangeListener {

        private final Handler mHandler = new Handler();

        private final Runnable mDrawBall = new Runnable() {
            public void run() {
                drawFrame();
            }
        };
        private boolean mVisible;
        private SharedPreferences mPrefs;

		private long delay = 80;

		private int count = 0;
		private int overMask = 0;
		
		private Paint overlay;
		private Paint background;

		BallEngine() {
            mPrefs = BlueBallWallpaper.this.getSharedPreferences(SHARED_PREFS_NAME, 0);
            mPrefs.registerOnSharedPreferenceChangeListener(this);
            onSharedPreferenceChanged(mPrefs, null);
            background = new Paint();
            background.setColor(0xFFFFFFFF);
        }

        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        	overMask = prefs.getInt("blueball_mask", 0);
            overlay = new Paint();
            overlay.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
            overlay.setARGB(overMask, 0, 0, 0);
        	if(key != null && key.equals("blueball_randomize") && prefs.getBoolean("blueball_randomize", true) != randomize){
        		randomize = prefs.getBoolean("blueball_randomize", true);
        		Log.d(TAG,"Resetting Wallpaper");
        		length = 0;
        		dA = null;
        		loadImages();
        	}
        	if(key != null && key.equals("blueball_max") && prefs.getInt("blueball_max", 0) != maxImg){
            	maxImg = prefs.getInt("blueball_max", 0);
            	Log.d(TAG,"Resetting Wallpaper");
        		length = 0;
        		dA = null;
        		loadImages();
        	}
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mHandler.removeCallbacks(mDrawBall);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            if (visible) {
                drawFrame();
            } else {
                mHandler.removeCallbacks(mDrawBall);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            //drawFrame();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            mVisible = false;
            mHandler.removeCallbacks(mDrawBall);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                float xStep, float yStep, int xPixels, int yPixels) {
            //drawFrame();
        }

        void drawFrame() {
            final SurfaceHolder holder = getSurfaceHolder();
            final Rect frame = holder.getSurfaceFrame();
            final int width = frame.width();
            final int height = frame.height();

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    // draw something
                    drawBall(c, width, height);
                }
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }

            mHandler.removeCallbacks(mDrawBall);
            if (mVisible) {
                mHandler.postDelayed(mDrawBall, delay);
            }
        }

        void drawBall(Canvas c, int width, int height) {
            c.save();
            //c.drawColor(0xffffffff);
            c.drawPaint(background);
            //draw stuff here
            int x = 0;
            int y = 0;
            if(length == 0){
            	c.restore();
            	return;
            }
            for(int ix = 0;y<height;ix++){
                c.drawBitmap(dA[ix%length][count].image, x, y, null);
            	if(x + 100 > width){
            		y+=100;
            		x=0;
            	}else{
            		x+=100;
            	}
            }
            c.drawPaint(overlay);
            count++;
            if(count >= dA[0].length){
            	count = 0;
            }
            c.restore();
        }
    }
}
