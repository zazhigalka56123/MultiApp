package com.organizationzaim.multiapp.FakeView.Game.DomainGame;

import android.content.Context;

import com.organizationzaim.multiapp.FakeView.Game.GameActivity;
import com.organizationzaim.multiapp.R;


public class Grandmother extends GameBody {

    public Grandmother(Context context) {
        bitmapId = R.drawable.art_main;
        size = 5;
        x=7;
        y= GameView.maxY - size - 1;
        speed = (float) 0.2;

        init(context);
    }

    @Override
    public void update() {
        if(GameActivity.isLeftPressed && x >= 0){
            x -= speed;
        }
        if(GameActivity.isRightPressed && x <= GameView.maxX - 5){
            x += speed;
        }
    }
}
