package com.rtpmt.packtrack;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import rtpmt.motes.packet.Packetizer;
import rtpmt.network.packet.SensorMessage.SensorInformation;
import rtpmt.network.packet.SensorMessage.SensorInformation.LocationInformation;


import com.example.sensorinfo.R;
import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;
import com.ftdi.j2xx.D2xxManager.D2xxException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rtpmt.android.network.tcp2.TCPClient;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context; 
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class StartActivity extends Activity {
	public static Context appContext;
	
	static Context DeviceUARTContext;
	D2xxManager ftdid2xx;
	FT_Device ftDev = null;
	int DevCount = -1;
	int currentIndex = -1;
	int openIndex = 0;
	TextView readText;

	ArrayAdapter<CharSequence> portAdapter; 

	Button configButton;
	Button openButton;
	Button readEnButton;
	Button writeButton;
	static int iEnableReadFlag = 1;

	/* local variables */
	final int baudRate = 230400; /* baud rate */
	byte stopBit = 1; /* 1:1stop bits, 2:2 stop bits */
	byte dataBit = 8; /* 8:8bit, 7: 7bit */
	byte parity = 0; /* 0: none, 1: odd, 2: even, 3: mark, 4: space */
	byte flowControl = 0; /* 0:none, 1: flow control(CTS,RTS) */
	int portNumber = 1; /* port number */
	Spinner portSpinner;
	ArrayList<CharSequence> portNumberList;

	public static final int readLength = 512;
	public int readcount = 0;
	public int iavailable = 0;
	byte[] readData;
	String readDataToText;
	public boolean bReadThreadGoing = false;
	public readThread read_thread;
	public static Packetizer sensorPacketizer;
	boolean uart_configured = false;
	
	TCPClient tCPClient;
	LocationInformation location;
	public static SensorList<Long,Integer> sensorList = new SensorList<Long,Integer>();
	
	//private String host = "saranlap.student.rit.edu";
	//private String host = "apurv.student.rit.edu";
	private String host = "192.168.1.128";
	private int port = 3000;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        appContext = getApplicationContext();

       //ActionBar Initialization
        ActionBar actionbar = getActionBar();
        actionbar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        
        ActionBar.Tab TimeIntervalTab = actionbar.newTab().setText("Time Interval");
        ActionBar.Tab ThresholdTab = actionbar.newTab().setText("Threshold");
        ActionBar.Tab LogTab = actionbar.newTab().setText("Logs");
        
        Fragment PlayerFragment = new TimePeriod(); //Time Period (Response Rate)
        Fragment StationsFragment = new Threshold(); //Threshold

        TimeIntervalTab.setTabListener(new MyTabsListener(PlayerFragment));
        ThresholdTab.setTabListener(new MyTabsListener(StationsFragment));
        ThresholdTab.setTabListener(new MyTabsListener(StationsFragment));

        actionbar.addTab(TimeIntervalTab);
        actionbar.addTab(ThresholdTab);
        
		Log.i("SensorDisplay", "Entered SensorDisplay");
		try {
			ftdid2xx = D2xxManager.getInstance(this);
			DeviceUARTContext = this;
		} catch (D2xxException e) {
		}

		readData = new byte[readLength];
		
		readText = (TextView) findViewById(R.id.readValues);
		readText.setMovementMethod(new ScrollingMovementMethod());

		Log.i("SensorDisplay", "readText set");

		/*
		 * Open Devices
		 */
		Log.i("SensorDisplay", "createDeviceList");
		createDeviceList();

		Log.i("SensorDisplay", "DevCount = " + DevCount);

		if (DevCount > 0) {
			Log.i("SensorDisplay", "connectFunction");
			connectFunction();
		}

		// Config
		if (DevCount <= 0 || ftDev == null) {
			Log.i("SensorDisplay", "Device not open yet at config");
			Toast.makeText(DeviceUARTContext, "Device not open yet...",
					Toast.LENGTH_SHORT).show();
		} else {
			SetConfig(baudRate, dataBit, stopBit, parity, flowControl);
			
			
		}

		// Read
		if (DevCount <= 0 || ftDev == null) {
			Log.i("SensorDisplay", "Device not open yet at read");
			Toast.makeText(DeviceUARTContext, "Device not open yet...",
					Toast.LENGTH_SHORT).show();
		} else if (uart_configured == false) {
			Log.i("SensorDisplay", "UART not configure yet");
			Toast.makeText(DeviceUARTContext, "UART not configure yet...",
					Toast.LENGTH_SHORT).show();
			return;
		} else {
			Log.i("SensorDisplay", "Read started");
			EnableRead();
		}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    
	
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
    }
    
    public void EnableRead() {
		Log.i("SensorDisplay", "inside EnableRead");

		Log.i("SensorDisplay", "EnableReadFlag set: " + iEnableReadFlag);

		if (iEnableReadFlag == 1) {
			Log.i("SensorDisplay", "EnableReadFlag == 1");
			ftDev.purge((byte) (D2xxManager.FT_PURGE_TX));
			ftDev.restartInTask();
		} else {
			Log.i("SensorDisplay", "EnableReadFlag != 1");
			ftDev.stopInTask();
		}
	}


	@SuppressLint("HandlerLeak")
	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
				Log.i("SensorDisplay", "iavailable > 0");
				
				readText.setText(readDataToText+" "+isNetworkAvailable()+"\n\r"+readText.getText());
			
				
		}
	};

	private class readThread extends Thread {
		Handler mHandler;
		Packetizer sensorPacketizer;
		boolean connectionAvailable = true;
		boolean serverAvailable;
		
		readThread(final Handler h, final Packetizer sensorPacketizer) {
			mHandler = h;
			this.sensorPacketizer  = sensorPacketizer;
			this.setPriority(Thread.MIN_PRIORITY);
		}
		
		private boolean connectToServer(){
			try {
				Log.i("SensorDisplay", "Init TCP started");
				tCPClient = new TCPClient();
				tCPClient.connect(host, port);
				serverAvailable = true;
				Log.i("SensorDisplay", "TCPClient Initiated");
			} catch (Exception ex) {
				serverAvailable = false;
			} //TODO TCPClient configurable
			
			return serverAvailable;
		}

		@Override
		public void run() {
			Log.i("SensorDisplay", "readThread started");
			Log.i("SensorDisplay", "Before TCP init");
			
			String packageName = getPackageName();
			String folderName = null;
			try {
				folderName = getPackageManager().getPackageInfo(packageName, 0).applicationInfo.dataDir;
			} catch (NameNotFoundException e1) {
				System.out.println("File Exception: "+e1.toString());
			}
			
			File file = new File(folderName+"/dataBuffer.proto");
			if (!file.exists()) {
			        try {
			            file.createNewFile();
			        } catch (IOException e) {
			            e.printStackTrace();
			        }
			}
			
			
			
			connectToServer();
			Log.i("SensorDisplay", "After TCP init");
			
			try {
				for (;;){
					SensorInformation sensorInfo = sensorPacketizer.readPacket();
					if(sensorInfo != null)
					for (SensorInformation.Sensor sensor : sensorInfo
							.getSensorsList()) {
						readDataToText = sensor.getSensorType().name() + " : "
								+ sensor.getSensorValue() + " "
								+ sensor.getSensorUnit() + "   "
								+ sensorInfo.getTimeStamp();
						location = sensorInfo.getLocation();
						Log.i("SensorValue:",  readDataToText);
						Message msg = mHandler.obtainMessage();
						mHandler.sendMessage(msg);
					}

					System.out.println();
					System.out.flush();

					// Sending data to server
										
					if(isNetworkAvailable())
					{
						if(!connectionAvailable && connectToServer())
						{
							//TODO Add code here to send data from file and then empty the file
							connectionAvailable = true;
							readDataToText = "";
							
							FileInputStream fis = new FileInputStream(folderName+"/dataBuffer.proto");
							DataInputStream dis = new DataInputStream(fis);
							
							for(;;)
							{		
								SensorInformation sensorInfoReadByte;
								
								try
								{
									Log.i("SensorDisplay", "Inside Read From File");
									sensorInfoReadByte = SensorInformation.parseDelimitedFrom(dis);	
									
									Log.i("SensorDisplay", "Delimited Parse");
									
									if(sensorInfoReadByte == null)
									{
										Log.i("SensorDisplay", "sensorInfoReadByte == null");
										break;
									}
									
									readDataToText += "Resending buffer data\n\r";
									Message msg = mHandler.obtainMessage();
									mHandler.sendMessage(msg);
									
									tCPClient.sendData(sensorInfoReadByte);
								}
								catch(InvalidProtocolBufferException e)
								{
									System.out.println("Exception: "+e.toString());
									break;
								}
								
								
							}
							
							dis.close();
							fis.close();
							file.delete();
						}
						
						
						// If network exists send the data to the server
						Log.i("SensorDisplay", "Before sending server data");
						tCPClient.sendData(sensorInfo);
						Log.i("SensorDisplay", "Sending Data to server");
					}
					else
					{
						// If network does not exist, store to a file
						connectionAvailable = false;
						
						Log.i("SensorDisplay", "Before saving server data to file");
						try {
						    // Write the new address book back to disk.
	
						   FileOutputStream output = new FileOutputStream(folderName+"/dataBuffer.proto", true);
						   DataOutputStream dos = new DataOutputStream(output);            
						   	   sensorInfo.writeDelimitedTo(dos);
						  // dos.close();
						   output.close();   
						   Log.i("SensorDisplay", "After saving server data to file");
						} catch (Exception e) {   
							Log.i("SensorDisplay", "Saving server data to file Exception: "+e);
						}
					}
					
				}
			} catch (IOException ex) {
				System.out.println(ex.toString());
			}
		}
	}
	
	private boolean isNetworkAvailable() 
	{

	    ConnectivityManager connectivityManager 
	          = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    boolean connectionAvailable = false;
	    if(activeNetworkInfo != null && activeNetworkInfo.isAvailable())
	    	connectionAvailable = true;
	    
	    return connectionAvailable;
	}
	
	/*
	 * Initialize TCP Client
	 */
	public void initalizeTCPClient(String ipAddress, int portNumber) {
		try {
			Log.i("SensorDisplay", "Init TCP started");
			tCPClient = new TCPClient();
			tCPClient.connect(ipAddress, portNumber);
			Log.i("SensorDisplay", "TCPClient Initiated");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void createDeviceList() {
		Log.i("SensorDisplay", "Inside createDeviceList");
		int tempDevCount = ftdid2xx.createDeviceInfoList(DeviceUARTContext);

		Log.i("SensorDisplay", "tempDevCount set");

		if (tempDevCount > 0) {
			Log.i("SensorDisplay", "tempDevCount > 0");
			if (DevCount != tempDevCount) {
				Log.i("SensorDisplay", "DevCount != tempDevCount");
				DevCount = tempDevCount;
			}
		} else {
			Log.i("SensorDisplay", "tempDevCount <= 0");
			DevCount = -1;
			currentIndex = -1;
		}
	}

	public void connectFunction() {
		Log.i("SensorDisplay", "inside connectFunction");
		int tmpProtNumber = openIndex + 1;

		Log.i("SensorDisplay", "tmpProtNumber set");

		if (currentIndex != openIndex) {
			Log.i("SensorDisplay", "currentIndex != openIndex");
			if (null == ftDev) {
				Log.i("SensorDisplay", "null == ftDev");
				ftDev = ftdid2xx.openByIndex(DeviceUARTContext, openIndex); // TODO
				Log.i("SensorDisplay", "ftDev opened = " + ftDev + " is open: "
						+ ftDev.isOpen());
			} else {
				Log.i("SensorDisplay", "null != ftDev");
				synchronized (ftDev) {
					Log.i("SensorDisplay", "synchronized(ftDev)");
					ftDev = ftdid2xx.openByIndex(DeviceUARTContext, openIndex);
				}
			}
			Log.i("SensorDisplay", "ftDev set");
			uart_configured = false;
		} else {
			Log.i("SensorDisplay", "currentIndex == openIndex");
			Toast.makeText(DeviceUARTContext,
					"Device port " + tmpProtNumber + " is already opened",
					Toast.LENGTH_LONG).show();
			return;
		}

		if (ftDev == null) {
			Log.i("SensorDisplay", "ftDev == null");
			Toast.makeText(
					DeviceUARTContext,
					"open device port(" + tmpProtNumber + ") NG, ftDev == null",
					Toast.LENGTH_LONG).show();
			return;
		}

		if (true == ftDev.isOpen()) {
			Log.i("SensorDisplay", "true == ftDev.isOpen()");
			currentIndex = openIndex;
			Toast.makeText(DeviceUARTContext,
					"open device port(" + tmpProtNumber + ") OK",
					Toast.LENGTH_SHORT).show();

			Log.i("SensorDisplay", "Toast.makeText");
			if (false == bReadThreadGoing) {
				Log.i("SensorDisplay", "false == bReadThreadGoing");
				Log.i("SensorDisplay", "readThread started");

				sensorPacketizer = new Packetizer("SensorData", this, ftDev);
				try {
					sensorPacketizer.open(null);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				 read_thread = new readThread(handler,sensorPacketizer);
				 read_thread.start();
				bReadThreadGoing = true;
			}

		} else {
			Log.i("SensorDisplay", "true != ftDev.isOpen()");
			Toast.makeText(DeviceUARTContext,
					"open device port(" + tmpProtNumber + ") NG",
					Toast.LENGTH_LONG).show();
		}
		
	}

	public void SetConfig(int baud, byte dataBits, byte stopBits, byte parity,
			byte flowControl) {
		if (ftDev.isOpen() == false) {
			Log.e("j2xx", "SetConfig: device not open");
			return;
		}

		// configure our port
		// reset to UART mode for 232 devices
		ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);

		ftDev.setBaudRate(baud);

		switch (dataBits) {
		case 7:
			dataBits = D2xxManager.FT_DATA_BITS_7;
			break;
		case 8:
			dataBits = D2xxManager.FT_DATA_BITS_8;
			break;
		default:
			dataBits = D2xxManager.FT_DATA_BITS_8;
			break;
		}

		switch (stopBits) {
		case 1:
			stopBits = D2xxManager.FT_STOP_BITS_1;
			break;
		case 2:
			stopBits = D2xxManager.FT_STOP_BITS_2;
			break;
		default:
			stopBits = D2xxManager.FT_STOP_BITS_1;
			break;
		}

		switch (parity) {
		case 0:
			parity = D2xxManager.FT_PARITY_NONE;
			break;
		case 1:
			parity = D2xxManager.FT_PARITY_ODD;
			break;
		case 2:
			parity = D2xxManager.FT_PARITY_EVEN;
			break;
		case 3:
			parity = D2xxManager.FT_PARITY_MARK;
			break;
		case 4:
			parity = D2xxManager.FT_PARITY_SPACE;
			break;
		default:
			parity = D2xxManager.FT_PARITY_NONE;
			break;
		}

		ftDev.setDataCharacteristics(dataBits, stopBits, parity);

		short flowCtrlSetting;
		switch (flowControl) {
		case 0:
			flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
			break;
		case 1:
			flowCtrlSetting = D2xxManager.FT_FLOW_RTS_CTS;
			break;
		case 2:
			flowCtrlSetting = D2xxManager.FT_FLOW_DTR_DSR;
			break;
		case 3:
			flowCtrlSetting = D2xxManager.FT_FLOW_XON_XOFF;
			break;
		default:
			flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
			break;
		}

		// TODO : flow ctrl: XOFF/XOM
		// TODO : flow ctrl: XOFF/XOM
		ftDev.setFlowControl(flowCtrlSetting, (byte) 0x0b, (byte) 0x0d);

		uart_configured = true;
		Toast.makeText(DeviceUARTContext, "Config done", Toast.LENGTH_SHORT)
				.show();
	}
    
}



class MyTabsListener implements ActionBar.TabListener {
	public Fragment fragment;
	
	public MyTabsListener(Fragment fragment) {
		this.fragment = fragment;
	}
	
	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
		Toast.makeText(StartActivity.appContext, "Reselected!", Toast.LENGTH_LONG).show();
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		ft.replace(R.id.fragment_container, fragment);
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
		ft.remove(fragment);
	}
	
}