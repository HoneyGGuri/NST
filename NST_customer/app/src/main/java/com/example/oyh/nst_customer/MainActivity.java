package com.example.oyh.nst_customer;

import android.Manifest;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.List;


/*
    UPDATE LOG
    2019/05/02  > AP4추가
                > IP_ADDRESS를 iptime으로 변경 "ip-nst.iptime.org"
                > 스캔 횟수를 100회로 고정
    2019/05/05  > Locate관련 버튼, 함수 제거
                > WifiService 추가 및 php 전송 함수 이전
    2019/05/06  > 고객용으로 확정
                > 플라스크 통신으로 변경
    2019/05/07  > 충돌 오류로 고객용 어플 새로 작성 및 코드 복사
                > 서비스 -> 액티비티 통신 성공, 위치 리턴 받는 것 확인
    2019/05/09  > 앱 디자인 시작
    2019/05/11  > 메세지 수신기능 추가(토스트)
    2019/05/15  > 디자인 지속 수정
                > 버튼 내용 수정
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = "WIFIScanner";

    int i=0,r=0, uid, save_id;
    //MyAsync back;
    WifiManager wifiManager;
    private static String IP_ADDRESS = "http://ip-nst.iptime.org:5000";


    // UI variable
    String serv_result = "";
    String loc="",msg="",save_msg="";
    ImageView msg_btn,exc,search,qna,map;
    RelativeLayout warning;
    TextView where, near;
    private List<ScanResult> mScanResult; // ScanResult List

    //dialog variable
    Here_dialog hd;
    msg_dialog md;
    QNA_dialog qd;
    Search_dialog sd;

    Socket socket;


    int cnt_arr, now, scanCount;
    String[] rss;
    int[][] arr;
    String[] result;
    /**
     * Called when the activity is first created.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG,this+" onCreate()");
        cnt_arr=0; now=0; uid=-1; save_id=uid;
        rss=new String[5];
        arr=new int[10][4];
        result=new String[100];
        scanCount=0;
        // Setup UI
        msg_btn=(ImageView)findViewById(R.id.msg_btn);
        exc=(ImageView)findViewById(R.id.exc_btn);
        search=(ImageView)findViewById(R.id.search_btn);
        qna=(ImageView)findViewById(R.id.qna_btn);
        map=(ImageView)findViewById(R.id.map);
        warning=(RelativeLayout)findViewById(R.id.warning_parent);
        warning.setVisibility(View.GONE);
        where=(TextView)findViewById(R.id.where);
        near=(TextView)findViewById(R.id.near);


        msg_btn.setOnClickListener(this);
        exc.setOnClickListener(this);
        search.setOnClickListener(this);
        qna.setOnClickListener(this);

        //Setup dialog
        hd=new Here_dialog(this);
        md=new msg_dialog(this);
        qd=new QNA_dialog(this);
        sd=new Search_dialog(this);


        // Setup WIFI
        wifiManager=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        Log.d(TAG, "Setup WIfiManager getSystemService");

        if(Build.VERSION.SDK_INT>=23 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,Manifest.permission.CHANGE_WIFI_STATE) !=PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,Manifest.permission.INTERNET) !=PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{
                    Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.INTERNET, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            },200);
        }


        final IntentFilter filter = new IntentFilter(
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);

        //registerReceiver(rReceiver, "com.example.SOCKET");

        wifiManager.startScan();

        try{
            socket = IO.socket(IP_ADDRESS);
            socket.connect();
            socket.on(Socket.EVENT_CONNECT, onConnect);
            socket.on("getMessage", onMsg);
            socket.on("getLocation", onLoc);

        }catch (URISyntaxException e){
            e.printStackTrace();
        }
    }

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            JSONObject data = new JSONObject();
            try {
                data.put("value", "-66");
                socket.emit("clientMessage", data);
                Log.d(TAG, "onConnect");
            }catch (JSONException e){
                e.printStackTrace();
                Log.d(TAG,"fail");
            }
        }
    };

    private Emitter.Listener onMsg = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            // 전달받은 데이터는 아래와 같이 추출할 수 있습니다.

            try {
                Log.d(TAG,"get Msg");
                JSONObject receivedData = (JSONObject) args[0];
                Log.d(TAG, receivedData.getString("msg"));
                msg=receivedData.getString("msg");

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!msg.equals(save_msg)){
                            msg_btn.setImageResource(R.drawable.message_btn_on);
                            save_msg=msg;
                        }
                    }
                });

            } catch (JSONException e) {
                e.printStackTrace();
                Log.d(TAG,"get Msg failed");
            }
        }
    };

    private Emitter.Listener onLoc = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            // 전달받은 데이터는 아래와 같이 추출할 수 있습니다.

            try {
                Log.d(TAG,"get Loc");
                JSONObject receivedData = (JSONObject) args[0];
                Log.d(TAG, receivedData.getString("loc"));
                loc=receivedData.getString("loc");
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        map(loc);
                    }
                });


            } catch (JSONException e) {
                e.printStackTrace();
                Log.d(TAG,"get Msg failed");
            }
        }
    };


    @Override
    public void onPause(){
        super.onPause();
        Log.d(TAG,this+" onPause()");
        //finish();
    }

    @Override
    public void onResume(){
        super.onResume();
        Log.d(TAG,this+" onResume()");

    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.d(TAG,this+" onDestroy()");
        unregisterReceiver(mReceiver);
        socket.disconnect();
    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {

                    getWIFIScanResult(); // get WIFISCanResult
                    wifiManager.startScan();

            }
        }
    };

    private BroadcastReceiver rReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals("com.example.SOCKET")) {

                getWIFIScanResult(); // get WIFISCanResult
                wifiManager.startScan();

            }
        }
    };

    public void getWIFIScanResult() {
        // -255 count
        int cnt_255=0;
        int[] avg=new int[4];
        mScanResult = wifiManager.getScanResults();

/*
        String rssi1_name = "NST1";
        String rssi2_name = "NST2";
        String rssi3_name = "NST3";
        String rssi4_name = "NST4";*/

        String rssi1_name = "FREE_U+zone";
        String rssi2_name = "Freeinternet";
        String rssi3_name = "Kwangwoon_KT";
        String rssi4_name = "U+zone";

        String rs1 = "";
        String rs2 = "";
        String rs3 = "";
        String rs4 = "";

        for (int i = 0; i < mScanResult.size(); i++) {
            ScanResult result = mScanResult.get(i);


            if (result.SSID.toString().equals(rssi1_name)) {
                rs1 = String.valueOf(result.level);
            }
            if (result.SSID.toString().equals(rssi2_name)) {
                rs2 = String.valueOf(result.level);
            }
            if (result.SSID.toString().equals(rssi3_name)) {
                rs3 = String.valueOf(result.level);
            }
            if (result.SSID.toString().equals(rssi4_name)) {
                rs4 = String.valueOf(result.level);
            }
        }

        if (rs1.equals("")) {rs1 = "-255"; cnt_255++;}
        if (rs2.equals("")) {rs2 = "-255"; cnt_255++;}
        if (rs3.equals("")) {rs3 = "-255"; cnt_255++;}
        if (rs4.equals("")) {rs4 = "-255"; cnt_255++;}

        rss[4]=String.valueOf(uid);
        if(cnt_255==4){
            warning.setVisibility(View.VISIBLE);
            hd.where.setText("알 수 없음");
            hd.near.setText("알 수 없음");
        }
        else {
            warning.setVisibility(View.GONE);
        }
        if(cnt_arr==0){
            rss[0]=rs1;
            rss[1]=rs2;
            rss[2]=rs3;
            rss[3]=rs4;
            if(cnt_255 <= 1){
                scanCount++;
                arr[now][0] = Integer.parseInt(rs1);
                arr[now][1] = Integer.parseInt(rs2);
                arr[now][2] = Integer.parseInt(rs3);
                arr[now][3] = Integer.parseInt(rs4);

                Log.d(TAG,"AP1 = " + rs1 + ", AP2 = " + rs2 + ", AP3 = " + rs3 + ", AP4 = " + rs4 + "\navg1 = " + rss[0] + ", avg2 = " + rss[1] + ", avg3 = " + rss[2] + ", avg4 = " + rss[3] + "uid = "+uid+ "\n현재 가중치 : 9대1");


                JSONObject data = new JSONObject();
                try {
                    data.put("rs1",rs1);
                    data.put("rs2",rs2);
                    data.put("rs3",rs3);
                    data.put("rs4",rs4);
                    socket.emit("getLocation", data);
                    socket.emit("getMessage","u hyo~ getto daje~");
                }catch (JSONException e){
                    e.printStackTrace();
                    Log.d(TAG, "fail to send rss");
                }
                cnt_arr++;
                now=1;
            }
        }
        else {
            if (cnt_255 <= 1) {
                scanCount++;
                arr[now][0] = Integer.parseInt(rs1);
                arr[now][1] = Integer.parseInt(rs2);
                arr[now][2] = Integer.parseInt(rs3);
                arr[now][3] = Integer.parseInt(rs4);
                if (now == 0) {
                    for (int i = 0; i < 2; i++) {
                        avg[0] = (int)((arr[0][0] * 0.9) + (arr[1][0] * 0.1));
                        avg[1] = (int)((arr[0][1] * 0.9) + (arr[1][1] * 0.1));
                        avg[2] = (int)((arr[0][2] * 0.9) + (arr[1][2] * 0.1));
                        avg[3] = (int)((arr[0][3] * 0.9) + (arr[1][3] * 0.1));
                    }
                    if (cnt_255 == 0)
                        now = 1;
                } else {
                    for (int i = 0; i < 2; i++) {
                        avg[0] = (int)((arr[0][0] * 0.1) + (arr[1][0] * 0.9));
                        avg[1] = (int)((arr[0][1] * 0.1) + (arr[1][1] * 0.9));
                        avg[2] = (int)((arr[0][2] * 0.1) + (arr[1][2] * 0.9));
                        avg[3] = (int)((arr[0][3] * 0.1) + (arr[1][3] * 0.9));
                    }
                    if (cnt_255 == 0)
                        now = 0;
                }
                for (int i = 0; i < 4; i++) {
                    rss[i] = String.valueOf(avg[i]);
                }
                Log.d(TAG,"AP1 = " + rs1 + ", AP2 = " + rs2 + ", AP3 = " + rs3 + ", AP4 = " + rs4 + "\navg1 = " + rss[0] + ", avg2 = " + rss[1] + ", avg3 = " + rss[2] + ", avg4 = " + rss[3] + "\n현재 가중치 : 9대1");

                JSONObject data = new JSONObject();
                try {
                    data.put("rs1",rs1);
                    data.put("rs2",rs2);
                    data.put("rs3",rs3);
                    data.put("rs4",rs4);
                    socket.emit("getLocation", data);
                    socket.emit("getMessage","u hyo~ getto daje~");
                }catch (JSONException e){
                    e.printStackTrace();
                    Log.d(TAG, "fail to send rss");
                }
            }
        }

    }

    public void showT(String messageToast) {
        Toast.makeText(this, messageToast, Toast.LENGTH_LONG).show();
    }

    private void map(String w){

        map.setScaleType(ImageView.ScaleType.FIT_XY);
        if(w.equals("A")){
            map.setImageResource(R.drawable.a);
            hd.where.setText("301호 앞 복도 안쪽");
            hd.near.setText("301호 강의실");
        }else if(w.equals("B")){
            map.setImageResource(R.drawable.b);
            hd.where.setText("301호 앞 복도");
            hd.near.setText("301호 강의실, 302호 강의실, 계단");
        }else if(w.equals("C")){
            map.setImageResource(R.drawable.c);
            hd.where.setText("302호 앞 삼거리");
            hd.near.setText("301호 강의실, 302호 강의실, 계단, 엘레베이터");
        }else if(w.equals("D")){
            map.setImageResource(R.drawable.d);
            hd.where.setText("엘레베이터 앞 복도");
            hd.near.setText("계단, 엘레베이터, 남자 화장실, 여자 화장실");
        }else if(w.equals("E")){
            map.setImageResource(R.drawable.e);
            hd.where.setText("휴게실");
            hd.near.setText("남자 화장실, 여자 화장실");
        }else if(w.equals("F")){
            map.setImageResource(R.drawable.f);
            hd.where.setText("303호 앞 복도");
            hd.near.setText("303호 강의실, 학생회실");
        }else if(w.equals("G")){
            map.setImageResource(R.drawable.g);
            hd.where.setText("학생회실");
            hd.near.setText("303호 강의실");
        }else if(w.equals("H")){
            map.setImageResource(R.drawable.h);
            hd.where.setText("304호 앞 삼거리");
            hd.near.setText("304호 강의실, 학생회실");
        }else if(w.equals("I")){
            map.setImageResource(R.drawable.i);
            hd.where.setText("엘레베이터 2호기 앞 복도");
            hd.near.setText("엘레베이터, 304호 강의실");
        }else if(w.equals("J")){
            map.setImageResource(R.drawable.j);
            hd.where.setText("304호 앞 복도 안쪽");
            hd.near.setText("304호 강의실");
        }else{
            map.setScaleType(ImageView.ScaleType.CENTER);
            map.setImageResource(R.drawable.map_white_cut);
            hd.where.setText("????");
        }
    }

    @Override
    public void onClick(View v) {
        if(v==msg_btn){
            md.content.setText(msg);
            md.show();
            msg_btn.setImageResource(R.drawable.message_btn);
        }else if(v==exc){
            hd.show();
        }else if(v==search){
            sd.show();
        }

        if(v==qna){
            qd.show();
        }
    }
    //////////////////////////////////////// 커스텀다이얼로그 부분 ///////////////////////////////////////////////
    public class Search_dialog extends Dialog implements View.OnClickListener {

        Button btn;
        public Search_dialog(Context context){
            super(context);

            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setContentView(R.layout.search_dialog);

            btn=(Button)findViewById(R.id.btn);
            btn.setOnClickListener(this);
        }

        @Override
        public void onClick(View view){
            if(view==btn){
                dismiss();
            }
        }
    }

    public class QNA_dialog extends Dialog implements View.OnClickListener {

        Button btn;
        public QNA_dialog(Context context){
            super(context);

            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setContentView(R.layout.qna_dialog);

            btn=(Button)findViewById(R.id.btn);
            btn.setOnClickListener(this);
        }

        @Override
        public void onClick(View view){
            if(view==btn){
                dismiss();
            }
        }
    }

    public class Here_dialog extends Dialog implements View.OnClickListener {

        Button btn;
        TextView where,near;
        public Here_dialog(Context context){
            super(context);

            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setContentView(R.layout.here_dialog);

            where=(TextView)findViewById(R.id.where);
            near=(TextView)findViewById(R.id.near);
            btn=(Button)findViewById(R.id.btn);
            btn.setOnClickListener(this);
        }

        @Override
        public void onClick(View view){
            if(view==btn){
                dismiss();
            }
        }
    }

    public class msg_dialog extends Dialog implements View.OnClickListener {
        Button btn;
        TextView content;
        public msg_dialog(Context context){
            super(context);

            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setContentView(R.layout.msg_dialog);

            content=(TextView)findViewById(R.id.content);
            btn=(Button)findViewById(R.id.btn);
            btn.setOnClickListener(this);
        }

        @Override
        public void onClick(View view){
            if(view==btn){
                dismiss();
            }
        }
    }

}
