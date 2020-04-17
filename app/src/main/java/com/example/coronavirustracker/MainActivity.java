package com.example.coronavirustracker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.coronavirustracker.bluetooth.BluetoothContactTracer2;
import com.example.coronavirustracker.database.ContactTrace;
import com.example.coronavirustracker.database.ContactTraceDao;
import com.example.coronavirustracker.database.ContactTraceRoomDatabase;
import com.example.coronavirustracker.internet.DownloadCallback;
import com.example.coronavirustracker.internet.NetworkFragment;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.example.coronavirustracker.CoronaHandler.KeyToString;
import static com.example.coronavirustracker.CoronaHandler.checkIfFileExist;
import static com.example.coronavirustracker.CoronaHandler.checkIfKeysExist;
import static com.example.coronavirustracker.CoronaHandler.generateKeyPair;
import static com.example.coronavirustracker.CoronaHandler.readFileAsString;
import static com.example.coronavirustracker.CoronaHandler.readKeyPair;
import static com.example.coronavirustracker.CoronaHandler.verifySignature;
import static com.example.coronavirustracker.CoronaHandler.writeToFile;

public class MainActivity extends FragmentActivity implements DownloadCallback {
    public static final UUID MY_UUID = UUID.fromString("d2033ac3-6439-4ed6-9bb3-c7a4f2e5801d");
    private static final String DEVICE_BLUETOOTH_NAME = "Corona Tracker";
    public static String TAG = "MainActivity1";
    private final static int REQUEST_ENABLE_BT = 1;
    public final static String TEMP_SIGNATURE_LOCATION = "temp_signature"; // used only for testing, not for actual
    private final static String URL = "http://hallowdawnlarp.com/covid19.php";
    public final static String PRIVATE_KEY_LOCATION = "privatekey.key";
    public final static String PUBLIC_KEY_LOCATION = "publickey.pub";
    public final static int THREAD_DELAY = 1000;//how long to wait before sending key to other bluetooth device.
    public static final int MIN_RSSI = -70; // the minimum RSSI value for the signal to be considered within 6 feet.
    private static final int CHECK_STATUS_EVERY = 1000; //how often we want to run the function that makes sure that the scan is going OK.


    private NetworkFragment networkFragment;
    private boolean downloading = false; // used so that we don't download 2 things at once.
    private static MainActivity instance;
    public static Context getLocalContext(){
        return instance;
    }
    private BluetoothContactTracer2 mBluetoothContactTracer2;
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    //Messages from BluetoothContactTracer
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;




    Handler mHandler = new Handler(Looper.getMainLooper()){ //TODO: finish
        @Override
        public void handleMessage(Message msg) {
            final int index = msg.arg1;
            switch(msg.what){
                case BluetoothContactTracer2.MessageConstants.MESSAGE_BLUETOOTH_DONE:
                    UIbluetoothSuccsess();
                case BluetoothContactTracer2.MessageConstants.MESSAGE_BLUETOOTH_ERROR:
                    UIbluetoothError();
                case BluetoothContactTracer2.MessageConstants.WRITE_KEY:
                    new CountDownTimer(THREAD_DELAY, THREAD_DELAY) {
                        public void onFinish() {
                            Pair<Key,Key> keyPair= readKeyPair(getLocalContext());
                            byte[] pub = keyPair.second.getEncoded();
                            mBluetoothContactTracer2.write(pub, index);
                        }
                        public void onTick(long millisUntilFinished) {
                            // millisUntilFinished    The amount of time until finished.
                        }
                    }.start();
            }
        }

    };

    Handler repeatHandler = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Context context = this;
        instance = this; // save the Context for further use
        Log.i(TAG, "Activity has started");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        networkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), URL);


        if(!checkIfKeysExist(context)) { //generate a publickey and privatekey the first time the user opens the app.
            try {
                CoronaHandler.writeKeyPair(generateKeyPair(), context);
            } catch (IOException e) {
                e.printStackTrace();
                // TODO: notify users that there was a problem creating the keys.
            }
        }
        if (mBluetoothAdapter == null) { // If the device doesn't support bluetooth, tell that to the user
            Toast.makeText(context, getString(R.string.no_bluetooth), Toast.LENGTH_SHORT).show();
        }
        if (!mBluetoothAdapter.isEnabled()) { // enable bluetooth if it's not already enabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION); //TODO: explain to the user why this is needed.


        IntentFilter bluetoothUpdateFilter = new IntentFilter();
        bluetoothUpdateFilter.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothUpdateFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        bluetoothUpdateFilter.addAction(BluetoothDevice.ACTION_UUID);
        registerReceiver(mReceiver, bluetoothUpdateFilter);

        mBluetoothAdapter.startDiscovery();

        mBluetoothContactTracer2 = new BluetoothContactTracer2(mBluetoothAdapter, MY_UUID, DEVICE_BLUETOOTH_NAME, context, false, mHandler);
        mBluetoothContactTracer2.runAcceptThread();

        repeatHandler.postDelayed(new Runnable(){ //constantly starts the discovery if the device isn't discovering.
            public void run(){
                if(!mBluetoothAdapter.isDiscovering()){
                    mBluetoothAdapter.startDiscovery();
                }
                repeatHandler.postDelayed(this, CHECK_STATUS_EVERY);
            }
        }, CHECK_STATUS_EVERY);


    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void startDownload(String params, String requestType) {
        if (!downloading && networkFragment != null) {
            // Execute the async download.
            networkFragment.startDownload(params, requestType);
            downloading = true;
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                try {
                    String macAddress = device.getAddress();
                    ContactTraceRoomDatabase db = ContactTraceRoomDatabase.getDatabase(context);
                    ContactTraceDao dao = db.ContactTraceDao();
                    List<String> allAddresses = dao.getMacAddresses();
                    int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    if (!allAddresses.contains(macAddress) && rssi > MIN_RSSI) {
                        mBluetoothContactTracer2.runConnectThread(device, macAddress);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    };
                //Old code that I'm probably not going to use.
                /*
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // discovery has finished, give a call to fetchUuidsWithSdp on first device in list.
                if (!mDeviceList.isEmpty()) {
                    BluetoothDevice device = mDeviceList.remove(0);
                    boolean result = device.fetchUuidsWithSdp();
                }else{
                    mBluetoothAdapter.startDiscovery();//no device found, restart the discovery
                }
            } else if (BluetoothDevice.ACTION_UUID.equals(action)) {
                // This is when we can be assured that fetchUuidsWithSdp has completed.
                // So get the uuids and call fetchUuidsWithSdp on another device in list
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] uuidExtra = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                //Log.i(TAG, "DeviceExtra name - " + extraDevice.getName());
                if (uuidExtra != null) {
                    if(CoronaHandler.uuidsMatch(uuidExtra, MY_UUID)){
                        String macAddress = device.getAddress();
                        mBluetoothContactTracer2.runConnectThread(device, macAddress);
                        return;
                    }
                } else {
                    //Log.i(TAG, "uuidExtra is still null");
                }
                if (!mDeviceList.isEmpty()) {
                    BluetoothDevice next_device = mDeviceList.remove(0);
                    boolean result = next_device.fetchUuidsWithSdp();
                }else{
                    mBluetoothAdapter.startDiscovery();//no devices are valid, restarting discovery.
                }

                 */



    @Override
    public void updateFromDownload(String result) {
        // Update your UI here based on result of download.
        TextView info = (TextView) findViewById(R.id.infoBox);
        info.setText(result);
        Log.i(TAG, result);
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo;
    }

    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        // Do something when the network progresses, but nothing now.
    }

    @Override
    public void finishDownloading() {
        downloading = false;
        if (networkFragment != null) {
            networkFragment.cancelDownload();
        }
    }

    private String formatURLParam(String URLparam) throws UnsupportedEncodingException {
        return "&key=" + URLEncoder.encode(URLparam, "UTF-8");
    }


    /********************************** Status Updates ***********************************************************************************/

    public void UIbluetoothError(){ // Since bluetooth errors ocour all the time,
        //Toast.makeText(getApplicationContext(), R.string.bluetooth_error,
        //        Toast.LENGTH_LONG).show();
    }

    public void UIbluetoothSuccsess(){
        Toast.makeText(getApplicationContext(), R.string.bluetooth_sucsess,
                Toast.LENGTH_LONG).show();
    }

    /*********************************** Button Methods ***********************************************************************************/


    public void getInfoFromServer(View view){
        TextView info = (TextView) findViewById(R.id.infoBox);
        info.setText(R.string.loading);
        startDownload("", RequestTypes.GET);
    }

    public void uploadFakeKeyToServer(View view){
        try {
            KeyPair keys = CoronaHandler.generateKeyPair();
            Key pub = keys.getPublic();
            Key pvt = keys.getPrivate();
            try {
                byte[] signature = CoronaHandler.generateSignature(pvt);
                String signatureString = CoronaHandler.KeyToString(signature);
                startDownload(formatURLParam(signatureString), "POST");
            } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
                e.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public void uploadRealKeyToServer(View view){
        TextView info = (TextView) findViewById(R.id.infoBox);
        info.setText(R.string.loading);
        try{
            Context context = view.getContext();
            Pair<Key, Key> keys = readKeyPair(context);
            Key pvt = keys.first;
            Key pub = keys.second;
            byte[] signature = CoronaHandler.generateSignature(pvt);
            String signatureString = CoronaHandler.KeyToString(signature);
            startDownload(formatURLParam(signatureString), "POST");
        } catch (NoSuchAlgorithmException | IOException  | InvalidKeyException | SignatureException e) {
            e.printStackTrace();
        }
    }

    public void printDatabase(View view){
        try {
            Log.i(TAG, CoronaHandler.readContactTrace(view.getContext()).toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void addToDatabase(View view){
        Key key = generateKeyPair().getPublic();
        ContactTrace contactTrace = new ContactTrace(key.getEncoded(), "");
        CoronaHandler.writeKey(contactTrace, view.getContext());
    }
    public void generateKeys(View view){
        try {

            if(!checkIfKeysExist(view.getContext())){;
                CoronaHandler.writeKeyPair(generateKeyPair(), view.getContext());
            }else{
                Log.i(TAG, "Keys already exist!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void printKeys(View view){
        Pair<Key, Key> keys = readKeyPair(view.getContext());
        Key pvt = keys.first;
        Key pub = keys.second;
        Log.i(TAG, "Private Key: " + pvt.toString());
        Log.i(TAG, "Public Key: " + pub.toString());
    }
    public void createSignature(View view){
        try {
            Context context = view.getContext();
            if (checkIfKeysExist(context)) {
                Pair<Key, Key> keys = readKeyPair(context);
                Key pvt = keys.first;
                Key pub = keys.second;
                byte[] signature = CoronaHandler.generateSignature(pvt);
                writeToFile(signature, TEMP_SIGNATURE_LOCATION, context);
                Log.i(TAG, "Done");
            }else{
                Log.i(TAG, "Keys don't exist!");
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void verifyRealSignature(View view){
        try {
            Context context = view.getContext();
            Pair<Key, Key> keys = readKeyPair(context);
            Key pvt = keys.first;
            Key pub = keys.second;
            byte[] signature = CoronaHandler.readFile(TEMP_SIGNATURE_LOCATION, context);
            Log.i(TAG, "VerifyRealSignature: Signature is" + (verifySignature(pub, signature) ? "OK" : "Not OK"));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void verifyFakeSignature(View view){
        try {
            Context context = view.getContext();
            KeyPair keys = generateKeyPair();
            Key pvt = keys.getPrivate();
            Key pub = keys.getPublic();
            byte[] signature = CoronaHandler.readFile(TEMP_SIGNATURE_LOCATION, context);
            Log.i(TAG, "VerifyFakeSignature: Signature is " + (verifySignature(pub, signature) ? "OK" : "Not OK"));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void makeDiscoverable(View view){
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
        startActivity(discoverableIntent);

    }

    public void runDiscovery(View view){//TODO: make this automatic
        if(!mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.startDiscovery();
        }
    }

    public void checkIfInDiscovery(View view){
        if(mBluetoothAdapter.isDiscovering()){
            Toast.makeText(getApplicationContext(), "You are in discovery mode",
                    Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(getApplicationContext(), "you are not in discovery mode",
                    Toast.LENGTH_SHORT).show();

        }
    }

    public void resetAll(View view){
        Context context = view.getContext();
        ContactTraceRoomDatabase db = ContactTraceRoomDatabase.getDatabase(view.getContext());
        ContactTraceDao dao = db.ContactTraceDao();
        dao.deleteAll();
        File pk = new File(context.getFilesDir(), PUBLIC_KEY_LOCATION);
        if(!pk.delete()){
            Log.i(TAG, "error deleting file");
        }
        File pvt = new File(context.getFilesDir(), PRIVATE_KEY_LOCATION);
        if(!pvt.delete()){
            Log.i(TAG, "error deleting file");
        }
        try {
            CoronaHandler.writeKeyPair(generateKeyPair(), view.getContext());
            } catch (IOException e) {
            e.printStackTrace();
        }
        Toast.makeText(getApplicationContext(), "all information has been reset",
                Toast.LENGTH_SHORT).show();
    }
}
