package com.organizationzaim.multiapp.FakeView.Game.DomainGame;

import android.content.Context;

import com.organizationzaim.multiapp.R;

import java.util.Random;

public class Stone extends GameBody {
    private int radius = 1;
    private float minSpeed = (float) 0.1;
    private float maxSpeed = (float) 0.5;
    private int[] Arr = {R.drawable.berry, R.drawable.seven, R.drawable.star, R.drawable.xxxx, R.drawable.vino};
    public Stone(Context context) {
        Random random = new Random();
        bitmapId = Arr[random.nextInt(Arr.length)];
        y = 0;

        x = random.nextInt(GameView.maxX) - radius;
        size = radius * 2;
        speed = minSpeed + (maxSpeed - minSpeed) * random.nextFloat();
        init(context);
        GameView.count += 1;
    }

    @Override
    public void update() {
        y += speed;
    }

    public boolean isCollision(float shipX, float shipY, float shipSize) {
        return !(((x+size) < shipX)||(x > (shipX+shipSize))||((y+size) < shipY)||(y > (shipY+shipSize)));
    }
}
