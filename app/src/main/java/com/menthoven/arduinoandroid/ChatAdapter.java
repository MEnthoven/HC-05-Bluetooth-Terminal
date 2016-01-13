package com.menthoven.arduinoandroid;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by da Ent on 28/11/2015.
 */
public class ChatAdapter extends ArrayAdapter<ChatMessage> {

    // View lookup cache
    static class ViewHolder {

        @Bind(R.id.time_text_view) TextView time;
        @Bind(R.id.device_text_view) TextView device;
        @Bind(R.id.message_text_view) TextView message;

        public ViewHolder(View view) {
            ButterKnife.bind(this, view);
        }
    }

    public ChatAdapter(Context context) {
        super(context, 0);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        ChatMessage chatMessage = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        ViewHolder viewHolder; // view lookup cache stored in tag
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.item_message, parent, false);
            viewHolder = new ViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }


        // Populate the data into the template view using the data object
        if (BluetoothActivity.showTimeIsChecked) {
            viewHolder.time.setText(chatMessage.getTime());
        } else {
            viewHolder.time.setText("");
        }
        viewHolder.device.setText(chatMessage.getDevice().concat(":"));
        viewHolder.message.setText(chatMessage.getMessage());
        // Return the completed to render on screen
        return convertView;
    }
}