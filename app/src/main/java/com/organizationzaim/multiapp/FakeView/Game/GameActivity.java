package com.organizationzaim.multiapp.FakeView.Game;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


import com.organizationzaim.multiapp.FakeView.FakeActivity;
import com.organizationzaim.multiapp.FakeView.Game.DomainGame.GameView;
import com.organizationzaim.multiapp.R;

import java.util.HashMap;

public class GameActivity extends AppCompatActivity implements View.OnTouchListener, View.OnClickListener {
    public static boolean isLeftPressed = false;
    public static boolean isRightPressed = false;
    public static int count;
    private LinearLayout gameLayout;
    public static TextView tv_count;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        
        HashMap<String, Object> eventValues = new HashMap<>();
        eventValues.put("af_openApp", 1);

        GameView gameView = new GameView(this);

        gameLayout = findViewById(R.id.gameLayout);

        gameLayout.addView(gameView);
        Button leftButton  = findViewById(R.id.leftButton);
        Button rightButton = findViewById(R.id.rightButton);
        Button exit        = findViewById(R.id.bt_exit);

        leftButton.setOnTouchListener(this);
        rightButton.setOnTouchListener(this);
        exit.setOnClickListener(this);


    }
    public boolean onTouch(View button, MotionEvent motion) {
        switch(button.getId()) {
            case R.id.leftButton:
                switch (motion.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isLeftPressed = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        isLeftPressed = false;
                        break;
                }
                break;
            case R.id.rightButton:
                switch (motion.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isRightPressed = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        isRightPressed = false;
                        break;
                }
                break;
        }
        return true;
    }

    @Override
    public void onClick(View v) {

        Intent intent = new Intent(GameActivity.this, FakeActivity.class);
        startActivity(intent);
    }
}