package com.alvin.capture.camera2.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.support.media.ExifInterface;
import android.util.Log;

import com.alvin.capture.jcamera.util.FileUtil;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

public class BitmapDecoder {

    /**
     *
     * @param filename 本地图片路径（判断是否需要旋转）
     * @param fileDescriptor
     * @param maxSize
     * @param config
     * @return
     */
    public static Bitmap decodeSampledBitmapFromDescriptor(String filename, FileDescriptor fileDescriptor, BitmapSize maxSize, Bitmap.Config config) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
        options.inSampleSize = calculateInSampleSize(options, maxSize.getWidth(), maxSize.getHeight());
        options.inJustDecodeBounds = false;
        if (config != null) {
            options.inPreferredConfig = config;
        }
        try {
            return decodeSampledBitmap(BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options), options, filename);//是否需要翻转
        } catch (Throwable e) {
            Log.e("http", e.getMessage(), e);
            return null;
        }
    }

    public static Bitmap decodeSampledBitmapFromFile(File file, BitmapSize maxSize, Bitmap.Config config) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        options.inSampleSize = calculateInSampleSize(options, maxSize.getWidth(), maxSize.getHeight());
        options.inJustDecodeBounds = false;
        if (config != null) {
            options.inPreferredConfig = config;
        }
        try {
            return decodeSampledBitmap(BitmapFactory.decodeFile(file.getPath(), options), options, file.getPath());//是否需要翻转
        } catch (Throwable e) {
            Log.e("http", e.getMessage(), e);
            return null;
        }
    }



    public static int calculateInSampleSize(BitmapFactory.Options options, int maxWidth, int maxHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (width > maxWidth || height > maxHeight) {
            if (width > height) {
                inSampleSize = Math.round((float) height / (float) maxHeight);
            } else {
                inSampleSize = Math.round((float) width / (float) maxWidth);
            }

            final float totalPixels = width * height;

            final float maxTotalPixels = maxWidth * maxHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > maxTotalPixels) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmap(Bitmap subsampledBitmap, BitmapFactory.Options options, String imageUri) throws IOException {
        if (options != null && FileUtil.isNotEmpty(options.outMimeType) && options.outMimeType.startsWith("image/")) {
            ExifInfo exifInfo = defineExifOrientation(imageUri);
            if(exifInfo != null && (exifInfo.flipHorizontal || exifInfo.rotation != 0)){
                return considerExactScaleAndOrientatiton(subsampledBitmap, exifInfo);
            }
        }
        return subsampledBitmap;
    }

    public static Bitmap considerExactScaleAndOrientatiton(Bitmap subsampledBitmap, ExifInfo exifInfo) {
        if(exifInfo != null){
            Matrix m = new Matrix();
            // Flip bitmap if need
            if (exifInfo.flipHorizontal) {
                m.postScale(-1, 1);
            }
            // Rotate bitmap if need
            if (exifInfo.rotation != 0) {
                m.postRotate(exifInfo.rotation);
            }
            Bitmap finalBitmap = Bitmap.createBitmap(subsampledBitmap, 0, 0, subsampledBitmap.getWidth(), subsampledBitmap.getHeight(), m, true);
            if (finalBitmap != subsampledBitmap) {
                subsampledBitmap.recycle();
            }
            return finalBitmap;
        }
        return subsampledBitmap;
    }

    public static ExifInfo defineExifOrientation(String imageUri) {
        int rotation = 0;
        boolean flip = false;
        try {
            ExifInterface exif = new ExifInterface(imageUri);
            int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    flip = true;
                case ExifInterface.ORIENTATION_NORMAL:
                    rotation = 0;
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    flip = true;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    flip = true;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    flip = true;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
            }
        } catch (Exception e) {
            Log.e("BitmapDecoder","图片转换失败"+imageUri);
        }
        return new ExifInfo(rotation, flip);
    }
}
