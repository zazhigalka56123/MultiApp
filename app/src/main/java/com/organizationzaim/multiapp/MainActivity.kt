package com.organizationzaim.multiapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.organizationzaim.multiapp.FakeView.Game.GameActivity


class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val intent = Intent(this, GameActivity::class.java)
        startActivity(intent)
    }
}
