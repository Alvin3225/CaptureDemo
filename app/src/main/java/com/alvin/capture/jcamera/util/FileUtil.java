package com.alvin.capture.jcamera.util;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {
    private static final String TAG = "ID";

    public static String saveBitmap(Bitmap b) {
        String dir = ContentValue.PATH + "Camera";
        long dataTake = System.currentTimeMillis();
        String jpegName = dir + File.separator + "picture_" + dataTake + ".jpg";
        try {
            FileOutputStream fout = new FileOutputStream(jpegName);
            BufferedOutputStream bos = new BufferedOutputStream(fout);
            b.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
            return jpegName;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static boolean deleteFile(String url) {
        boolean result = false;
        File file = new File(url);
        if (file.exists()) {
            result = file.delete();
        }
        return result;
    }

    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * 删除文件
     * @param path 文件的路径
     */
    public static void delFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return;
        }
        file.delete();
    }
    public static String createFilePath(String folder, String subfolder, String uniqueId) {
        File dir = new File( folder);
        if (subfolder != null) {
            dir = new File(dir, subfolder);
        }
        dir.mkdirs();
        String fileName = ContentValue.FILE_START_NAME + uniqueId + ContentValue.VIDEO_EXTENSION;
        return new File(dir,fileName).getAbsolutePath();
    }
    /**
     * 判断传入的地址是否已经有这个文件夹，没有的话需要创建
     */
    public static void createSavePath(String path){
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
    }
    public static boolean  getVideoWH(String path,Intent intent){
        MediaMetadataRetriever retr = new MediaMetadataRetriever();
        retr.setDataSource(path);
        String height = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT); // 视频高度
        String width = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); // 视频宽度
        String rotation = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION); // 视频旋转方向
        String duration = retr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
        if(isNumeric(width) && isNumeric(height)){
            if (rotation.equals("90")||rotation.equals("270")) {
                intent.putExtra("videoWidth", Integer.parseInt(height));
                intent.putExtra("videoHeight", Integer.parseInt(width));
                intent.putExtra("imaWidth", Integer.parseInt(height));
                intent.putExtra("imaHeght", Integer.parseInt(width));
                intent.putExtra("w", Integer.parseInt(height));
                intent.putExtra("h", Integer.parseInt(width));
            }else{
                intent.putExtra("videoWidth", Integer.parseInt(width));
                intent.putExtra("videoHeight", Integer.parseInt(height));
                intent.putExtra("imaWidth", Integer.parseInt(width));
                intent.putExtra("imaHeght", Integer.parseInt(height));
                intent.putExtra("w", Integer.parseInt(width));
                intent.putExtra("h", Integer.parseInt(height));
            }
        }else{
            return false;
        }
        if (isNumeric(duration)) {
            intent.putExtra("totalTime", Long.parseLong(duration)/1000);
        }
        return true;
    }
    public static boolean isNumeric(String str) {
        try{
            if (!isNotEmpty(str)) {
                return false;
            }
            for (int i = str.length(); --i >= 0;) {
                if (!Character.isDigit(str.charAt(i))) {
                    return false;
                }
            }
            return true;
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }
    /**
     * str 不等于:null , "" ,"null" 返回true
     *
     * @param str
     * @return
     */
    public static boolean isNotEmpty(String str) {
        return !(str == null || "".equals(str));
    }
}
