package cs.umass.edu.metawearlogging;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.hanks.library.AnimateCheckBox;
import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.Logging;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = "TESTING";

    private static final String deviceUUID = "F6:8D:FC:1A:E4:50"; //"E8:B9:62:B6:E3:4B";

    private BtleService.LocalBinder serviceBinder;

    private Accelerometer accelerometer;

    private Route logRoute;

    private Logging logging;

    private TextView sensorOutput;

    private String dataString;

    private static final int BLUETOOTH_REQUEST_CODE = 999;

    private MetaWearBoard mwBoard;

    private TextView txtConnect;
    private AnimateCheckBox chkConnect;

    private TextView txtStartAccelerometer;
    private AnimateCheckBox chkAccelerometer;

    private TextView txtDownloadLogs;
    private AnimateCheckBox chkDownloadLogs;

    private TextView txtDisconnect;
    private AnimateCheckBox chkDisconnect;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case BLUETOOTH_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    setup();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Please grant permission...", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void setup(){
        getApplicationContext().bindService(new Intent(this, BtleService.class), this, BIND_AUTO_CREATE);

        Switch switchMetawear = (Switch) findViewById(R.id.switchMetawear);
        switchMetawear.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean enable) {
                if (enable){
                    dataString = "";
                    sensorOutput.setText(dataString);
                    connectToMetawear(deviceUUID);
                } else {
                    stopLogging();
                }
            }
        });

        sensorOutput = (TextView) findViewById(R.id.txtSensorOutput);
        txtConnect = (TextView) findViewById(R.id.txtConnect);
        chkConnect = (AnimateCheckBox) findViewById(R.id.chkConnect);
        txtStartAccelerometer = (TextView) findViewById(R.id.txtStartAccelerometer);
        chkAccelerometer = (AnimateCheckBox) findViewById(R.id.chkAccelerometer);
        txtDownloadLogs = (TextView) findViewById(R.id.txtDownloadLogs);
        chkDownloadLogs = (AnimateCheckBox) findViewById(R.id.chkDownloadLogs);
        txtDisconnect = (TextView) findViewById(R.id.txtDisconnect);
        chkDisconnect = (AnimateCheckBox) findViewById(R.id.chkDisconnect);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, BLUETOOTH_REQUEST_CODE);
    }

    private void stopLogging(){
        if (logging != null){
            Log.i(TAG, "Starting data download...");
            logging.stop();
            txtDownloadLogs.setText("Downloading logs...");
            logging.downloadAsync().continueWith(new Continuation<Void, Object>() {
                @Override
                public Object then(Task<Void> task) throws Exception {
                    if (task.isFaulted()) {
                        Log.i(TAG, "TASK FAULTED!");
                        task.getError().printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            txtDownloadLogs.setText("Logs downloaded.");
                            chkDownloadLogs.performClick();
                        }
                    });
                    Log.i(TAG, "Downloaded data.");

                    stopAccelerometer();

                    logging.stop();
                    disconnect();

                    return null;
                }
            });
        } else {
            if (accelerometer != null){
                stopAccelerometer();
            }
            if (mwBoard != null){
                disconnect();
            }
        }
    }

    private void stopAccelerometer(){
        Log.i(TAG, "Stopping accelerometer...");
        accelerometer.stop();
        accelerometer.acceleration().stop();
    }

    private void disconnect(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtDisconnect.setText("Disconnecting...");
            }
        });

        mwBoard.disconnectAsync().onSuccess(
                new Continuation<Void, Object>() {
                    @Override
                    public Object then(Task<Void> task) throws Exception {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                txtDisconnect.setText("Disconnected.");
                                chkDisconnect.performClick();
                            }
                        });
                        return null;
                    }
                });
    }

    private void configureBoard(MetaWearBoard mwBoard){
        accelerometer = mwBoard.getModule(Accelerometer.class);
        logging = mwBoard.getModule(Logging.class);
        logging.clearEntries();
        logging.start(false);

        Log.i(TAG, "Adding accelerometer route...");
        txtStartAccelerometer.setText("Adding route...");
        accelerometer.acceleration().addRouteAsync(new RouteBuilder() {
            @Override
            public void configure(RouteComponent source) {
                source.log(new Subscriber() {
                    @Override
                    public void apply(Data data, Object... env) {
                        Log.i(TAG, "received : " + data.toString());

                        final Acceleration value = data.value(Acceleration.class);

                        dataString += "\n" + value.x() + ", " + value.y() + ", " + value.z();

                        runOnUiThread(() -> sensorOutput.setText(dataString));
                    }
                });
            }
        }).continueWith(new Continuation<Route, Object>() {
            @Override
            public Object then(Task<Route> task) throws Exception {
                Log.i(TAG, "Added route. Starting Accelerometer");
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txtStartAccelerometer.setText("Route added.");
                        chkAccelerometer.performClick();
                    }
                });
                logRoute = task.getResult();
                accelerometer.acceleration().start();
                accelerometer.start();

                return null;
            }
        });
    }

    public static Task<Void> reconnect(final MetaWearBoard board) {
        return board.connectAsync()
                .continueWithTask(task -> {
                    if (task.isFaulted()) {
                        return reconnect(board);
                    } else if (task.isCancelled()) {
                        return task;
                    }
                    return Task.forResult(null);
                });
    }

    public void connectToMetawear(String deviceUUID) {
        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothDevice btDevice = btManager.getAdapter().getRemoteDevice(deviceUUID);
        mwBoard = serviceBinder.getMetaWearBoard(btDevice);

        Log.i(TAG, "Connecting to Metawear...");
        txtConnect.setText("Connecting...");
        mwBoard.connectAsync()
                .continueWithTask(task -> {
                    if (task.isCancelled()) {
                        return task;
                    }
                    return task.isFaulted() ? reconnect(mwBoard) : Task.forResult(null);
                })
                .continueWith(task -> {
                    if (!task.isCancelled()) {
                        Log.i(TAG, "Connected!");
                        runOnUiThread(() -> {
                            txtConnect.setText("Connected");
                            chkConnect.performClick();
                        });
                        configureBoard(mwBoard);
                    }
                    return null;
                });
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        serviceBinder = (BtleService.LocalBinder) iBinder;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }
}
