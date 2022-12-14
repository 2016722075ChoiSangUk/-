package com.plateocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.opencv.core.CvType.CV_32S;

public class OcrRunner {
    private OcrResultListener listener1;

    /*
        허, 하, 호(렌터카용), 배(택배운송용)[5][6]
        용도기호 : 가~마, 거~저, 고~조, 구~주 (32개)
        군용 : 공군:공, 해군:해, 육군:육, 합동참모본부:합, 국직부대:국
     */

    static public interface OcrResultListener {
        public void onFinish(String str, Bitmap bitmap);
    };


    private String hangul = "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주바사아자배허하호";
    private String number = "1234567890";
    private String whitlist = hangul+number;
    private TessBaseAPI tessBaseAPI;
    private Context context;
    private OcrResultListener listener;
    final Pattern pattern = Pattern.compile("((\\d{2,3})(["+hangul+"])(\\d{4}))");

    public OcrRunner(Context context, OcrResultListener listener) {
        this.context = context;
        this.listener = listener;
        tesserInit();
        OpenCVLoader.initDebug(); // 초기화
    }

    public OcrRunner(Context context) {
        this(context, null);
    }

    private void tesserInit() {
        String path = context.getFilesDir() + "/tessdata/kor.traineddata";
        File fd = new File(path);
        if (!fd.exists()) {
            File dr = new File(context.getFilesDir() + "/tessdata");
            dr.mkdirs();
            try (OutputStream output = new FileOutputStream(fd)) {
                InputStream is = context.getAssets().open("kor.traineddata");
                byte[] buffer = new byte[4 * 1024]; // or other buffer size
                int read;
                while ((read = is.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
                Log.d(">>>", "tesserdata copy.");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        File ff = new File(path + File.separator);
        Log.d(">>>", ff + ".." + ff.exists());

        tessBaseAPI = new TessBaseAPI();

        tessBaseAPI.init(context.getFilesDir().getAbsolutePath(), "kor");
        tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, whitlist);
//        tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890허가");
    }

    public void setOcrResultListener(OcrResultListener listener) {
        this.listener = listener;
    }

    public void setListener1(OcrResultListener listener) {
        this.listener1 = listener;
    }

    public void apply(String txt, Mat mat) {
        Bitmap imgT = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888); // 비트맵 생성
        Utils.matToBitmap(mat, imgT);
//        listener1.onFinish(txt, imgT);
    }
    public void apply(String txt, Bitmap imgT) {
//        listener1.onFinish(txt, imgT);
    }

    public void find(Bitmap bitmap) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 8;


        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Mat matBase = new Mat();
        Mat matGray = new Mat();
        Mat matCny = new Mat();
        Utils.bitmapToMat(bitmap ,matBase);

        apply("origin", matBase);
        ///// Image simplify.
        // 1. GrayScale
        Imgproc.cvtColor(matBase, matGray, Imgproc.COLOR_BGR2GRAY); // GrayScale

        // 4. Blur
        Imgproc.GaussianBlur(matGray, matGray, new Size(7,5), 5);
        apply("GaussianBlur", matGray);

        // 2. Canny Edge
        Imgproc.Canny(matGray, matCny, 15, 100, 3, true); // Canny Edge 검출
        // 3. Binary
        Imgproc.threshold(matGray, matCny, 110, 255, Imgproc.THRESH_BINARY); //Binary
        apply("binary", matCny);


        Mat imgLabels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int numOfLabels = Imgproc.connectedComponentsWithStats(matCny, imgLabels, stats, centroids, 8, CvType.CV_16U);
        List<Rect> labels = new ArrayList<>();
        for(int i=0; i< numOfLabels; i++) {
            int a = (int) stats.get(i, 0)[0];
            int b = (int) stats.get(i, 1)[0];
            int c = (int) stats.get(i, 2)[0];
            int d = (int) stats.get(i, 3)[0];
            Rect rect =  new Rect(a,b,c,d);

            if (rect.x==0 || rect.y==0 || rect.width < 100 || rect.height < 30 || rect.width>rect.height*16)
                continue;
            Log.d(">>>", "labeld"+rect);
            labels.add(rect);
//            Imgproc.rectangle(matBase,  new Rect(a,b,c,d), new Scalar(200,0,0), 3);
        }
        apply("labeling", matBase);


        for(Rect rect : labels) {
            Mat matRoi = new Mat(matCny, rect);
            if(rect.width > rect.height*6) {
                Imgproc.resize(matRoi, matRoi, new Size(rect.width, rect.width/6));
            }
            Bitmap imgRoi = Bitmap.createBitmap(matRoi.cols(), matRoi.rows(), Bitmap.Config.ARGB_8888); // 비트맵 생성

            ///// Reduce noise.
            // 4. Erode
            Imgproc.erode(matRoi, matRoi, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(6, 6)));
            apply("erode", matRoi);
//        // 5. Dilate
            Imgproc.dilate(matRoi, matRoi, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(9, 9)));
            apply("dilate", matRoi);


            ///// Find contour
            Imgproc.findContours(matRoi, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            Imgproc.drawContours(matRoi, contours, -1, new Scalar(255, 0, 0), 5);
            apply("contour", matRoi);



            tessBaseAPI.setImage(imgRoi);
            String r = tessBaseAPI.getUTF8Text().replaceAll(" ","");
            Log.d(">>>", "OCR_THREAD::"+r);

            apply("whole"+ "=>"+ r, imgRoi);

            Matcher m = pattern.matcher(r);
            if(m.find()) {
                String str = m.group(0);
                Log.d(">>>", "OCR_THREAD_MATCHER::"+str);
                if(listener!=null) {
                    listener.onFinish(str, imgRoi);
                }
                break;
            }

        }

        if(labels.size()>-1) {
            Bitmap img = Bitmap.createBitmap(matCny.cols(), matCny.rows(), Bitmap.Config.ARGB_8888); // 비트맵 생성
            Utils.matToBitmap(matCny,img);
            tessBaseAPI.setImage(img);
            String r = tessBaseAPI.getUTF8Text().replaceAll(" ","");
            Log.d(">>>", "OCR_THREAD::"+r);

            apply("all"+ "=>"+ r, img);

            Matcher m = pattern.matcher(r);
            if(m.find()) {
                String str = m.group(0);
                Log.d(">>>", "OCR_THREAD_MATCHER 2::"+str);
                if(listener!=null) {
                    listener.onFinish(str, img);
                }
            }
        }

        if(true)
        return ;


        ///// Reduce noise.
        // 4. Erode
        Imgproc.erode(matCny, matCny, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(6, 6)));
        apply("erode", matCny);
//        // 5. Dilate
        Imgproc.dilate(matCny, matCny, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new org.opencv.core.Size(9, 9)));
        apply("dilate", matCny);


        ///// Find contour
        Imgproc.findContours(matCny, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
//        Imgproc.drawContours(matBase, contours, -1, new Scalar(255, 0, 0), 5);
        apply("contour", matCny);



        Bitmap imgCany = Bitmap.createBitmap(matCny.cols(), matCny.rows(), Bitmap.Config.ARGB_8888); // 비트맵 생성


        int i = 0;
        if(contours.size()>0) {
            List<Rect> queue = new ArrayList<>();
            for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0]) {
                MatOfPoint matOfPoint = contours.get(idx);
                Rect rect = Imgproc.boundingRect(matOfPoint);



                if (rect.width < 100 || rect.height < 30 || rect.width <= rect.height || rect.width <= rect.height * 3 || rect.width >= rect.height * 6)
                    continue; // 사각형 크기에 따라 출력 여부 결정

                queue.add(rect);
            }
            for(Rect rect : queue) {
                Bitmap roi = Bitmap.createBitmap(imgCany, (int) rect.tl().x, (int) rect.tl().y, rect.width, rect.height);
                tessBaseAPI.setImage(roi);
                String r = tessBaseAPI.getUTF8Text().replaceAll(" ","");
                Log.d(">>>", "OCR_THREAD::"+r);
                Matcher m = pattern.matcher(r);


                apply("ROI"+(++i)+ "=>"+ r +"=="+rect.width+","+rect.height, roi);

                if(m.find()) {
                    String str = m.group(0);
                    Log.d(">>>", "OCR_THREAD_MATCHER::"+str);
                    if(listener!=null) {
                        listener.onFinish(str, roi);
                    }
                } else {
//                    listener.onFinish("----", roi);
                }
            }
            if(queue.size()==0) {
                Bitmap img = Bitmap.createBitmap(matCny.cols(), matCny.rows(), Bitmap.Config.ARGB_8888); // 비트맵 생성
                Utils.matToBitmap(matCny,img);
                tessBaseAPI.setImage(img);
                String r = tessBaseAPI.getUTF8Text().replaceAll(" ","");
                Log.d(">>>", "OCR_THREAD::"+r);

                apply("whole"+(++i)+ "=>"+ r, img);

                Matcher m = pattern.matcher(r);
                if(m.find()) {
                    String str = m.group(0);
                    Log.d(">>>", "OCR_THREAD_MATCHER::"+str);
                    if(listener!=null) {
                        listener.onFinish(str, img);
                    }
                }
            }
        }
    }

}
