package com.organizationzaim.multiapp.FakeView.Game.DomainGame;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.view.SurfaceHolder;

import android.view.SurfaceView;

import androidx.annotation.RequiresApi;


import com.organizationzaim.multiapp.FakeView.FakeActivity;
import com.organizationzaim.multiapp.FakeView.Game.GameActivity;

import java.util.ArrayList;

public class GameView extends SurfaceView implements Runnable {
    public static int     maxX = 20;
    public static int     maxY = 20;
    public static float   unitW = 0;
    public static float   unitH = 0;

    private boolean       firstTime = true;
    public static boolean gameRunning = true;
    private Grandmother grandmother;
    private Thread gameThread = null;
    private Paint paint;
    private Canvas canvas;
    private SurfaceHolder surfaceHolder;

    private ArrayList<Stone> stones = new ArrayList<>();
    private final int           ASTEROID_INTERVAL = 50;
    private int                 currentTime = 0;
    public static int                 count;

    private GameActivity gameActivity;

    public GameView(GameActivity gameActivity) {
        super(gameActivity);
        this.gameActivity = gameActivity;
        maxX = 20;
        maxY = 40;
        unitW = 0;
        unitH = 0;
        firstTime = true;
        gameRunning = true;
        gameThread = null;
        stones = new ArrayList<>();
        currentTime = 0;
        surfaceHolder = getHolder();
        paint = new Paint();

        gameThread = new Thread(this);
        gameThread.start();
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void run() {
        count = 0;
        while (gameRunning) {
            update();
            draw();
            checkCollision();
            checkIfNewAsteroid();
            control();
        }
        gameRunning = false;
        exiting();

    }

    private void exiting() {
        try {
            paint.setAntiAlias(true);
            paint.setTextSize(100);
            paint.setColor(Color.WHITE);
            canvas.drawText("Game over",surfaceHolder.getSurfaceFrame().width() / 4,300,paint);
            Thread.sleep(2000);
            gameActivity.startActivity(new Intent(gameActivity, FakeActivity.class));
            gameActivity.finish();
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    private void update() {
        if(!firstTime) {
            grandmother.update();
            for (Stone stone : stones) {
                stone.update();
            }
        }
    }

    private void draw() {
        if (surfaceHolder.getSurface().isValid()) {

            if(firstTime){
                firstTime = false;
                unitW = surfaceHolder.getSurfaceFrame().width() / maxX;
                unitH = surfaceHolder.getSurfaceFrame().height() / maxY;
                grandmother = new Grandmother(getContext());
            }

            canvas = surfaceHolder.lockCanvas();
            float[] arr = new float[3];
            arr[0] = 26.0f;
            arr[1] = 72.0f;
            arr[2] = 81.0f;
            canvas.drawColor(Color.HSVToColor(arr));
            grandmother.draw(paint, canvas);
            paint.setAntiAlias(true);
            paint.setTextSize(100);
            paint.setColor(Color.WHITE);
            canvas.drawText("Score: " + count,surfaceHolder.getSurfaceFrame().width() / 4,100,paint);

            for(Stone stone : stones){
                stone.draw(paint, canvas);
            }

            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void control() {
        try {
            gameThread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void checkCollision(){
        for (Stone stone : stones) {
            if(stone.isCollision(grandmother.x, grandmother.y, grandmother.size)){
                gameRunning = false;
            }
        }
    }

    private void checkIfNewAsteroid(){
        if(currentTime >= ASTEROID_INTERVAL){
            Stone stone = new Stone(getContext());
            stones.add(stone);
            currentTime = 0;
        }else{
            currentTime ++;
        }
    }
}