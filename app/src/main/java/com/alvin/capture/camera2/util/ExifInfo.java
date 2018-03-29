package com.alvin.capture.camera2.util;

/**
 * Created by Administrator on 2015/10/19.
 */
public class ExifInfo {

    public final int rotation;
    public final boolean flipHorizontal;

    public ExifInfo() {
        this.rotation = 0;
        this.flipHorizontal = false;
    }

    public ExifInfo(int rotation, boolean flipHorizontal) {
        this.rotation = rotation;
        this.flipHorizontal = flipHorizontal;
    }
}
