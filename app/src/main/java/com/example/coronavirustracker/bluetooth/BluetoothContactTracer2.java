package com.example.coronavirustracker.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import com.example.coronavirustracker.CoronaHandler;
import com.example.coronavirustracker.database.ContactTrace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothContactTracer2 {

    private static int MSG_SIZE = 2048;
    private static byte[] MESSAGE_DONE = new byte[MSG_SIZE];// Contains 2048 zeros. the message that will be sent when the program has received the other program's key, signaling that the connection may be terminated.
    private static byte[] MESSAGE_REQUEST_KEY; // the message to send to the other device to tell them that you haven't recieved their key yet, and want to recieve it. initialized in the constructor.
    private static int MAX_NUM_ATTEMPTS;


    private BluetoothAdapter mBluetoothAdapter;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private UUID mUUID;
    private String mName;
    private byte[] mKey;
    private Context mContext;
    private boolean mSecure;
    //private String mMacAddress;

    public static final String TAG = "BluetoothContactTracer2";

    public BluetoothContactTracer2(BluetoothAdapter bluetoothAdapter, UUID uuid, String name, byte[] key, Context context, boolean secure){
        mBluetoothAdapter = bluetoothAdapter;
        mUUID = uuid;
        mName = name;
        mKey = key;
        mContext = context;
        mSecure = secure;

        MESSAGE_REQUEST_KEY = new byte[MSG_SIZE]; //makes the MESSAGE_REQUEST_KEY constant to be what it's meant to be.
        MESSAGE_REQUEST_KEY[0] = 1;
    }

    public synchronized void runAcceptThread(){
        Log.i(TAG, "running accept Thread");
        if(mAcceptThread != null){
            mAcceptThread.cancel();
        }
        mAcceptThread = new AcceptThread();
        mAcceptThread.start();
    }

    public synchronized void runConnectThread(BluetoothDevice device){
        Log.i(TAG, "running connect Thread");
        if(mConnectThread != null){
            mConnectThread.cancel();
        }
        mConnectThread= new ConnectThread(device);
        mConnectThread.start();
    }

    public synchronized void runConnectedThread(BluetoothSocket socket){
        Log.i(TAG, "running connected Thread");
        if(mConnectedThread != null){
            return;
        }else{
            mConnectedThread = new ConnectedThread(socket);
            mConnectedThread.start();
        }
    }

    public synchronized void restartBluetooth(){
        Log.i(TAG, "keys have been shared, and bluetooth is being restarted.");
        if(mAcceptThread != null){
            mAcceptThread.cancel();
            mAcceptThread= null;
        }
        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread= null;
        }
        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread= null;
        }
        runAcceptThread();
    }

    private class AcceptThread extends Thread{
        private final BluetoothServerSocket mmServerSocket;
        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                if(mSecure){
                    tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(mName, mUUID);
                }else {
                    tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(mName, mUUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    Log.i(TAG, "from acceptThread: connection pending");
                    socket = mmServerSocket.accept();
                    Log.i(TAG, "from acceptThread: connection accepted");
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    runConnectedThread(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Could not close server socket.");
                    }
                    break;
                }
            }
        }

        public void cancel(){
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket close() of server failed", e);
            }

        }
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                if(mSecure){
                    tmp = mmDevice.createRfcommSocketToServiceRecord(mUUID);
                }else {
                    tmp = mmDevice.createInsecureRfcommSocketToServiceRecord(mUUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                Log.i(TAG, "connectThread pending");

                mmSocket.connect();
                Log.i(TAG, "connectThread sucsess");

            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }
            runConnectedThread(mmSocket);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }

    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        int numAttempts; //stores the number of times the key has been sent. If the key was attempted to be sent more than 3 times, there's probably an error with the other device, so we should end the connection.
        boolean hasReceivedKey; // stores wether or not the program has already written a key, in order to prevents the key from being written twice.

        public ConnectedThread(BluetoothSocket socket) {
            hasReceivedKey = false;
            numAttempts = 0;

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            write(mKey);
            mmBuffer = new byte[MSG_SIZE];
            int numBytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    if(mmBuffer == MESSAGE_DONE){//the other app is telling you that we need to end the connection.
                        if(hasReceivedKey){
                            break;
                        }else{
                            write(MESSAGE_REQUEST_KEY);
                        }
                    }else if(mmBuffer == MESSAGE_REQUEST_KEY){
                        if(numAttempts < MAX_NUM_ATTEMPTS) {
                            write(mKey);
                            numAttempts++;
                        }else{
                            //the other program has already asked us for our key 3 times, and something is probably going wrong. Disconnect.
                            Log.e(TAG, "Other connected bluetooth device has requested our key 3 times, but nothing happened. Terminating connection. ");
                            break;
                        }
                    }else{ // the other device has sent a key.
                        ContactTrace mmContactTrace = new ContactTrace(mKey); //TODO: incorperate MAC address
                        CoronaHandler.writeKey(mmContactTrace, mContext);
                    }
                    // TODO: Send the obtained bytes to the UI activity.
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
            restartBluetooth();

            //TODO: also incorperate a timeout if both devices can't transfer keys fast enough.
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                // TODO: Share the sent message with the UI activity.
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);
                // TODO: Send a failure message back to the activity.
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }



    }



}
