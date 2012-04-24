eventghost-android
==================

Eventghost for Android allow you to:
 - Communicate with EventGhost servers running the Network Event Receiver plugin
 - Act as an EventGhost server and receive events
 
For more information on EventGhost, see:
http://eventghost.org


When adding to your project, make sure to:

AndroidManifest.xml:
 - Declare service: Eventghost$EventghostService
 - Add permission: android.permission.INTERNET 

To connect to a server and send events:

public onCreate(Bundle bundle) {
	Eventghost eg = new Eventghost();
	String host = "192.168.1.100";
	String pw = "password";
	eg.connect(host, pw);
	eg.sendEvent("myEvent", "myPayload");	
}

To start listening (Android device becomes Eventghost server):
public onCreate(Bundle bundle) {
	Eventghost eg = new Eventghost();
	eg.startListening();
}

public onReceivedEvent(String event, String payload) {
	Log.d(TAG, "Received Event: " + event + " with payload: " + payload);
}
