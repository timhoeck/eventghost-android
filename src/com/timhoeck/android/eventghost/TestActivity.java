package com.timhoeck.android.eventghost;

import com.timhoeck.android.eventghost.Eventghost.EventghostListener;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.*;

public class TestActivity extends Activity {
	private Eventghost eg = new Eventghost(new EventListener());
	private TextView lblStatus;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);


		Button btnStart = (Button) findViewById(R.id.btnStart);
		btnStart.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				//start server
				final EditText txtServerPort = (EditText) findViewById(R.id.txtServerPort);
				try {
					eg.startListening(TestActivity.this, Integer.valueOf(txtServerPort.getText().toString()));
				} catch (Exception e) {
					//use default port
					eg.startListening(TestActivity.this);
				}
				
			}});
		
		Button btnStop = (Button) findViewById(R.id.btnStop);
		btnStop.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				//start server
				eg.stopListening(TestActivity.this);
			}});
	
		final EditText txtHost = (EditText) findViewById(R.id.txtHost);
		final EditText txtPort = (EditText) findViewById(R.id.txtPort);
		final EditText txtPassword = (EditText) findViewById(R.id.txtPassword);
		final EditText txtEvent = (EditText) findViewById(R.id.txtEvent);
		final EditText txtPayload = (EditText) findViewById(R.id.txtPayload);
		final EditText txtServerPassword = (EditText) findViewById(R.id.txtServerPassword);
		lblStatus = (TextView) findViewById(R.id.lblStatus);

		final CheckBox chkShowPass = (CheckBox) findViewById(R.id.chkShowPass);
		chkShowPass.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (isChecked) {
					txtPassword.setTransformationMethod(null);
					txtServerPassword.setTransformationMethod(null);
				} else {
					txtPassword.setTransformationMethod(new PasswordTransformationMethod());
					txtServerPassword.setTransformationMethod(new PasswordTransformationMethod());	
				}
			}});

		Button btnSend = (Button) findViewById(R.id.btnSend);
		btnSend.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				//send example event
				eg.useServer(txtHost.getText().toString(), Integer.valueOf(txtPort.getText().toString()), txtPassword.getText().toString());
				eg.sendEvent(txtEvent.getText().toString(), txtPayload.getText().toString());
			}});
	}
	
	
	public class EventListener implements EventghostListener {

		public void onReceivedEvent(String event, String payload) {
			Toast.makeText(getApplicationContext(), "Received Event: " + event + " with payload " + payload, Toast.LENGTH_SHORT).show();
		}

		public void onServerStatusChanged(String status) {
			lblStatus.setText("Status: " + status);
		}
	
	}
}