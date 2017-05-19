/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.torproject.android.service.vpn;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.VpnService;
import android.net.VpnService.Builder;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;


import org.torproject.android.service.R;
import org.torproject.android.service.TorServiceConstants;
import org.torproject.android.service.util.TorServiceUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class OrbotVpnManager implements Handler.Callback {
    private static final String TAG = "OrbotVpnService";

    private PendingIntent mConfigureIntent;

    private Thread mThreadVPN;

    private String mSessionName = "OrbotVPN";
    private ParcelFileDescriptor mInterface;

    private int mTorPortSocks = -1;
	private int mTorPortDNS = -1;

	public static int sSocksProxyServerPort = -1;
    public static String sSocksProxyLocalhost = null;

    private final static int VPN_MTU = 1500;
    
    private final static boolean mIsLollipop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    
    //this is the actual DNS server we talk to over UDP or TCP (now using Tor's DNS port)
    private final static String DEFAULT_ACTUAL_DNS_HOST = "127.0.0.1";


	File filePdnsd = null;

	private boolean isRestart = false;
    
    private VpnService mService;
    

    public OrbotVpnManager (VpnService service)
    {
    	mService = service;

		File fileBinHome = mService.getDir(TorServiceConstants.DIRECTORY_TOR_BINARY, Application.MODE_PRIVATE);
		filePdnsd = new File(fileBinHome,TorServiceConstants.PDNSD_ASSET_KEY);

		Tun2Socks.init();

	}
   
    //public int onStartCommand(Intent intent, int flags, int startId) {
    public int handleIntent(Builder builder, Intent intent) {

    	if (intent != null)
    	{
	    	String action = intent.getAction();
	    	
	    	if (action.equals("start"))
	    	{
		
		        // Stop the previous session by interrupting the thread.
		        if (mThreadVPN == null || (!mThreadVPN.isAlive()))
		        {
		        	Log.d(TAG,"starting OrbotVPNService service!");
		        	
		        	mTorPortSocks = intent.getIntExtra("torSocks", -1);
					mTorPortDNS = intent.getIntExtra("torDNS", -1);

					if (mTorPortSocks == -1 || mTorPortDNS == -1) {
                        Log.e(TAG,"you provide socks and DNS port to VPN");
                        return -1;
                    }

		            setupTun2Socks(builder);               
		        }
	    	}
	    	else if (action.equals("stop"))
	    	{
	    		Log.d(TAG,"stop OrbotVPNService service!");
	    		
	    		stopVPN();    		
	    		//if (mHandler != null)
	    			//mHandler.postDelayed(new Runnable () { public void run () { stopSelf(); }}, 1000);
	    	}
	    	else if (action.equals("refresh"))
	    	{
	    		Log.d(TAG,"refresh OrbotVPNService service!");
	    		
	    		if (!isRestart)
	    			setupTun2Socks(builder);
	    	}
    	}
     
        
        return Service.START_STICKY;
    }
  

    
    private void stopVPN ()
    {
        if (mInterface != null){
            try
            {
            	Log.d(TAG,"closing interface, destroying VPN interface");
                
        		mInterface.close();
        		mInterface = null;
            	
            }
            catch (Exception e)
            {
                Log.d(TAG,"error stopping tun2socks",e);
            }
            catch (Error e)
            {
                Log.d(TAG,"error stopping tun2socks",e);
            }   
        }
        
        Tun2Socks.Stop();
        
        try {
        	TorServiceUtils.killProcess(filePdnsd);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        mThreadVPN = null;
        

    }

    @Override
    public boolean handleMessage(Message message) {
        if (message != null) {
            Toast.makeText(mService, message.what, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

  
    private synchronized void setupTun2Socks(final Builder builder)  {

    	
        if (mInterface != null) //stop tun2socks now to give it time to clean up
        {
        	isRestart = true;
        	Tun2Socks.Stop();
        }
        
    	mThreadVPN = new Thread ()
    	{
    		
    		public void run ()
    		{
	    		try
		        {
	    			
	    			if (isRestart)
	    			{
	    				Log.d(TAG,"is a restart... let's wait for a few seconds");
			        	Thread.sleep(3000);
	    			}
	    			
	    			//start PDNSD daemon pointing to actual DNS
	    			startDNS(DEFAULT_ACTUAL_DNS_HOST,mTorPortDNS);
	    			
		    		final String vpnName = "OrbotVPN";
		    		final String localhost = "127.0.0.1";

		    		final String virtualGateway = "10.10.10.1";
		    		final String virtualIP = "10.10.10.2";
		    		final String virtualNetMask = "255.255.255.0";
		    		final String dummyDNS = "8.8.8.8"; //this is intercepted by the tun2socks library, but we must put in a valid DNS to start
		    		final String defaultRoute = "0.0.0.0";
		    		
		    		final String localSocks = localhost + ':'
		    		        + String.valueOf(mTorPortSocks);
		    		
		    		final String localDNS = localhost + ':' + "8091";//String.valueOf(TorServiceConstants.TOR_DNS_PORT_DEFAULT);
		    		final boolean localDnsTransparentProxy = true;
		        	
			        builder.setMtu(VPN_MTU);
			        builder.addAddress(virtualGateway,32);
			        
			        builder.setSession(vpnName);		        

			        builder.addDnsServer(dummyDNS);
			        builder.addRoute(dummyDNS,32);
				        
			        //route all traffic through VPN (we might offer country specific exclude lists in the future)
			        builder.addRoute(defaultRoute,0);	
			        
			        //handle ipv6
			        //builder.addAddress("fdfe:dcba:9876::1", 126);
					//builder.addRoute("::", 0);
			        
			        if (mIsLollipop)			        
			        	doLollipopAppRouting(builder);			        

			         // Create a new interface using the builder and save the parameters.
			        ParcelFileDescriptor newInterface = builder.setSession(mSessionName)
			                .setConfigureIntent(mConfigureIntent)
			                .establish();
		
			        if (mInterface != null)
			        {
			        	Log.d(TAG,"Stopping existing VPN interface");
			        	mInterface.close();
			        	mInterface = null;
			        }

		        	mInterface = newInterface;
			        
		        	Tun2Socks.Start(mInterface, VPN_MTU, virtualIP, virtualNetMask, localSocks , localDNS , localDnsTransparentProxy);
		        	
		        	isRestart = false;
		        	
		        }
		        catch (Exception e)
		        {
		        	Log.d(TAG,"tun2Socks has stopped",e);
		        }
	    	}
    		
    	};
    	
    	mThreadVPN.start();
    	
    }
    
    
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void doLollipopAppRouting (Builder builder) throws NameNotFoundException
    {    
    	   
        ArrayList<TorifiedApp> apps = TorifiedApp.getApps(mService, TorServiceUtils.getSharedPrefs(mService.getApplicationContext()));
    
        boolean perAppEnabled = false;
        
        for (TorifiedApp app : apps)
        {
        	if (app.isTorified() && (!app.getPackageName().equals(mService.getPackageName())))
        	{
        		builder.addAllowedApplication(app.getPackageName());
        		perAppEnabled = true;
        	}
        	
        }
    
        if (!perAppEnabled)
        	builder.addDisallowedApplication(mService.getPackageName());
    
    }
    
    
    public void onRevoke() {
    
    	Log.w(TAG,"VPNService REVOKED!");
    	
    	if (!isRestart)
    	{
	    	SharedPreferences prefs = TorServiceUtils.getSharedPrefs(mService.getApplicationContext()); 
	        prefs.edit().putBoolean("pref_vpn", false).commit();      
	    	stopVPN();	
    	}
    	
    	isRestart = false;
    	
    	//super.onRevoke();
    
    }

    private void startDNS (String dns, int port) throws IOException, TimeoutException
    {
    	makePdnsdConf(mService, dns, port,filePdnsd.getParentFile() );
    	
        ArrayList<String> customEnv = new ArrayList<String>();
    	String baseDirectory = filePdnsd.getParent();

        String[] cmdString = {filePdnsd.getCanonicalPath(),"-c",baseDirectory + "/pdnsd.conf"};
        ProcessBuilder pb = new ProcessBuilder(cmdString);
        pb.redirectErrorStream(true);
		Process proc = pb.start();
		try { proc.waitFor();} catch (Exception e){}

        Log.i(TAG,"PDNSD: " + proc.exitValue());

        if (proc.exitValue() != 0)
        {
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            String line = null;
            while ((line = br.readLine ()) != null) {
                Log.d(TAG,"pdnsd: " + line);
            }

        }

        
    }
    
    public static void makePdnsdConf(Context context, String dns, int port, File fileDir) throws FileNotFoundException {
        String conf = String.format(context.getString(R.string.pdnsd_conf), dns, port);

        File f = new File(fileDir,"pdnsd.conf");

        if (f.exists()) {
                f.delete();
        }

        FileOutputStream fos = new FileOutputStream(f, false);
    	PrintStream ps = new PrintStream(fos);
    	ps.print(conf);
    	ps.close();

        File cache = new File(fileDir,"pdnsd.cache");

        if (!cache.exists()) {
                try {
                        cache.createNewFile();
                } catch (Exception e) {

                }
        }
}

    
}
