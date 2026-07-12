package com.chessfantasy.israel;

import android.app.Application;

import com.chessfantasy.israel.data.GameRepository;

public class ChessFantasyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        GameRepository.init(this);
    }
}
