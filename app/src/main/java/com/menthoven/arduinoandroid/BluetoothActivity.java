package com.menthoven.arduinoandroid;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class BluetoothActivity extends AppCompatActivity {


    BluetoothService bluetoothService;
    BluetoothDevice device;

    @Bind(R.id.edit_text)
    EditText editText;
    @Bind(R.id.send_button)
    Button sendButton;
    @Bind(R.id.chat_list_view)
    ListView chatListView;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.empty_list_item)
    TextView emptyListTextView;
    @Bind(R.id.toolbar_progress_bar)
    ProgressBar toolbalProgressBar;
    @Bind(R.id.coordinator_layout_bluetooth)
    CoordinatorLayout coordinatorLayout;

    MenuItem reconnectButton;
    ChatAdapter chatAdapter;

    Snackbar snackTurnOn;

    private boolean showMessagesIsChecked = true;
    private boolean autoScrollIsChecked = true;
    public static boolean showTimeIsChecked = true;

    @OnClick(R.id.send_button) void send() {
        // Send a item_message using content of the edit text widget
        String message = editText.getText().toString();
        if (message.trim().length() == 0) {
            editText.setError("Enter text first");
        } else {
            sendMessage(message);
            editText.setText("");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);


        ButterKnife.bind(this);

        editText.setError("Enter text first");

        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    send();
                    return true;
                }
                return false;
            }
        });

        snackTurnOn = Snackbar.make(coordinatorLayout, "Bluetooth turned off", Snackbar.LENGTH_INDEFINITE)
                .setAction("Turn On", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        enableBluetooth();
                    }
                });



        chatAdapter = new ChatAdapter(this);
        chatListView.setEmptyView(emptyListTextView);
        chatListView.setAdapter(chatAdapter);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setSupportActionBar(toolbar);

        myHandler handler = new myHandler(BluetoothActivity.this);


        assert getSupportActionBar() != null; // won't be null, lint error
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        device = getIntent().getExtras().getParcelable(Constants.EXTRA_DEVICE);

        bluetoothService = new BluetoothService(handler, device);

        setTitle(device.getName());
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        bluetoothService.connect();
        Log.d(Constants.TAG, "Connecting");
    }

    @Override protected void onStop() {
        super.onStop();
        if (bluetoothService != null) {
            bluetoothService.stop();
            Log.d(Constants.TAG, "Stopping");
        }

        unregisterReceiver(mReceiver);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                setStatus("None");
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


    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (bluetoothService.getState() != Constants.STATE_CONNECTED) {
            Snackbar.make(coordinatorLayout, "You are not connected", Snackbar.LENGTH_LONG)
                    .setAction("Connect", new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            reconnect();
                        }
                    }).show();
            return;
        } else {
            byte[] send = message.getBytes();
            bluetoothService.write(send);
        }
    }


    private static class myHandler extends Handler {
        private final WeakReference<BluetoothActivity> mActivity;

        public myHandler(BluetoothActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {

            final BluetoothActivity activity = mActivity.get();

            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case Constants.STATE_CONNECTED:
                            activity.setStatus("Connected");
                            activity.reconnectButton.setVisible(false);
                            activity.toolbalProgressBar.setVisibility(View.GONE);
                            break;
                        case Constants.STATE_CONNECTING:
                            activity.setStatus("Connecting");
                            activity.toolbalProgressBar.setVisibility(View.VISIBLE);
                            break;
                        case Constants.STATE_NONE:
                            activity.setStatus("Not Connected");
                            activity.toolbalProgressBar.setVisibility(View.GONE);
                            break;
                        case Constants.STATE_ERROR:
                            activity.setStatus("Error");
                            activity.reconnectButton.setVisible(true);
                            activity.toolbalProgressBar.setVisibility(View.GONE);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    ChatMessage messageWrite = new ChatMessage("Me", writeMessage);
                    activity.addMessageToAdapter(messageWrite);
                    break;
                case Constants.MESSAGE_READ:

                    String readMessage = (String) msg.obj;

                    if (readMessage != null && activity.showMessagesIsChecked) {
                        ChatMessage messageRead = new ChatMessage(activity.device.getName(), readMessage.trim());
                        activity.addMessageToAdapter(messageRead);

                    }
                    break;

                case Constants.MESSAGE_SNACKBAR:
                    Snackbar.make(activity.coordinatorLayout, msg.getData().getString(Constants.SNACKBAR), Snackbar.LENGTH_LONG)
                            .setAction("Connect", new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    activity.reconnect();
                                }
                            }).show();

                    break;
            }
        }


    }

    private void addMessageToAdapter(ChatMessage chatMessage) {
        chatAdapter.add(chatMessage);
        if (autoScrollIsChecked) scrollChatListViewToBottom();
    }

    private void scrollChatListViewToBottom() {
        chatListView.post(new Runnable() {
            @Override
            public void run() {
                // Select the last row so it will scroll into view...
                chatListView.smoothScrollToPosition(chatAdapter.getCount() - 1);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.bluetooth_menu, menu);
        reconnectButton = menu.findItem(R.id.action_reconnect);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                bluetoothService.stop();
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_reconnect:
                reconnect();
                return true;
            case R.id.action_clear:
                chatAdapter.clear();
                return true;
            case R.id.checkable_auto_scroll:
                autoScrollIsChecked = !item.isChecked();
                item.setChecked(autoScrollIsChecked);
                return true;
            case R.id.checkable_show_messages:
                showMessagesIsChecked = !item.isChecked();
                item.setChecked(showMessagesIsChecked);
                return true;
            case R.id.checkable_show_time:
                showTimeIsChecked = !item.isChecked();
                item.setChecked(showTimeIsChecked);
                chatAdapter.notifyDataSetChanged();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        snackTurnOn.show();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        if (snackTurnOn.isShownOrQueued()) snackTurnOn.dismiss();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        reconnect();
                }
            }
        }
    };

    private void setStatus(String status) {
        toolbar.setSubtitle(status);
    }

    private void enableBluetooth() {
        setStatus("Enabling Bluetooth");
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
    }

    private void reconnect() {
        reconnectButton.setVisible(false);
        bluetoothService.stop();
        bluetoothService.connect();
    }

}
