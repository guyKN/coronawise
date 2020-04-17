package com.example.coronavirustracker.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.coronavirustracker.CoronaHandler;
import com.example.coronavirustracker.MainActivity;
import com.example.coronavirustracker.database.ContactTrace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BluetoothContactTracer2 { // TODO: ensure errors are right

    public interface MessageConstants {
        int MESSAGE_BLUETOOTH_DONE = 0;
        int WRITE_KEY = 1;
        int MESSAGE_BLUETOOTH_ERROR = 2;
    }

    private static int MSG_SIZE = 2048;



    private AcceptThread mAcceptThread;
    private List<ConnectThread> mConnectThreads = new ArrayList<>();
    private List<ConnectedThread> mConnectedThreads = new ArrayList<>();

    private BluetoothAdapter mBluetoothAdapter;
    private UUID mUUID;
    private String mName;
    private Context mContext;
    private boolean mSecure;
    private Handler mHandler;
    //private String mMacAddress;

    public static final String TAG = "BluetoothContactTracer2";

    public BluetoothContactTracer2(BluetoothAdapter bluetoothAdapter, UUID uuid, String name, Context context, boolean secure, Handler handler) {
        mBluetoothAdapter = bluetoothAdapter;
        mUUID = uuid;
        mName = name;
        mContext = context;
        mSecure = secure;
        mHandler = handler;
    }

    public synchronized void runAcceptThread() {
        Log.i(TAG, "running accept Thread");
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
        }
        mAcceptThread = new AcceptThread();
        mAcceptThread.start();
    }

    public synchronized void runConnectThread(BluetoothDevice device, String macAddress) {
        Log.i(TAG, "running connect Thread");
        ConnectThread connectThread = new ConnectThread(device, macAddress);
        connectThread.start();
        mConnectThreads.add(connectThread);
    }

    public synchronized void runConnectedThread(BluetoothSocket socket, String macAddress) {
        Log.i(TAG, "running connected Thread");
        int index = mConnectedThreads.size();
        ConnectedThread connectedThread = new ConnectedThread(socket, macAddress, index);
        connectedThread.start();
        mConnectedThreads.add(connectedThread);
    }

    public synchronized void restartBluetooth() {
        Log.i(TAG, "keys have been shared, and bluetooth is being restarted.");
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        for (ConnectThread connectThread :mConnectThreads) {
            connectThread.cancel();
        }
        mConnectThreads.clear();

        for (ConnectedThread connectedThread :mConnectedThreads) {
            connectedThread.cancel();
        }
        mConnectedThreads.clear();
        mBluetoothAdapter.startDiscovery();
        runAcceptThread();
    }

    public void write(byte[] out, int index) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        try {
            synchronized (this) {
                try{
                    r = mConnectedThreads.get(index);
                }catch (IndexOutOfBoundsException e){
                    return;
                }
            }
            // Perform the write unsynchronized
            r.write(out);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private boolean shouldLoop = true;
        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                if (mSecure) {
                    tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(mName, mUUID);
                } else {
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
            while (shouldLoop) {
                try {
                    Log.i(TAG, "from acceptThread: connection pending");
                    socket = mmServerSocket.accept();
                    Log.i(TAG, "from acceptThread: connection accepted");
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    mHandler.obtainMessage(MessageConstants.MESSAGE_BLUETOOTH_ERROR).sendToTarget();
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    runConnectedThread(socket, "");
                }
            }
            runAcceptThread();
        }

        public void cancel() {
            try {
                shouldLoop = false;
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket close() of server failed", e);
                mHandler.obtainMessage(MessageConstants.MESSAGE_BLUETOOTH_ERROR).sendToTarget();
            }

        }
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mmMacAddress;

        public ConnectThread(BluetoothDevice device, String macAddress) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;
            mmMacAddress = macAddress;
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                if (mSecure) {
                    tmp = mmDevice.createRfcommSocketToServiceRecord(mUUID);
                } else {
                    tmp = mmDevice.createInsecureRfcommSocketToServiceRecord(mUUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
                mHandler.obtainMessage(MessageConstants.MESSAGE_BLUETOOTH_ERROR).sendToTarget();
            }
            mmSocket = tmp;
        }

        public void run() {
            // No need to cancel the discovery, since we are connecting multiple devices at the same time.
            //mBluetoothAdapter.cancelDiscovery();
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
                    mHandler.obtainMessage(MessageConstants.MESSAGE_BLUETOOTH_ERROR).sendToTarget();
                }
                return;
            }
            runConnectedThread(mmSocket, mmMacAddress);
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
        private String mmMacAddress;
        public int mIndex;

        int numAttempts; //stores the number of times the key has been sent. If the key was attempted to be sent more than 3 times, there's probably an error with the other device, so we should end the connection.
        boolean hasReceivedKey; // stores wether or not the program has already written a key, in order to prevents the key from being written twice.

        public ConnectedThread(BluetoothSocket socket, String macAddress, int index) {
            mIndex = index;
            mmMacAddress = macAddress;
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
                mHandler.obtainMessage(MessageConstants.MESSAGE_BLUETOOTH_ERROR).sendToTarget();
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
                mHandler.obtainMessage(MessageConstants.MESSAGE_BLUETOOTH_ERROR).sendToTarget();
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
        mHandler.obtainMessage(
                MessageConstants.WRITE_KEY, mIndex, 0
        ).sendToTarget();
            mmBuffer = new byte[MSG_SIZE];
            int numBytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs.
            try {
                // Read from the InputStream.
                numBytes = mmInStream.read(mmBuffer);
                ContactTrace mmContactTrace = new ContactTrace(mmBuffer, mmMacAddress);
                CoronaHandler.writeKey(mmContactTrace, mContext);
                Log.i(TAG, "Wrote Key");
                mHandler.obtainMessage(MessageConstants.MESSAGE_BLUETOOTH_DONE).sendToTarget();
                Thread.sleep(MainActivity.THREAD_DELAY);
            } catch (IOException | InterruptedException e) {
                Log.d(TAG, "Input stream was disconnected", e);
                mHandler.obtainMessage(MessageConstants.MESSAGE_BLUETOOTH_ERROR).sendToTarget();
            }
            //break;

        }
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                // TODO: Share the sent message with the UI activity.
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);
                e.printStackTrace();
                mHandler.obtainMessage(MessageConstants.MESSAGE_BLUETOOTH_ERROR).sendToTarget();
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }



        //TODO: also incorperate a timeout if both devices can't transfer keys fast enough.
    }


}














                    /*
                    if(mmBuffer == MESSAGE_DONE){//the other app is telling you that we need to end the connection.
                        if(hasReceivedKey){
                            mHandler.obtainMessage(
                                    MessageConstants.MESSAGE_BLUETOOTH_DONE
                            ).sendToTarget();
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
                        ContactTrace mmContactTrace = new ContactTrace(mKey, mmMacAddress);
                        CoronaHandler.writeKey(mmContactTrace, mContext);
                        Log.i(TAG, "Wrote Key");
                        write(MESSAGE_DONE);
                    }
                    */
