package ru.deewend.walkietalkie;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import ru.deewend.walkietalkie.thread.WalkieTalkieThread;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("Главная");
        setContentView(R.layout.activity_main);
    }

    public void onConnectButtonPressed(View view) {
        onButtonPressed(true);
    }

    public void onCreateRoomButtonPressed(View view) {
        onButtonPressed(false);
    }

    private void onButtonPressed(boolean connect) {
        changeStateRecursive(false);

        String username = ((EditText) findViewById(R.id.username_edit_text))
                .getText().toString();
        WalkieTalkieThread thread = new WalkieTalkieThread(
                (connect ? WalkieTalkieThread.MODE_CONNECT :
                        WalkieTalkieThread.MODE_HOST_AND_CONNECT), username, this);
        WalkieTalkie.getInstance().linkWTThread(thread);
        thread.start();
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
