package com.menthoven.arduinoandroid;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemClick;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;

    BluetoothDevicesAdapter bluetoothDevicesAdapter;

    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.devices_list_view)
    ListView devicesListView;
    @Bind(R.id.empty_list_item)
    TextView emptyListTextView;
    @Bind(R.id.toolbar_progress_bar)
    ProgressBar toolbarProgressCircle;
    @Bind(R.id.coordinator_layout_main)
    CoordinatorLayout coordinatorLayout;

    @OnClick(R.id.search_button) void search() {

        if (bluetoothAdapter.isEnabled()) {
            // Bluetooth enabled
            startSearching();
        } else {

            enableBluetooth();
        }
    }

    private void enableBluetooth() {
        setStatus("Enabling Bluetooth");
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
    }

    @OnItemClick(R.id.devices_list_view) void onItemClick(int position) {
        setStatus("Asking to connect");
        final BluetoothDevice device = bluetoothDevicesAdapter.getItem(position);

        new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setTitle("Connect")
                .setMessage("Do you want to connect to: " + device.getName() + " - " + device.getAddress())
                .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Log.d(Constants.TAG, "Opening new Activity");
                        bluetoothAdapter.cancelDiscovery();
                        toolbarProgressCircle.setVisibility(View.INVISIBLE);

                        Intent intent = new Intent(MainActivity.this, BluetoothActivity.class);

                        intent.putExtra(Constants.EXTRA_DEVICE, device);

                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        setStatus("Cancelled connection");
                        Log.d(Constants.TAG, "Cancelled ");
                    }
                }).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
        setSupportActionBar(toolbar);

        setStatus("None");

        bluetoothDevicesAdapter = new BluetoothDevicesAdapter(this);

        devicesListView.setAdapter(bluetoothDevicesAdapter);
        devicesListView.setEmptyView(emptyListTextView);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {

            Log.e(Constants.TAG, "Device has no bluetooth");
            new AlertDialog.Builder(MainActivity.this)
                    .setCancelable(false)
                    .setTitle("No Bluetooth")
                    .setMessage("Your device has no bluetooth")
                    .setPositiveButton("Close app", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            Log.d(Constants.TAG, "App closed");
                            finish();
                        }
                    }).show();

        }
    }

    @Override protected void onStart() {
        super.onStart();

        Log.d(Constants.TAG, "Registering receiver");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
    }

    @Override protected void onStop() {
        super.onStop();
        Log.d(Constants.TAG, "Receiver unregistered");
        unregisterReceiver(mReceiver);
    }


    private void setStatus(String status) {
        toolbar.setSubtitle(status);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                startSearching();
            } else {
                setStatus("Error");
                Snackbar.make(coordinatorLayout, "Failed to enable bluetooth", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Try Again", new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                enableBluetooth();
                            }
                        }).show();
            }
        }

    }

    private void startSearching() {
        if (bluetoothAdapter.startDiscovery()) {
            toolbarProgressCircle.setVisibility(View.VISIBLE);
            setStatus("Searching for devices");
        } else {
            setStatus("Error");
            Snackbar.make(coordinatorLayout, "Failed to start searching", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Try Again", new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            startSearching();
                        }
                    }).show();
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                device.fetchUuidsWithSdp();

                if (bluetoothDevicesAdapter.getPosition(device) == -1) {
                    // -1 is returned when the item is not in the adapter
                    bluetoothDevicesAdapter.add(device);
                    bluetoothDevicesAdapter.notifyDataSetChanged();
                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                toolbarProgressCircle.setVisibility(View.INVISIBLE);
                setStatus("None");

            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Snackbar.make(coordinatorLayout, "Bluetooth turned off", Snackbar.LENGTH_INDEFINITE)
                                .setAction("Turn on", new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        enableBluetooth();
                                    }
                                }).show();
                        break;
                }
            }
        }
    };
}


