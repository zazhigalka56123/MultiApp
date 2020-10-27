package com.organizationzaim.multiapp.FakeView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.organizationzaim.multiapp.FakeView.Game.GameActivity;
import com.organizationzaim.multiapp.R;


public class FakeActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake);
        Button bt_start_game = findViewById(R.id.bt_start_game);
        bt_start_game.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.bt_start_game:
                Intent intent = new Intent(FakeActivity.this, GameActivity.class);
                startActivity(intent);
                break;
        }
    }
}