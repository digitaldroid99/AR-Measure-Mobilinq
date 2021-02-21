package com.nanosoft.mobilinq;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;
    private float upDistance = 0f;
    private ArFragment arFragment;
    private ModelRenderable andyRenderable;
    private AnchorNode myanchornode;
    private DecimalFormat form_numbers = new DecimalFormat("#0.00 m");

    private Anchor anchor1 = null, anchor2 = null;

    private HitResult myhit;

    private TextView text;
    private SeekBar sk_height_control;

    List<AnchorNode> anchorNodes = new ArrayList<>();

    private boolean measure_height = false;
    private ArrayList<String> arl_saved = new ArrayList<String>();

    private float fl_measurement = 0.0f;

    private String message;

    // Manage FloatingActinButtons
    FloatingActionButton mFabMenu, mFabWidth, mFabHeight, mFabCapture;
    boolean mAllFabsVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }
        try
        {
            this.getSupportActionBar().hide();
        }
        catch (NullPointerException e){}

        setContentView(R.layout.activity_main);

        // Manage floating action buttons
        mFabMenu = (FloatingActionButton) findViewById(R.id.fab_menu);
        mFabWidth = (FloatingActionButton) findViewById(R.id.fab_width);
        mFabHeight = (FloatingActionButton) findViewById(R.id.fab_height);
        mFabCapture = (FloatingActionButton) findViewById(R.id.fab_capture);

        mFabWidth.setVisibility(View.GONE);
        mFabHeight.setVisibility(View.GONE);
        mFabCapture.setVisibility(View.GONE);

        mAllFabsVisible = false;

        mFabMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mAllFabsVisible) {
                    mFabWidth.show();
                    mFabHeight.show();
                    mFabCapture.show();
                }else{
                    mFabWidth.hide();
                    mFabHeight.hide();
                    mFabCapture.hide();
                }
                mAllFabsVisible = !mAllFabsVisible;
            }
        });



        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        text = (TextView) findViewById(R.id.text);

        sk_height_control = (SeekBar) findViewById(R.id.sk_height_control);
        sk_height_control.setEnabled(false);

        mFabWidth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetLayout();
                measure_height = false;
                text.setText("Click the extremes you want to measure");
                mFabMenu.callOnClick();
            }
        });

        mFabHeight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetLayout();
                measure_height = true;
                text.setText("Click the base of the object you want to measure");
                mFabMenu.callOnClick();
            }
        });

        mFabCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(fl_measurement != 0.0f)
                    saveDialog();
                else
                    Toast.makeText(MainActivity.this, "Make a measurement before saving", Toast.LENGTH_SHORT).show();
                mFabMenu.callOnClick();
            }
        });



        sk_height_control.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                upDistance = progress;
                fl_measurement = progress/100f;
                text.setText("Height: "+form_numbers.format(fl_measurement));
                myanchornode.setLocalScale(new Vector3(1f, progress/10f, 1f));
                //ascend(myanchornode, upDistance);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ModelRenderable.builder()
                .setSource(this, R.raw.cubito3)
                .build()
                .thenAccept(renderable -> andyRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (andyRenderable == null) {
                        return;
                    }
                    myhit = hitResult;

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();

                    AnchorNode anchorNode = new AnchorNode(anchor);


                    anchorNode.setParent(arFragment.getArSceneView().getScene());


                    if(!measure_height) {
                        if(anchor2 != null){
                            emptyAnchors();
                        }
                        if (anchor1 == null) {
                            anchor1 = anchor;
                        } else {
                            anchor2 = anchor;
                            fl_measurement = getMetersBetweenAnchors(anchor1, anchor2);
                            text.setText("Width: " +
                                    form_numbers.format(fl_measurement));

                        }
                    }
                    else{
                        emptyAnchors();
                        anchor1 = anchor;
                        text.setText("Move the slider till the cube reaches the upper base");
                        sk_height_control.setEnabled(true);
                    }

                    myanchornode = anchorNode;
                    anchorNodes.add(anchorNode);

                    // Create the transformable andy and add it to the anchor.
                    TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
                    andy.setParent(anchorNode);
                    andy.setRenderable(andyRenderable);
                    andy.select();
                    andy.getScaleController().setEnabled(false);
                });

        // Request to write external storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[] {
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
        } else {

        }
    }

    /**
     * Function to raise an object perpendicular to the ArPlane a specific distance
     * @param an anchor belonging to the object that should be raised
     * @param up distance in centimeters the object should be raised vertically
     */
    private void ascend(AnchorNode an, float up) {
        Anchor anchor = myhit.getTrackable().createAnchor(
                myhit.getHitPose().compose(Pose.makeTranslation(0, up / 100f, 0)));

        an.setAnchor(anchor);
    }

    /**
     * Function to return the distance in meters between two objects placed in ArPlane
     * @param anchor1 first object's anchor
     * @param anchor2 second object's anchor
     * @return the distance between the two anchors in meters
     */
    private float getMetersBetweenAnchors(Anchor anchor1, Anchor anchor2) {
        float[] distance_vector = anchor1.getPose().inverse()
                .compose(anchor2.getPose()).getTranslation();
        float totalDistanceSquared = 0;
        for (int i = 0; i < 3; ++i)
            totalDistanceSquared += distance_vector[i] * distance_vector[i];
        return (float) Math.sqrt(totalDistanceSquared);
    }


    /**
     * Check whether the device supports the tools required to use the measurement tools
     * @param activity
     * @return boolean determining whether the device is supported or not
     */
    private boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    private void saveDialog() {
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);
        View mView = getLayoutInflater().inflate(R.layout.dialog_save, null);

        EditText et_measure = (EditText) mView.findViewById(R.id.et_measure);
        mBuilder.setTitle("Measurement title");

        mBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if(et_measure.length() != 0){
                    arl_saved.add(et_measure.getText()+": "+form_numbers.format(fl_measurement));
                    dialogInterface.dismiss();
                    takeScreenshotAR(et_measure.getText().toString());
                }
                else
                    Toast.makeText(MainActivity.this, "Title can't be empty", Toast.LENGTH_SHORT).show();
            }
        });

        mBuilder.setView(mView);
        AlertDialog dialog = mBuilder.create();

        dialog.show();
    }

    /**
     * Set layout to its initial state
     */
    private void resetLayout(){
        sk_height_control.setProgress(10);
        sk_height_control.setEnabled(false);
        measure_height = false;
        emptyAnchors();
    }

    private void emptyAnchors(){
        anchor1 = null;
        anchor2 = null;
        for (AnchorNode n : anchorNodes) {
            arFragment.getArSceneView().getScene().removeChild(n);
            n.getAnchor().detach();
            n.setParent(null);
            n = null;
        }
    }

    // Take screen of Main fragement Scene
    private void takeScreenshot(String filename) {
        try {
            // image naming and path  to include sd card  appending name you choose for file
            String mPath = Environment.getExternalStorageDirectory().toString() + "/" + filename + ".jpg";

            // create bitmap screen capture
            View v1 = getWindow().getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
            v1.setDrawingCacheEnabled(false);

            File imageFile = new File(mPath);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();

//            openScreenshot(imageFile);
        } catch (Throwable e) {
            // Several error may come out with file handling or DOM
            e.printStackTrace();
        }
    }

    // Take screen of AR fragement Scene
    private void takeScreenshotAR(String filename) {
        // image naming and path  to include sd card  appending name you choose for file
        String mPath = Environment.getExternalStorageDirectory().toString() + "/" + filename + ".jpg";

        ArSceneView view = arFragment.getArSceneView();

        // Create a bitmap the size of the scene view.
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);

        // Create a handler thread to offload the processing of the image.
        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
        handlerThread.start();
        // Make the request to copy.
        PixelCopy.request(view, bitmap, (copyResult) -> {
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    // Get bitmap for measurement text
                    View v1 = getWindow().getDecorView().getRootView().findViewById(R.id.text);
                    v1.setDrawingCacheEnabled(true);
                    Bitmap bitmapText = Bitmap.createBitmap(v1.getDrawingCache());
                    v1.setDrawingCacheEnabled(false);

                    Bitmap bitmapOverlay = overlay(bitmap, bitmapText);
                    saveBitmapToDisk(bitmapOverlay, filename);
                } catch (IOException e) {
                    Log.d(TAG, e.toString());
                    return;
                }
//                SnackbarUtility.showSnackbarTypeLong(settingsButton, "Screenshot saved in /Pictures/Screenshots");
                Toast.makeText(MainActivity.this, "Screenshot saved in /Pictures/Screenshots", Toast.LENGTH_LONG).show();
            } else {
//                SnackbarUtility.showSnackbarTypeLong(settingsButton, "Failed to take screenshot");
                Toast.makeText(MainActivity.this, "Failed to take screenshoot", Toast.LENGTH_LONG).show();
            }
            handlerThread.quitSafely();
        }, new Handler(handlerThread.getLooper()));
    }

    private Bitmap overlay(Bitmap bmp1, Bitmap bmp2) {
        Bitmap bmOverlay = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(bmp1, new Matrix(), null);
        canvas.drawBitmap(bmp2, new Matrix(), null);
        return bmOverlay;
    }

    public void saveBitmapToDisk(Bitmap bitmap, String filename) throws IOException {
        try{
            File videoDirectory = new File(Environment.getExternalStorageDirectory() + File.separator + "Screenshots");

            if(!videoDirectory.exists() && !videoDirectory.isDirectory())
                videoDirectory.mkdir();

            File mediaFile = new File(videoDirectory, filename+".jpeg");
            FileOutputStream fileOutputStream = new FileOutputStream(mediaFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            fileOutputStream.flush();
            fileOutputStream.close();
        }catch(Exception e){
            Toast.makeText(MainActivity.this, "Error writing screenshoot : " + e.toString(), Toast.LENGTH_SHORT).show();
        }

    }
}
