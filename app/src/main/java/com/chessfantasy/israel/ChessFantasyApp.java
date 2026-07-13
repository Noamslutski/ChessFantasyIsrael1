package com.chessfantasy.israel;

import android.app.Application;

import com.chessfantasy.israel.data.FirebaseGateway;
import com.chessfantasy.israel.data.GameRepository;

public class ChessFantasyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        GameRepository.init(this);
        // Optional backend — a no-op unless google-services.json is present.
        FirebaseGateway.get().init(this);
        FirebaseGateway.get().signIn(() -> GameRepository.get().syncRemote(true));
    }
}
