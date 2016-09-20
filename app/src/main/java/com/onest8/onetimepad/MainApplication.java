package com.onest8.onetimepad;

import android.app.Application;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        PRNGFixes.apply();
    }
}
