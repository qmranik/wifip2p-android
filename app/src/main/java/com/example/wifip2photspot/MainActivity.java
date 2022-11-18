package com.example.wifip2photspot;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.InetAddresses;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    TextView tvStatus;
    Button btnSend;
    Button btnWifiState;
    Button btnDiscover;
    EditText etMessage;
    TextView tvMessage;
    ListView listView;

    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    BroadcastReceiver receiver;
    IntentFilter intentFilter;

    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] devicesNames;
    WifiP2pDevice[] deviceArray;

    Socket socket;
    ServerClass serverClass;
    ClientClass clientClass;
    boolean isHost;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeAll();
        exqListeners();
        requestUserPermission();

    }

    public void requestUserPermission() {

        ArrayList<String> permissions = new ArrayList<String>();

        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(ActivityCompat.checkSelfPermission(this,Manifest.permission_group.NEARBY_DEVICES) != PackageManager.PERMISSION_GRANTED){
                permissions.add(Manifest.permission_group.NEARBY_DEVICES);
            }
        }
        String[] str = new String[permissions.size()];

        for (int i = 0; i < permissions.size(); i++) {
            str[i] = permissions.get(i);
        }

        ActivityCompat.requestPermissions(this, str,1);

    }

    private void exqListeners() {

        btnWifiState.setOnClickListener(view -> {
            Intent intent = new Intent(Settings.Panel.ACTION_WIFI);
            startActivityForResult(intent, 1);

        });

        btnDiscover.setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestUserPermission();
                return;
            }


            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    tvStatus.setText("Discovery Started");
                }

                @Override
                public void onFailure(int i) {
                    tvStatus.setText("Discovery Failed");
                }
            });
        });

        listView.setOnItemClickListener((adapterView, view, i, l) -> {

            final WifiP2pDevice device = deviceArray[i];
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;
            manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    tvStatus.setText("Connected device : "+device.deviceAddress);
                }

                @Override
                public void onFailure(int i) {
                    tvStatus.setText("not connected");
                }
            });

        });

        btnSend.setOnClickListener(view -> {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            String msg = etMessage.getText().toString();
            executor.execute(()->{
                if(msg!=null && isHost){
                    serverClass.write(msg.getBytes());
                }else if(msg != null && !isHost){
                    clientClass.write(msg.getBytes());
                }
            });
        });

    }

    private void initializeAll() {

        tvStatus = findViewById(R.id.tvConStatus);
        btnSend  = findViewById(R.id.btnSend) ;
        btnWifiState = findViewById(R.id.btnWifiState);
        btnDiscover = findViewById(R.id.btnDiscover);
        etMessage = findViewById(R.id.etMessage);
        tvMessage = findViewById(R.id.tvMessage);
        listView = findViewById(R.id.listview);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this,getMainLooper(),null);
        receiver = new WifiDirectBroadcastRecever(manager,channel,this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        Log.d("debug","1");

    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {


            if(!peers.equals(wifiP2pDeviceList.getDeviceList())) {
                peers.clear();
                peers.addAll(wifiP2pDeviceList.getDeviceList());

                devicesNames = new String[wifiP2pDeviceList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[wifiP2pDeviceList.getDeviceList().size()];

                int index = 0;
                for (WifiP2pDevice device : wifiP2pDeviceList.getDeviceList()) {
                    devicesNames[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                Log.d("debug", String.valueOf(devicesNames.length));
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, devicesNames);
                listView.setAdapter(adapter);

//                 adapter.addAll(wifiP2pDeviceList.getDeviceList().toString());
                adapter.notifyDataSetChanged();
                if (peers.size() == 0) {
                    tvStatus.setText("No devices found");
                    return;
                }

            }

        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener =  new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
            if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner ){
                tvStatus.setText("HOST");
                isHost=true;
                serverClass = new ServerClass();
                serverClass.start();
            }else{
                tvStatus.setText("CLIENT");
                isHost=false;
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();
            }

        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver,intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public class ServerClass extends Thread{

        ServerSocket serverSocket;
        private InputStream inputStream;
        private OutputStream outputStream;


        public void write(byte[] bytes){

            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8889);
                socket = serverSocket.accept();
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

            } catch (IOException e) {
                e.printStackTrace();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(() -> {
                byte[] buffer = new byte[1024];
                int bytes;

                while (socket!=null){
                    try {
                        bytes = inputStream.read(buffer);
                        if(bytes > 0){
                            int finalBytes = bytes;
                            handler.post(() -> {
                               String str = new String(buffer,0,finalBytes);
                               tvMessage.setText(str);
                            });

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            });

        }
    }


    public class ClientClass extends Thread{
        String hostAddress;
        private InputStream inputStream;
        private OutputStream outputStream;

        public void write(byte[] bytes){

            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        public ClientClass(InetAddress hostAddress){
            this.hostAddress = hostAddress.getHostAddress();
             socket = new Socket();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAddress,8889),500);

                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

            } catch (IOException e) {
                e.printStackTrace();
            }

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executorService.execute(() -> {
                byte[] buffer = new byte[1024];
                int bytes;

                while (socket != null){
                    try {
                        bytes = inputStream.read(buffer);
                        if(bytes>0){
                            int finalBytes = bytes;
                            handler.post(() -> {
                               String message = new String(buffer,0,finalBytes);
                                tvMessage.setText(message);
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

        }
    }


}