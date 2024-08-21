package com.example.rcchip;

import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RC extends AppCompatActivity {

    private static final int udp_port=2400;
    private static final int local_port = 3200;
    volatile InetAddress ip=null;

    volatile boolean confirmed=false;
    private int G_angle=0;
    private int G_strength=0;

    private boolean smoke=false;

    private Button connectButton;
    private Button smokeButton;

    private long lastClickTime = 0;

    private static final long MIN_CLICK_INTERVAL = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rc);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        JoystickView joystick=findViewById(R.id.joystick);
        smokeButton=findViewById(R.id.smoke_button);
        connectButton=findViewById(R.id.cc_button);

        joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                long currentTime = System.currentTimeMillis();
                if ((Math.abs(G_angle-angle)>=10)||(Math.abs(G_strength-strength)>=10)){
                    Log.i("joystick", "onMove: "+G_strength);
                    Log.i("joystick", "onMove: "+G_angle);
                    G_angle=angle;
                    G_strength=strength;
                    String message="";
                    if (G_strength==0){
                        message+="r";
                    }
                    else if ((G_angle>=0)&&(G_angle<=180)){
                        message+="+";
                        message+=String.format(Locale.ENGLISH,"%03d", (int) (G_strength*2.55));
                        message+=";";
                        message+=String.format(Locale.ENGLISH,"%03d",G_angle);;
                    }
                    else {
                        message+="-";
                        message+=String.format(Locale.ENGLISH,"%03d",(int) (G_strength*2.55));
                        message+=";";
                        message+=String.format(Locale.ENGLISH,"%03d",G_angle);;
                    }
                    Log.i("joystick", "onMove: "+confirmed);
                    Log.i("joystick", "onMove: "+ip);
                    if (G_strength==0){
                        new SendUDP().execute(message);
                    }
                    else if (currentTime - lastClickTime > MIN_CLICK_INTERVAL) {
                        lastClickTime = currentTime;
                        new SendUDP().execute(message);
                    }
                }
            }
        });

        smokeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new SendUDP().execute("s");
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ConfirmIP().execute();
            }
        });
    }

    private synchronized void config(boolean b,InetAddress i){
        confirmed=b;
        ip=i;
    }

    private class ConfirmIP extends AsyncTask<Void,Void,Void>{

        private boolean temp_c;
        private InetAddress temp_ip;

        public ConfirmIP() {
        }

        public ConfirmIP(boolean temp_c, InetAddress temp_ip) {
            this.temp_c = temp_c;
            this.temp_ip = temp_ip;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                Thread.sleep(100);
                DatagramSocket socket=new DatagramSocket(local_port);
                byte[] buffer=new byte[128];
                while (!temp_c){
                    DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                    socket.receive(packet);
                    String message=new String(packet.getData(),0,packet.getLength());
                    Log.i("confirmIP",message+"/form: "+packet.getAddress());
                    if (message.equals("cced")){
                        temp_c=true;
                        temp_ip=packet.getAddress();
                    }
                    else {
                        temp_c=false;
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            confirmed=temp_c;
            if (confirmed){
                ip=temp_ip;
                connectButton.setEnabled(false);
                new SendUDP().execute("cced");
                Toast.makeText(RC.this,"device connected!",Toast.LENGTH_LONG).show();
            }
            Log.i("confirmIP", "onPostExecute: "+confirmed+"/"+ip);
        }
    }

    private class SendUDP extends AsyncTask<String,Void,Void>{

        @Override
        protected Void doInBackground(String... strings) {
            if(confirmed){
                try {
                    DatagramSocket socket=new DatagramSocket(udp_port);
                    byte[] buffer=strings[0].getBytes();
                    //InetAddress ip=InetAddress.getByName("192.168.111.99");
                    Log.i("sendUDP", "doInBackground: "+strings[0]+ip.toString());
                    DatagramPacket packet=new DatagramPacket(buffer,buffer.length,ip,udp_port);
                    socket.send(packet);
                    socket.close();
                    System.out.println("sent");
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        /*@Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            if (confirmed){
                GetReply temp=new GetReply();
                temp.execute();
                setAsyncTaskTimeout(temp,500);
            }
        }*/
    }

    private class GetReply extends AsyncTask<Void,Void,Void>{
        private boolean temp_c=false;

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                DatagramSocket socket=new DatagramSocket(local_port);
                byte[] buffer=new byte[128];
                DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                socket.receive(packet);
                String message=new String(packet.getData(),0,packet.getLength());
                Log.i("REPLY",message+"/form: "+packet.getAddress());
                if (message.equals("cced")){
                    temp_c=true;
                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            confirmed=temp_c;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            confirmed=temp_c;
            connectButton.setEnabled(true);
        }
    }

    private void setAsyncTaskTimeout(final AsyncTask<?, ?, ?> asyncTask, long timeoutMillis) {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (asyncTask.getStatus() == AsyncTask.Status.RUNNING) {
                    asyncTask.cancel(true);
                    Toast.makeText(RC.this, "Task timed out", Toast.LENGTH_SHORT).show();
                }
            }
        }, timeoutMillis);
    }

    /*public void confirmIP() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket socket=new DatagramSocket(local_port);
                    byte[] buffer=new byte[128];
                    while (!confirmed){
                        DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                        socket.receive(packet);
                        String message=new String(packet.getData(),0,packet.getLength());
                        Log.i("confirmIP",message+"/form: "+packet.getAddress());
                        Thread.sleep(1000);
                        if (message.equals("cced")){
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Message message_confirm=handler.obtainMessage(1,true);
                                    handler.sendMessage(message_confirm);
                                    Message message_ip=handler.obtainMessage(2,packet.getAddress());
                                    handler.sendMessage(message_ip);
                                    Toast.makeText(RC.this,message, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }*/

    /*public void sendPacket (String message){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (confirmed){
                    try {
                        DatagramSocket socket=new DatagramSocket();
                        byte[] buffer=message.getBytes();
                        //InetAddress ip=InetAddress.getByName("192.168.111.99");
                        Log.d("ip",ip.toString());
                        DatagramPacket packet=new DatagramPacket(buffer,buffer.length,ip,udp_port);
                        socket.send(packet);
                        socket.close();
                        System.out.println("sent");
                    } catch (SocketException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }*/

    /*public String getPacket(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket socket=new DatagramSocket(3200);
                    boolean running=true;
                    byte[] buffer=new byte[256];
                    while (running){
                        DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                        socket.receive(packet);
                        String incoming=new String(packet.getData(),0,packet.getLength());
                        InetAddress remoteAddress=packet.getAddress();
                        String remoteIP=remoteAddress.getHostAddress();
                        Log.i("remote IP",remoteIP);
                        Thread.sleep(3000);
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }).start();
        return null;
    }*/
}