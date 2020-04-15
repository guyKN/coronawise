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
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Toast;

import com.example.coronavirustracker.bluetooth.BluetoothContactTracer;
import com.example.coronavirustracker.bluetooth.BluetoothContactTracer2;
import com.example.coronavirustracker.database.ContactTrace;
import com.example.coronavirustracker.internet.DownloadCallback;
import com.example.coronavirustracker.internet.NetworkFragment;

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
import java.util.Date;
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

    private NetworkFragment networkFragment;
    private boolean downloading = false; // used so that we don't download 2 things at once.
    private static MainActivity instance;
    public static Context getLocalContext(){
        return instance;
    }
    private BluetoothContactTracer2 mBluetoothContactTracer2;
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private final Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) { // TODO: this has the potential for leak. Fix that.
            Log.i(TAG, message.toString());
            return true;
        }
    });

    //Messages from BluetoothContactTracer
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    //Key names recived from ContactTracer
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";




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


        IntentFilter deviceFoundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(deviceFoundReceiver, deviceFoundFilter);
        mBluetoothAdapter.startDiscovery();



        try {
            Pair<Key,Key> keyPair= readKeyPair(context);
            byte[] pub = keyPair.second.getEncoded();
            mBluetoothContactTracer2 = new BluetoothContactTracer2(mBluetoothAdapter, MY_UUID, DEVICE_BLUETOOTH_NAME, pub, context, false);
            mBluetoothContactTracer2.runAcceptThread();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(deviceFoundReceiver);
        super.onDestroy();
    }

    private void startDownload(String params, String requestType) {
        if (!downloading && networkFragment != null) {
            // Execute the async download.
            networkFragment.startDownload(params, requestType);
            downloading = true;
        }
    }

    private final BroadcastReceiver deviceFoundReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device.getName() != null) {
                    mBluetoothContactTracer2.runConnectThread(device);
                    Log.i(TAG, "new device found " + device.toString() + ". Device name " + device.getName());
                    mBluetoothAdapter.cancelDiscovery();
                }else{
                    mBluetoothAdapter.startDiscovery();
                }

            }
        }
    };




    @Override
    public void updateFromDownload(String result) {
        // Update your UI here based on result of download.
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



    /*********************************** Button Methods ***********************************************************************************/


    public void getInfoFromServer(View view){
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
        try{
            Context context = view.getContext();
            Pair<Key, Key> keys = readKeyPair(context);
            Key pvt = keys.first;
            Key pub = keys.second;
            byte[] signature = CoronaHandler.generateSignature(pvt);
            String signatureString = CoronaHandler.KeyToString(signature);
            CoronaHandler.writeKey(new ContactTrace(pub, "blank_MAC"), context);
            startDownload(formatURLParam(signatureString), "POST");

        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException | InvalidKeyException | SignatureException e) {
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
        ContactTrace contactTrace = new ContactTrace(key.getEncoded(), "blank_MAC");
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
        try {
            Pair<Key, Key> keys = readKeyPair(view.getContext());
            Key pvt = keys.first;
            Key pub = keys.second;
            Log.i(TAG, "Private Key: " + pvt.toString());
            Log.i(TAG, "Public Key: " + pub.toString());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
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
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
            startActivity(discoverableIntent);
        }

    }

    public void runDiscovery(View view){//TODO: make this automatic
        if(!mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.startDiscovery();
        }
    }

}
