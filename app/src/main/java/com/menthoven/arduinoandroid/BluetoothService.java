package com.menthoven.arduinoandroid;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by da Ent on 1-11-2015.
 */
public class BluetoothService {

    private Handler myHandler;
    private int state;

    BluetoothDevice myDevice;

    ConnectThread connectThread;
    ConnectedThread connectedThread;

    public BluetoothService(Handler handler, BluetoothDevice device) {
        state = Constants.STATE_NONE;
        myHandler = handler;
        myDevice = device;
    }

    public synchronized void connect() {
        Log.d(Constants.TAG, "Connecting to: " + myDevice.getName() + " - " + myDevice.getAddress());
        // Start the thread to connect with the given device

        setState(Constants.STATE_CONNECTING);
        connectThread = new ConnectThread(myDevice);
        connectThread.start();
    }

    public synchronized void stop() {
        cancelConnectThread();
        cancelConnectedThread();
        setState(Constants.STATE_NONE);
    }

    private synchronized void setState(int state) {
        Log.d(Constants.TAG, "setState() " + this.state + " -> " + state);
        this.state = state;
        // Give the new state to the Handler so the UI Activity can update
        myHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return state;
    }


    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(Constants.TAG, "connected to: " + device.getName());

        cancelConnectThread();
        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        setState(Constants.STATE_CONNECTED);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        Log.e(Constants.TAG, "Connection Failed");
        // Send a failure item_message back to the Activity
        Message msg = myHandler.obtainMessage(Constants.MESSAGE_SNACKBAR);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.SNACKBAR, "Unable to connect");
        msg.setData(bundle);
        myHandler.sendMessage(msg);
        setState(Constants.STATE_ERROR);
        cancelConnectThread();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        Log.e(Constants.TAG, "Connection Lost");
        // Send a failure item_message back to the Activity
        Message msg = myHandler.obtainMessage(Constants.MESSAGE_SNACKBAR);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.SNACKBAR, "Cconnection was lost");
        msg.setData(bundle);
        myHandler.sendMessage(msg);
        setState(Constants.STATE_ERROR);
        cancelConnectedThread();
    }

    private void cancelConnectThread() {
        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
    }

    private void cancelConnectedThread() {
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (state != Constants.STATE_CONNECTED) {
                Log.e(Constants.TAG, "Trying to send but not connected");
                return;
            }
            r = connectedThread;
        }

        // Perform the write unsynchronized
        r.write(out);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                UUID uuid = Constants.myUUID;
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e(Constants.TAG, "Create RFcomm socket failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                Log.e(Constants.TAG, "Unable to connect", connectException);
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(Constants.TAG, "Unable to close() socket during connection failure", closeException);
                }
                connectionFailed();
                return;
            }

            synchronized (BluetoothService.this) {
                connectThread = null;
            }

            // Do work to manage the connection (in a separate thread)
            connected(mmSocket, mmDevice);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(Constants.TAG, "Close() socket failed", e);
            }
        }
    }


    public class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(Constants.TAG, "Temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(Constants.TAG, "Begin connectedThread");
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            StringBuilder readMessage = new StringBuilder();

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {

                    bytes = mmInStream.read(buffer);
                    String read = new String(buffer, 0, bytes);
                    readMessage.append(read);

                    if (read.contains("\n")) {

                        myHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, readMessage.toString()).sendToTarget();
                        readMessage.setLength(0);
                    }

                } catch (IOException e) {

                    Log.e(Constants.TAG, "Connection Lost", e);
                    connectionLost();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                myHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, bytes).sendToTarget();
            } catch (IOException e) {
                Log.e(Constants.TAG, "Exception during write", e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(Constants.TAG, "close() of connect socket failed", e);}
        }
    }

}
