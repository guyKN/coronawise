/****************
This class is not being used. 
*****************/
package com.example.coronavirustracker.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.coronavirustracker.MainActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothContactTracer {
    //General consants
    private final String TAG = MainActivity.TAG;

    //properties of the class
    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private Context mContext;
    private UUID mUUID;
    private String mName; // the name of this bluetooth device shown when connecting
    private byte[] mPublicKey;

    //Different states of the class
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device


    public BluetoothContactTracer(Context context, Handler handler, String name,UUID uuid, byte[] publicKey){
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mHandler = handler;
        this.mContext = context;
        this.mState = STATE_NONE;
        this.mUUID = uuid;
        this.mName = name;
        this.mPublicKey = publicKey;
    }

    private synchronized void setState(int state){
        Log.d(MainActivity.TAG, "State changed to " + state);
        this.mState = state;
        mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget(); // sends the new state to the main activity in order to update the UI
    }
    public synchronized int getBluetoothState() {
        return mState;
    }

    // starts to search for nearby devices.
    public synchronized void start(){
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        setState(STATE_LISTEN);

        mAcceptThread = new AcceptThread();
        mAcceptThread.start();

    }

    public synchronized void connect(BluetoothDevice device){
        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(STATE_CONNECTED);
        write(mPublicKey);//TODO: remove this, as it's only for debugging

    }

    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }


    public synchronized void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
        }
        setState(STATE_NONE);
    }

    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        // Start the service over to restart listening mode
        BluetoothContactTracer.this.start();
    }

    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        // Start the service over to restart listening mode
        BluetoothContactTracer.this.start();
    }










    private class AcceptThread extends Thread {
        private BluetoothServerSocket mmServerSocket;
        public AcceptThread(){
            BluetoothServerSocket tmp;
            try{
                mmServerSocket =  mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(mName,mUUID);
            } catch (IOException e) {
                Log.i(TAG, "Error ocoured while trying to use listenUsingInsecureRfcommWithServiceRecord. ");
                e.printStackTrace();
            }
        }
        public void run(){
            BluetoothSocket socket = null;
            while(getBluetoothState() != STATE_CONNECTED) {
                try {
                    socket = mmServerSocket.accept(); //this is a blocking call, meaning it will only return if there's an error or if it's sucsessful;
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.i(TAG, "socket accept() failed");
                    break;
                }

                //Complete the connection if the connection was accepted
                if(socket != null){
                    switch(getBluetoothState()){
                        case STATE_LISTEN: // the user has not yet tried to connect
                        case STATE_CONNECTING: //As expected. Now that we have a connection and a thing to connect to. We can now become connected.
                            connected(socket, socket.getRemoteDevice());
                        case STATE_NONE: //unexpected
                        case STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }
                            break;
                    }
                }
            }
        }
        public void cancel() {
            Log.d(TAG, "Socket  Canceled.");
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket close() of server failed", e);
            }
        }
    }


    private class ConnectThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(mUUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery(); //TODO: is this right
            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }
            // Reset the ConnectThread because we're done
            synchronized (BluetoothContactTracer.this) {
                mConnectThread = null;
            }
            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }

        }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run(){
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[2048];
            int bytes;
            while(true){
                try{
                    bytes = mmInStream.read(buffer); //waits until the input stream says something, then sends it the activity.
                    mHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothContactTracer.this.start();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
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
