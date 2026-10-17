package com.example.liquidprank;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Launcher screen. Explains the gag and the exit gestures, then starts the
 * effect after a short countdown so you have time to hand the phone over.
 */
public class MainActivity extends Activity {

    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button startButton = findViewById(R.id.startButton);
        final TextView status = findViewById(R.id.status);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startButton.setEnabled(false);
                timer = new CountDownTimer(3000, 1000) {
                    @Override
                    public void onTick(long msLeft) {
                        status.setText(getString(R.string.countdown, (msLeft / 1000) + 1));
                    }

                    @Override
                    public void onFinish() {
                        startActivity(new Intent(MainActivity.this, PrankActivity.class));
                        status.setText("");
                        startButton.setEnabled(true);
                    }
                }.start();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }
}
