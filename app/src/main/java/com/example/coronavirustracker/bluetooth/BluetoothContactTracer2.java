package com.example.coronavirustracker.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothContactTracer2 {

    private BluetoothAdapter mBluetoothAdapter;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private UUID mUUID;
    private String mName;
    private byte[] mKey;
    private Context mContext;
    private boolean mSecure;
    private String mMacAddress;

    public static final String TAG = "BluetoothContactTracer2";

    public BluetoothContactTracer2(BluetoothAdapter bluetoothAdapter, UUID uuid, String name, byte[] key, Context context, boolean secure, String macAdress){
        mBluetoothAdapter = bluetoothAdapter;
        mUUID = uuid;
        mName = name;
        mKey = key;
        mContext = context;
        mSecure = secure;
        mMacAddress = macAdress;
    }

    public void runAcceptThread(){
        Log.i(TAG, "running accept Thread");
        if(mAcceptThread != null){
            mAcceptThread.cancel();
        }
        mAcceptThread = new AcceptThread();
        mAcceptThread.start();
    }

    public void runConnectThread(BluetoothDevice device){
        Log.i(TAG, "running connect Thread");
        if(mConnectThread != null){
            mConnectThread.cancel();
        }
        mConnectThread= new ConnectThread(device);
        mConnectThread.start();
    }

    public void runConnectedThread(BluetoothSocket socket){
        Log.i(TAG, "running connected Thread");
        if(mConnectedThread != null){
            return;
        }else{
            mConnectedThread = new ConnectedThread(socket);
            mConnectedThread.start();
        }
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

        public ConnectedThread(BluetoothSocket socket) {
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
            mmBuffer = new byte[2048];
            int numBytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    Log.i(TAG, mmBuffer.toString());
                    // TODO: Send the obtained bytes to the UI activity.
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
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


    }



}
