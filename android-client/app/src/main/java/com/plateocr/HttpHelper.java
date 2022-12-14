package com.plateocr;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HttpHelper {
    static public String SERVER_URL = "https://choia.pythonanywhere.com";
    private Thread checkThread;

    public interface HttpListener {
        public void onSuccess(String data);
        public void onFailure(String e);
        public void onComplete();
    }

    static public Thread login(String id, String pw, HttpListener listener) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(SERVER_URL+"/api/login");
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();

                    con.setRequestMethod("POST");
                    con.setDoInput(true);
                    con.setDoOutput(true);
                    con.setUseCaches(false);
                    con.setConnectTimeout(5000);

                    String strParams = "id="+id+"&password="+pw; //sbParams에 정리한 파라미터들을 스트링으로 저장. 예)id=id1&pw=123;
                    OutputStream os = con.getOutputStream();
                    os.write(strParams.getBytes("UTF-8")); // 출력 스트림에 출력.
                    os.flush(); // 출력 스트림을 플러시(비운다)하고 버퍼링 된 모든 출력 바이트를 강제 실행.
                    os.close(); // 출력 스트림을 닫고 모든 시스템 자원을 해제.

                    Log.d(">>>", "Login:send");

                    int responseCode = con.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream is = con.getInputStream();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] byteBuffer = new byte[1024];
                        byte[] byteData = null;
                        int nLength = 0;
                        while ((nLength = is.read(byteBuffer, 0, byteBuffer.length)) != -1) {
                            baos.write(byteBuffer, 0, nLength);
                        }
                        byteData = baos.toByteArray();

                        String response = new String(byteData);
                        if(listener!=null) {
                            Log.d(">>>", "Login:succ");
                            listener.onSuccess(response);
                        }
                    } else {

                        if(listener!=null) {
                            listener.onFailure("로그인 실패");
                        }
                        Log.d(">>>", "Login:fail");
                    }

                    Log.d(">>>", "Login:done");
                } catch (IOException e) {
                    if(listener!=null) {
                        listener.onFailure(e.getMessage());
                    }
                    Log.e(">>>", "run: "+e.getMessage());
                }
                if(listener!=null) {
                    listener.onComplete();
                }
            }
        });
    }

    public static Thread checkPlateNumber(String num, HttpListener listener) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(SERVER_URL+"/api/car/" + num);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    Log.d(">>>", "Car:send");
                    int responseCode = con.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream is = con.getInputStream();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] byteBuffer = new byte[1024];
                        byte[] byteData = null;
                        int nLength = 0;
                        while ((nLength = is.read(byteBuffer, 0, byteBuffer.length)) != -1) {
                            baos.write(byteBuffer, 0, nLength);
                        }
                        byteData = baos.toByteArray();

                        String response = new String(byteData);
                        if(listener!=null) {
                            listener.onSuccess(response);
                        }
                    }
                    Log.d(">>>", "Car:done");
                } catch (IOException e) {
                    if(listener!=null) {
                        listener.onFailure(e.getMessage());
                    }
                    Log.e(">>>", "run: "+e.getMessage());
                }
                if(listener!=null) {
                    listener.onComplete();
                }
            }
        });

    }
}
