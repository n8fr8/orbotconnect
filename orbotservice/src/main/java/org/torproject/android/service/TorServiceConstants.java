/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */
/* See LICENSE for licensing information */

package org.torproject.android.service;

import android.content.Intent;

public interface TorServiceConstants {

	public final static String TOR_APP_USERNAME = "org.torproject.android";
	public final static String BROWSER_APP_USERNAME = "info.guardianproject.orfox";
	
	public final static String DIRECTORY_TOR_BINARY = "bin";
	public final static String DIRECTORY_TOR_DATA = "data";
	
	//name of the tor C binary
	public final static String TOR_ASSET_KEY = "tor";	
	
	//torrc (tor config file)
	public final static String TORRC_ASSET_KEY = "torrc";
	public final static String TORRCDIAG_ASSET_KEY = "torrcdiag";
	public final static String TORRC_TETHER_KEY = "torrctether";
	
	public final static String TOR_CONTROL_COOKIE = "control_auth_cookie";
	
	//privoxy
	public final static String POLIPO_ASSET_KEY = "polipo";
	
	//privoxy.config
	public final static String POLIPOCONFIG_ASSET_KEY = "torpolipo.conf";
	
	//geoip data file asset key
	public final static String GEOIP_ASSET_KEY = "geoip";
	public final static String GEOIP6_ASSET_KEY = "geoip6";

	//various console cmds
	public final static String SHELL_CMD_CHMOD = "chmod";
	public final static String SHELL_CMD_KILL = "kill -9";
	public final static String SHELL_CMD_RM = "rm";
	public final static String SHELL_CMD_PS = "toolbox ps";
	public final static String SHELL_CMD_PS_ALT = "ps";
    
    
	//public final static String SHELL_CMD_PIDOF = "pidof";
	public final static String SHELL_CMD_LINK = "ln -s";
	public final static String SHELL_CMD_CP = "cp";
	

	public final static String CHMOD_EXE_VALUE = "770";

	public final static int FILE_WRITE_BUFFER_SIZE = 1024;

	public final static String IP_LOCALHOST = "127.0.0.1";
	public final static int UPDATE_TIMEOUT = 1000;

	public final static int STANDARD_DNS_PORT = 53;
	public final static int TOR_DNS_PORT_DEFAULT = 9400;
	public final static String TOR_VPN_DNS_LISTEN_ADDRESS = "127.0.0.1";
	
	public final static int CONTROL_PORT_DEFAULT = 9091;
    public final static int SOCKS_PROXY_PORT_DEFAULT = 9090;

    
	//path to check Tor against
	public final static String URL_TOR_CHECK = "https://check.torproject.org";

    //control port 
    public final static String TOR_CONTROL_PORT_MSG_BOOTSTRAP_DONE = "Bootstrapped 100%";
    public final static String LOG_NOTICE_HEADER = "NOTICE";
    public final static String LOG_NOTICE_BOOTSTRAPPED = "Bootstrapped";
    
    /**
     * A request to Orbot to transparently start Tor services
     */
    public final static String ACTION_START = "org.torproject.android.connect.intent.action.START";
    /**
     * {@link Intent} send by Orbot with {@code ON/OFF/STARTING/STOPPING} status
     */
    public final static String ACTION_STATUS = "org.torproject.android.connect.intent.action.STATUS";
    /**
     * {@code String} that contains a status constant: {@link #STATUS_ON},
     * {@link #STATUS_OFF}, {@link #STATUS_STARTING}, or
     * {@link #STATUS_STOPPING}
     */
    public final static String EXTRA_STATUS = "org.torproject.android.connect.intent.extra.STATUS";
    /**
     * A {@link String} {@code packageName} for Orbot to direct its status reply
     * to, used in {@link #ACTION_START} {@link Intent}s sent to Orbot
     */
    public final static String EXTRA_PACKAGE_NAME = "org.torproject.android.connect.intent.extra.PACKAGE_NAME";
    /**
     * The SOCKS proxy settings in URL form.
     */
    public final static String EXTRA_SOCKS_PROXY = "org.torproject.android.connect.intent.extra.SOCKS_PROXY";
    public final static String EXTRA_SOCKS_PROXY_HOST = "org.torproject.android.connect.intent.extra.SOCKS_PROXY_HOST";
    public final static String EXTRA_SOCKS_PROXY_PORT = "org.torproject.android.connect.intent.extra.SOCKS_PROXY_PORT";
    /**
     * The HTTP proxy settings in URL form.
     */
    public final static String EXTRA_HTTP_PROXY = "org.torproject.android.connect.intent.extra.HTTP_PROXY";
    public final static String EXTRA_HTTP_PROXY_HOST = "org.torproject.android.connect.intent.extra.HTTP_PROXY_HOST";
    public final static String EXTRA_HTTP_PROXY_PORT = "org.torproject.android.connect.intent.extra.HTTP_PROXY_PORT";

    public final static String LOCAL_ACTION_LOG = "log";
    public final static String LOCAL_ACTION_BANDWIDTH = "bandwidth";
    public final static String LOCAL_EXTRA_LOG = "log";

    /**
     * All tor-related services and daemons are stopped
     */
    public final static String STATUS_OFF = "OFF";
    /**
     * All tor-related services and daemons have completed starting
     */
    public final static String STATUS_ON = "ON";
    public final static String STATUS_STARTING = "STARTING";
    public final static String STATUS_STOPPING = "STOPPING";

    /**
     * The user has disabled the ability for background starts triggered by
     * apps. Fallback to the old {@link Intent} action that brings up Orbot:
     * {@link org.torproject.android.OrbotMainActivity#INTENT_ACTION_REQUEST_START_TOR}
     */
    public final static String STATUS_STARTS_DISABLED = "STARTS_DISABLED";

    // actions for internal command Intents
    public static final String CMD_SIGNAL_HUP = "signal_hup";
    public static final String CMD_STATUS = "status";
    public static final String CMD_FLUSH = "flush";
    public static final String CMD_NEWNYM = "newnym";
    public static final String CMD_VPN = "vpn";
    public static final String CMD_VPN_CLEAR = "vpnclear";
    public static final String CMD_UPDATE_TRANS_PROXY = "update";
    public static final String CMD_SET_EXIT = "setexit";

    public static final String BINARY_TOR_VERSION = "0.2.9.9-openssl1.0.2k-1";
    public static final String PREF_BINARY_TOR_VERSION_INSTALLED = "BINARY_TOR_VERSION_INSTALLED";
    
    //obfsproxy 
    public static final String OBFSCLIENT_ASSET_KEY = "obfs4proxy";
    
   // public static final String MEEK_ASSET_KEY = "meek-client";
    
	//name of the iptables binary
	public final static String IPTABLES_ASSET_KEY = "xtables";	

	//DNS daemon for TCP DNS over TOr
	public final static String PDNSD_ASSET_KEY = "pdnsd";

	//EXIT COUNTRY CODES
	public final static String[] COUNTRY_CODES = {"DE","AT","SE","CH","IS","CA","US","ES","FR","BG","PL","AU","BR","CZ","DK","FI","GB","HU","NL","JP","RO","RU","SG","SK"};


	public static final String HIDDEN_SERVICES_DIR = "hidden_services";

}
