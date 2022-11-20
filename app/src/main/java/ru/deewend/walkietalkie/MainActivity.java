package ru.deewend.walkietalkie;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import ru.deewend.walkietalkie.thread.WalkieTalkieThread;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 200;

    private static boolean SHOULD_CREATE_LOCATION_LISTENER = true;

    private boolean connect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("Главная");
        setContentView(R.layout.activity_main);
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                grantResults[2] == PackageManager.PERMISSION_GRANTED
        ) {
            if (SHOULD_CREATE_LOCATION_LISTENER) {
                LocationManager locationManager =
                        (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                LocationListener locationListener = new WalkieTalkie.WTLocationListener();
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 2500, 0, locationListener);

                SHOULD_CREATE_LOCATION_LISTENER = false;
            }

            String username = ((EditText) findViewById(R.id.username_edit_text))
                    .getText().toString();
            WalkieTalkieThread thread = new WalkieTalkieThread(
                    (connect ? WalkieTalkieThread.MODE_CONNECT :
                            WalkieTalkieThread.MODE_HOST_AND_CONNECT), username, this);
            WalkieTalkie.getInstance().linkWTThread(thread);
            thread.start();
        } else {
            Toast.makeText(this, "Приложению необходмы права на использование " +
                    "микрофона и получение геолокации для корректной работы", Toast.LENGTH_LONG).show();
        }
    }

    public void onConnectButtonPressed(View view) {
        onButtonPressed(true);
    }

    public void onCreateRoomButtonPressed(View view) {
        onButtonPressed(false);
    }

    private void onButtonPressed(boolean connect) {
        changeStateRecursive(false);

        this.connect = connect;
        ActivityCompat.requestPermissions(this,
                new String[] {
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST_CODE);
    }

    public void changeStateRecursive(boolean enable) {
        changeStateRecursive(findViewById(R.id.main_activity_layout), enable);
    }

    public void changeStateRecursive(ViewGroup parent, boolean enable) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                changeStateRecursive((ViewGroup) child, enable);
            } else {
                child.setEnabled(enable);
            }
        }
    }
}
