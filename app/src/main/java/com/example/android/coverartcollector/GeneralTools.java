package com.example.android.coverartcollector;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;


// implements methods that will be useful to many activities
public class GeneralTools {

    private Context myContext;
    private Vibrator vibrator;

    public final static int touchVibDelay = 50;

    GeneralTools(Context context) {
        myContext = context;
        vibrator = (Vibrator) myContext.getSystemService(Context.VIBRATOR_SERVICE);
    }

    // Got this vibration code from a stackoverflow explanation of using vibration
    // https://stackoverflow.com/questions/13950338/how-to-make-an-android-device-vibrate
    public void vibrate(int time) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(time, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            vibrator.vibrate(time);
        }
    }
}
