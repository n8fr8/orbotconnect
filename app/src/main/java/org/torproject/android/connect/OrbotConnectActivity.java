/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - https://guardianproject.info */
/* See LICENSE for licensing information */

package org.torproject.android.connect;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.torproject.android.connect.R;
import org.torproject.android.service.OrbotConstants;
import org.torproject.android.service.util.Prefs;
import org.torproject.android.service.TorService;
import org.torproject.android.service.TorServiceConstants;
import org.torproject.android.service.util.TorServiceUtils;
import org.torproject.android.settings.SettingsPreferences;
import org.torproject.android.ui.AppManager;
import org.torproject.android.ui.ImageProgressView;
import org.torproject.android.vpn.VPNEnableActivity;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.lzyzsd.circleprogress.DonutProgress;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class OrbotConnectActivity extends Activity
        implements OrbotConstants, OnLongClickListener {

    /* Useful UI bits */
    private TextView lblStatus = null; //the main text display widget
    private ImageProgressView imgStatus = null; //the main touchable image for activating Orbot

	private Button mBtnStart = null;
    private DonutProgress mProgress = null;

    /* Some tracking bits */
    private String torStatus = null; //latest status reported from the tor service
    private Intent lastStatusIntent;  // the last ACTION_STATUS Intent received

    private SharedPreferences mPrefs = null;

    private boolean autoStartFromIntent = false;
    
    private final static int REQUEST_VPN = 8888;
    private final static int REQUEST_SETTINGS = 0x9874;
    private final static int REQUEST_VPN_APPS_SELECT = 8889;

    // message types for mStatusUpdateHandler
    private final static int STATUS_UPDATE = 1;
    private static final int MESSAGE_TRAFFIC_COUNT = 2;

	public final static String INTENT_ACTION_REQUEST_HIDDEN_SERVICE = "org.torproject.android.REQUEST_HS_PORT";
	public final static String INTENT_ACTION_REQUEST_START_TOR = "org.torproject.android.START_TOR";	

    //this is needed for backwards compat back to Android 2.3.*
    @SuppressLint("NewApi")
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs)
    {
        if(Build.VERSION.SDK_INT >= 11)
          return super.onCreateView(parent, name, context, attrs);
        return null;
    }



    /**
     * Called when the activity is first created.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = TorServiceUtils.getSharedPrefs(getApplicationContext());

        /* Create the widgets before registering for broadcasts to guarantee
         * that the widgets exist when the status updates try to update them */
    	doLayout();

    	/* receive the internal status broadcasts, which are separate from the public
    	 * status broadcasts to prevent other apps from sending fake/wrong status
    	 * info to this app */
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.ACTION_STATUS));
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.LOCAL_ACTION_BANDWIDTH));
        lbm.registerReceiver(mLocalBroadcastReceiver,
                new IntentFilter(TorServiceConstants.LOCAL_ACTION_LOG));
	}

	private void sendIntentToService(final String action) {

		Intent torService = new Intent(OrbotConnectActivity.this, TorService.class);
        torService.setAction(action);
        startService(torService);

	}

    private void stopTor() {

        enableVPN(false);

        requestTorStatus();

        Intent torService = new Intent(OrbotConnectActivity.this, TorService.class);
        stopService(torService);

    }

    /**
     * The state and log info from {@link TorService} are sent to the UI here in
     * the form of a local broadcast. Regular broadcasts can be sent by any app,
     * so local ones are used here so other apps cannot interfere with Orbot's
     * operation.
     */
    private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            if (action.equals(TorServiceConstants.LOCAL_ACTION_LOG)) {
                Message msg = mStatusUpdateHandler.obtainMessage(STATUS_UPDATE);
                msg.obj = intent.getStringExtra(TorServiceConstants.LOCAL_EXTRA_LOG);
                msg.getData().putString("status", intent.getStringExtra(TorServiceConstants.EXTRA_STATUS));
                mStatusUpdateHandler.sendMessage(msg);

            } else if (action.equals(TorServiceConstants.LOCAL_ACTION_BANDWIDTH)) {
                long upload = intent.getLongExtra("up", 0);
                long download = intent.getLongExtra("down", 0);
                long written = intent.getLongExtra("written", 0);
                long read = intent.getLongExtra("read", 0);

                Message msg = mStatusUpdateHandler.obtainMessage(MESSAGE_TRAFFIC_COUNT);
                msg.getData().putLong("download", download);
                msg.getData().putLong("upload", upload);
                msg.getData().putLong("readTotal", read);
                msg.getData().putLong("writeTotal", written);
                msg.getData().putString("status", intent.getStringExtra(TorServiceConstants.EXTRA_STATUS));

                mStatusUpdateHandler.sendMessage(msg);

            } else if (action.equals(TorServiceConstants.ACTION_STATUS)) {
                lastStatusIntent = intent;
                
                Message msg = mStatusUpdateHandler.obtainMessage(STATUS_UPDATE);
                msg.getData().putString("status", intent.getStringExtra(TorServiceConstants.EXTRA_STATUS));

                mStatusUpdateHandler.sendMessage(msg);
            }
        }
    };
 
    private void doLayout ()
    {
        setContentView(R.layout.layout_main);
        
        setTitle(R.string.app_name);


        lblStatus = (TextView)findViewById(R.id.lblStatus);
        lblStatus.setOnLongClickListener(this);
        imgStatus = (ImageProgressView)findViewById(R.id.imgStatus);
        imgStatus.setOnLongClickListener(this);
        imgStatus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {

                if (torStatus == TorServiceConstants.STATUS_OFF) {
                    lblStatus.setText(getString(R.string.status_starting_up));
                    startTor();
                } else {
                    lblStatus.setText(getString(R.string.status_shutting_down));
                    stopTor();
                }

            }
        });

		mBtnStart =(Button)findViewById(R.id.btnStart);
		mBtnStart.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v) {

				if (torStatus == TorServiceConstants.STATUS_OFF) {
					lblStatus.setText(getString(R.string.status_starting_up));
					startTor();
				} else {
					lblStatus.setText(getString(R.string.status_shutting_down));
					stopTor();
				}

			}
		});

        mProgress = (DonutProgress) findViewById(R.id.donut_progress);


        String currentExit = Prefs.getExitNodes();
		int selIdx = -1;
		
		ArrayList<String> cList = new ArrayList<String>();
		cList.add(0, getString(R.string.vpn_default_world));
	
		for (int i = 0; i < TorServiceConstants.COUNTRY_CODES.length; i++)
		{
			Locale locale = new Locale("",TorServiceConstants.COUNTRY_CODES[i]);
			cList.add(locale.getDisplayCountry());
			
			if (currentExit.contains(TorServiceConstants.COUNTRY_CODES[i]))
				selIdx = i+1;
		}


        //((TextView)findViewById(R.id.torInfo)).setText("Tor v" + TorServiceConstants.BINARY_TOR_VERSION);

    }

    
   /*
    * Create the UI Options Menu (non-Javadoc)
    * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
    */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.orbot_main, menu);

        return true;
    }
    
    

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
    	 if (item.getItemId() == R.id.menu_settings)
         {
             Intent intent = new Intent(OrbotConnectActivity.this, SettingsPreferences.class);
             startActivityForResult(intent, REQUEST_SETTINGS);
         }
         else if (item.getItemId() == R.id.menu_edit_apps)
         {
            editApps();
         }
         else if (item.getItemId() == R.id.menu_about)
         {
                 showAbout();
                 
                 
         }
         else if (item.getItemId() == R.id.menu_scan)
         {
         	IntentIntegrator integrator = new IntentIntegrator(OrbotConnectActivity.this);
         	integrator.initiateScan();
         }
         else if (item.getItemId() == R.id.menu_share_bridge)
         {
         	
     		String bridges = Prefs.getBridgesList();
         	
     		if (bridges != null && bridges.length() > 0)
     		{
         		try {
						bridges = "bridge://" + URLEncoder.encode(bridges,"UTF-8");
	            		
	                	IntentIntegrator integrator = new IntentIntegrator(OrbotConnectActivity.this);
	                	integrator.shareText(bridges);
	                	
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
     		}

         }
     
		return super.onOptionsItemSelected(item);
	}

	private void showAbout ()
        {
                
            LayoutInflater li = LayoutInflater.from(this);
            View view = li.inflate(R.layout.layout_about, null); 
            
            String version = "";
            
            try {
                version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName + " (Tor " + TorServiceConstants.BINARY_TOR_VERSION + ")";
            } catch (NameNotFoundException e) {
                version = "Version Not Found";
            }
            
            TextView versionName = (TextView)view.findViewById(R.id.versionName);
            versionName.setText(version);    
            
                    new AlertDialog.Builder(this)
            .setTitle(getString(R.string.button_about))
            .setView(view)
            .show();
        }


    /**
     * This is our attempt to REALLY exit Orbot, and stop the background service
     * However, Android doesn't like people "quitting" apps, and/or our code may
     * not be quite right b/c no matter what we do, it seems like the TorService
     * still exists
     **/
    private void doExit() {
        stopTor();

        // Kill all the wizard activities
        setResult(RESULT_CLOSE_ALL);
        finish();
    }

	protected void onPause() {
		try
		{
			super.onPause();
	
			if (aDialog != null)
				aDialog.dismiss();
		}
		catch (IllegalStateException ise)
		{
			//can happen on exit/shutdown
		}
	}

	private void doTorCheck ()
	{
		
		openBrowser(URL_TOR_CHECK,false);
		

	}

    private void enableVPN (boolean enable)
    {
        Prefs.putUseVpn(enable);

        if (enable) {

            startActivity(new Intent(OrbotConnectActivity.this, VPNEnableActivity.class));

        } else
            stopVpnService();
    }


    private void editApps ()
    {
        startActivityForResult(new Intent(OrbotConnectActivity.this, AppManager.class), REQUEST_VPN_APPS_SELECT);
    }

    private synchronized void handleIntents() {
        if (getIntent() == null)
            return;

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        Log.d(TAG, "handleIntents " + action);

        //String type = intent.getType();

        if (action == null)
            return;

        switch (action) {
            case INTENT_ACTION_REQUEST_HIDDEN_SERVICE:
                final int hiddenServicePort = intent.getIntExtra("hs_port", -1);
                final int hiddenServiceRemotePort = intent.getIntExtra("hs_onion_port", -1);
                final String hiddenServiceName = intent.getStringExtra("hs_name");
                final String backupToPackage = intent.getStringExtra("hs_backup_to_package");
                final Boolean authCookie = intent.getBooleanExtra("hs_auth_cookie", false);
                final Uri mKeyUri = intent.getData();


                return; //don't null the setIntent() as we need it later

            case INTENT_ACTION_REQUEST_START_TOR:
                autoStartFromIntent = true;

                startTor();

                //never allow backgrounds start from this type of intent start
                //app devs who want background starts, can use the service intents
                /**
                 if (Prefs.allowBackgroundStarts())
                 {
                 Intent resultIntent;
                 if (lastStatusIntent == null) {
                 resultIntent = new Intent(intent);
                 } else {
                 resultIntent = lastStatusIntent;
                 }
                 resultIntent.putExtra(TorServiceConstants.EXTRA_STATUS, torStatus);
                 setResult(RESULT_OK, resultIntent);
                 finish();
                 }*/

                break;
            case Intent.ACTION_VIEW:
                String urlString = intent.getDataString();

                if (urlString != null) {

                    if (urlString.toLowerCase().startsWith("bridge://"))

                    {
                        String newBridgeValue = urlString.substring(9); //remove the bridge protocol piece
                        newBridgeValue = URLDecoder.decode(newBridgeValue); //decode the value here

                        showAlert(getString(R.string.bridges_updated), getString(R.string.restart_orbot_to_use_this_bridge_) + newBridgeValue, false);

                        setNewBridges(newBridgeValue);
                    }
                }
                break;
        }

        updateStatus(null);

        setIntent(null);

    }

    private void setNewBridges(String newBridgeValue) {

        Prefs.setBridgesList(newBridgeValue); //set the string to a preference
        Prefs.putBridgesEnabled(true);

        setResult(RESULT_OK);

        enableBridges(true);
    }

	/*
	 * Launch the system activity for Uri viewing with the provided url
	 */
	private void openBrowser(final String browserLaunchUrl,boolean forceExternal)
	{
		boolean isBrowserInstalled = appInstalledOrNot(TorServiceConstants.BROWSER_APP_USERNAME);

        if (isBrowserInstalled)
        {
            startIntent(TorServiceConstants.BROWSER_APP_USERNAME,Intent.ACTION_VIEW,Uri.parse(browserLaunchUrl));
        }
		else if (false)
        {
			//use the system browser since VPN is on
			startIntent(null,Intent.ACTION_VIEW, Uri.parse(browserLaunchUrl));
		}
		
	}

    

    private void startIntent (String pkg, String action, Uri data)
    {
        Intent i;
		PackageManager pm = getPackageManager();

        try {
			if (pkg != null) {
				i = pm.getLaunchIntentForPackage(pkg);
				if (i == null)
					throw new PackageManager.NameNotFoundException();
			}
			else
			{
				i = new Intent();
			}

            i.setAction(action);
            i.setData(data);

			if (i.resolveActivity(pm)!=null)
				startActivity(i);

        } catch (PackageManager.NameNotFoundException e) {

        }
    }
    
    private boolean appInstalledOrNot(String uri)
    {
        PackageManager pm = getPackageManager();
        try
        {
               PackageInfo pi = pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);               
               return pi.applicationInfo.enabled;
        }
        catch (PackageManager.NameNotFoundException e)
        {
              return false;
        }
   }    
    
    @Override
    protected void onActivityResult(int request, int response, Intent data) {
        super.onActivityResult(request, response, data);

        if (request == REQUEST_SETTINGS && response == RESULT_OK)
        {
            OrbotConnectApp.forceChangeLanguage(this);
        }
        else if (request == REQUEST_VPN)
        {
			if (response == RESULT_OK) {
                sendIntentToService(TorServiceConstants.CMD_VPN);
            }
			else
			{
				Prefs.putUseVpn(false);
			}
        }
        else if (request == REQUEST_VPN_APPS_SELECT)
        {
            if (response == RESULT_OK) {

                if (torStatus == TorServiceConstants.STATUS_ON) {
                    enableVPN(false);
                    enableVPN(true);
                }
            }
        }

        IntentResult scanResult = IntentIntegrator.parseActivityResult(request, response, data);
        if (scanResult != null) {
             // handle scan result
        	
        	String results = scanResult.getContents();
        	
        	if (results != null && results.length() > 0)
        	{
	        	try {
					
					int urlIdx = results.indexOf("://");
					
					if (urlIdx!=-1)
					{
						results = URLDecoder.decode(results, "UTF-8");
						results = results.substring(urlIdx+3);

						showAlert(getString(R.string.bridges_updated),getString(R.string.restart_orbot_to_use_this_bridge_) + results,false);	
						
						setNewBridges(results);
					}
					else
					{
						JSONArray bridgeJson = new JSONArray(results);
						StringBuffer bridgeLines = new StringBuffer();
						
						for (int i = 0; i < bridgeJson.length(); i++)
						{
							String bridgeLine = bridgeJson.getString(i);
							bridgeLines.append(bridgeLine).append("\n");
						}
						
						setNewBridges(bridgeLines.toString());
					}
					
					
				} catch (Exception e) {
					Log.e(TAG,"unsupported",e);
				}
        	}
        	
          }
        
    }

    private void sendGetBridgeEmail (String type)
    {
    	Intent intent = new Intent(Intent.ACTION_SEND);
    	intent.setType("message/rfc822");
		intent.putExtra(Intent.EXTRA_EMAIL  , new String[]{"bridges@torproject.org"});
		
		if (type != null)
		{
	    	intent.putExtra(Intent.EXTRA_SUBJECT, "get transport " + type);
	    	intent.putExtra(Intent.EXTRA_TEXT, "get transport " + type);
	    	
		}
		else
		{
			intent.putExtra(Intent.EXTRA_SUBJECT, "get bridges");
			intent.putExtra(Intent.EXTRA_TEXT, "get bridges");
			
		}
		
    	startActivity(Intent.createChooser(intent, getString(R.string.send_email)));
    }
    
    private void enableBridges (boolean enable)
    {
		Prefs.putBridgesEnabled(enable);

		if (torStatus == TorServiceConstants.STATUS_ON)
		{
			String bridgeList = Prefs.getBridgesList();
			if (bridgeList != null && bridgeList.length() > 0)
			{
				requestTorRereadConfig ();
			}
		}
    }

    private void requestTorRereadConfig() {
        sendIntentToService(TorServiceConstants.CMD_SIGNAL_HUP);
    }
    
    public void stopVpnService ()
    {    	
        sendIntentToService(TorServiceConstants.CMD_VPN_CLEAR);
    }


    @Override
    protected void onResume() {
        super.onResume();

//        mBtnBridges.setChecked(Prefs.bridgesEnabled());

		requestTorStatus();

		updateStatus(null);
		
    }

    AlertDialog aDialog = null;
    
    //general alert dialog for mostly Tor warning messages
    //sometimes this can go haywire or crazy with too many error
    //messages from Tor, and the user cannot stop or exit Orbot
    //so need to ensure repeated error messages are not spamming this method
    private void showAlert(String title, String msg, boolean button)
    {
            try
            {
                    if (aDialog != null && aDialog.isShowing())
                            aDialog.dismiss();
            }
            catch (Exception e){} //swallow any errors
            
             if (button)
             {
                            aDialog = new AlertDialog.Builder(OrbotConnectActivity.this)
                     .setIcon(R.drawable.onion32)
             .setTitle(title)
             .setMessage(msg)
             .setPositiveButton(R.string.btn_okay, null)
             .show();
             }
             else
             {
                     aDialog = new AlertDialog.Builder(OrbotConnectActivity.this)
                     .setIcon(R.drawable.onion32)
             .setTitle(title)
             .setMessage(msg)
             .show();
             }
    
             aDialog.setCanceledOnTouchOutside(true);
    }

    /**
     * Update the layout_main UI based on the status of {@link TorService}.
     * {@code torServiceMsg} must never be {@code null}
     */
    private void updateStatus(String torServiceMsg) {

    	if (torStatus == null)
    		return; //UI not init'd yet
    	
        if (torStatus == TorServiceConstants.STATUS_ON) {
        	
            imgStatus.setImageResource(R.drawable.toron);

            showProgress (100);

            mBtnStart.setText(R.string.menu_stop);

            if (torServiceMsg != null)
            {
            	if (torServiceMsg.contains(TorServiceConstants.LOG_NOTICE_HEADER)) {
                    lblStatus.setText(torServiceMsg);
                }
            }
        	else
        		lblStatus.setText(getString(R.string.status_activated));


            boolean showFirstTime = mPrefs.getBoolean("connect_first_time", true);

            if (showFirstTime)
            {
                Editor pEdit = mPrefs.edit();
                pEdit.putBoolean("connect_first_time", false);
                pEdit.commit();
                showAlert(getString(R.string.status_activated),
                        getString(R.string.connect_first_time), true);

                String tordAppString = mPrefs.getString(PREFS_KEY_TORIFIED, "");
                if (TextUtils.isEmpty(tordAppString))
                {
                    editApps();
                }

            }

            if (autoStartFromIntent)
            {
                autoStartFromIntent = false;
                Intent resultIntent = lastStatusIntent;

		if (resultIntent == null)
			resultIntent = new Intent(TorServiceConstants.ACTION_START);

		resultIntent.putExtra(
				TorServiceConstants.EXTRA_STATUS,
				torStatus == null?TorServiceConstants.STATUS_OFF:torStatus
		);

		setResult(RESULT_OK, resultIntent);

                finish();
                Log.d(TAG, "autoStartFromIntent finish");
            }


        } else if (torStatus == TorServiceConstants.STATUS_STARTING) {

            imgStatus.setImageResource(R.drawable.torstarting);
            showProgress (10);

            if (torServiceMsg != null)
            {
            	if (torServiceMsg.contains(TorServiceConstants.LOG_NOTICE_BOOTSTRAPPED)) {
                    lblStatus.setText(torServiceMsg);

                    try {
                      //  Pattern p = Pattern.compile("-?\\d+");\\d+(\\.?\\d+)?%
                        Pattern p = Pattern.compile("\\d+(\\.?\\d+)?%");
                        Matcher m = p.matcher(torServiceMsg);
                        while(m.find()) {
                            String perc = m.group().replace('%',' ').trim();
                            showProgress(Integer.parseInt(perc));
                        }
                    }
                    catch (Exception e){}
                }
            }
            else
            	lblStatus.setText(getString(R.string.status_starting_up));

			mBtnStart.setText("...");

        } else if (torStatus == TorServiceConstants.STATUS_STOPPING) {

            stopProgress();

        	  if (torServiceMsg != null && torServiceMsg.contains(TorServiceConstants.LOG_NOTICE_HEADER))
              	lblStatus.setText(torServiceMsg);	
        	  
            imgStatus.setImageResource(R.drawable.torstarting);
            lblStatus.setText(torServiceMsg);

        } else if (torStatus == TorServiceConstants.STATUS_OFF) {
            stopProgress();

            imgStatus.setImageResource(R.drawable.toroff);
			mBtnStart.setText(R.string.menu_start);

        }

    }

    /**
     * Starts tor and related daemons by sending an
     * {@link TorServiceConstants#ACTION_START} {@link Intent} to
     * {@link TorService}
     */
    private void startTor() {

        enableVPN(true);
        sendIntentToService(TorServiceConstants.ACTION_START);


    }
    
    /**
     * Request tor status without starting it
     * {@link TorServiceConstants#ACTION_START} {@link Intent} to
     * {@link TorService}
     */
    private void requestTorStatus() {
        sendIntentToService(TorServiceConstants.ACTION_STATUS);
    }

    private boolean isTorServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (TorService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public boolean onLongClick(View view) {

        if (torStatus == TorServiceConstants.STATUS_OFF) {
            lblStatus.setText(getString(R.string.status_starting_up));
            startTor();
        } else {
        	lblStatus.setText(getString(R.string.status_shutting_down));
        	
            stopTor();
        }
        
        return true;
                
    }

// this is what takes messages or values from the callback threads or other non-mainUI threads
//and passes them back into the main UI thread for display to the user
    private Handler mStatusUpdateHandler = new Handler() {

        @Override
        public void handleMessage(final Message msg) {
        	
        	String newTorStatus = msg.getData().getString("status");
        	String log = (String)msg.obj;

        	if (torStatus == null && newTorStatus != null) //first time status
        	{
        		torStatus = newTorStatus;
        		findViewById(R.id.frameMain).setVisibility(View.VISIBLE);
        		updateStatus(log);
        		
        		//now you can handle the intents properly
        		handleIntents();
        		
        	}
        	else if (newTorStatus != null && !torStatus.equals(newTorStatus)) //status changed
        	{
        		torStatus = newTorStatus;
        		updateStatus(log);
        	}        	
        	else if (log != null) //it is just a log
        		updateStatus(log);
        	
            switch (msg.what) {
                case MESSAGE_TRAFFIC_COUNT:

                    Bundle data = msg.getData();
                    DataCount datacount =  new DataCount(data.getLong("upload"),data.getLong("download"));     
                    
                    long totalRead = data.getLong("readTotal");
                    long totalWrite = data.getLong("writeTotal");
                
                    //downloadText.setText(formatCount(datacount.Download) + " / " + formatTotal(totalRead));
                    //uploadText.setText(formatCount(datacount.Upload) + " / " + formatTotal(totalWrite));

                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
          LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalBroadcastReceiver);

    }

    public class DataCount {
           // data uploaded
           public long Upload;
           // data downloaded
           public long Download;
           
           DataCount(long Upload, long Download){
               this.Upload = Upload;
               this.Download = Download;
           }
       }
       
    private String formatCount(long count) {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.getDefault());
        // Converts the supplied argument into a string.
        // Under 2Mb, returns "xxx.xKb"
        // Over 2Mb, returns "xxx.xxMb"
        if (count < 1e6)
            return numberFormat.format(Math.round(((float) ((int) (count * 10 / 1024)) / 10)))
                    + getString(R.string.kbps);
        else
            return numberFormat.format(Math
                    .round(((float) ((int) (count * 100 / 1024 / 1024)) / 100)))
                    + getString(R.string.mbps);
    }

    private String formatTotal(long count) {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.getDefault());
        // Converts the supplied argument into a string.
        // Under 2Mb, returns "xxx.xKb"
        // Over 2Mb, returns "xxx.xxMb"
        if (count < 1e6)
            return numberFormat.format(Math.round(((float) ((int) (count * 10 / 1024)) / 10)))
                    + getString(R.string.kb);
        else
            return numberFormat.format(Math
                    .round(((float) ((int) (count * 100 / 1024 / 1024)) / 100)))
                    + getString(R.string.mb);
    }


    private void showProgress (int percent)
    {
        if (mProgress.getVisibility() == View.GONE) {
            mProgress.setMax(100);;
            mProgress.setVisibility(View.VISIBLE);
        }

        if (percent > mProgress.getProgress())
            mProgress.setProgress(percent);
    }

    private void stopProgress ()
    {
       mProgress.setVisibility(View.GONE);
        mProgress.setProgress(0);

    }

}
