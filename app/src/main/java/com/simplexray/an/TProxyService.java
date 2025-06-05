package com.simplexray.an;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.net.ProxyInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TProxyService extends VpnService {
    public static final String ACTION_CONNECT = "com.simplexray.an.CONNECT";
    public static final String ACTION_DISCONNECT = "com.simplexray.an.DISCONNECT";
    public static final String ACTION_START = "com.simplexray.an.START";
    public static final String ACTION_STOP = "com.simplexray.an.STOP";
    public static final String ACTION_LOG_UPDATE = "com.simplexray.an.LOG_UPDATE";
    public static final String EXTRA_LOG_DATA = "log_data";
    private static final String TAG = "VpnService";
    private static final long BROADCAST_DELAY_MS = 3000;

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> logBroadcastBuffer = new ArrayList<>();
    private final Runnable broadcastLogsRunnable = () -> {
        synchronized (logBroadcastBuffer) {
            if (!logBroadcastBuffer.isEmpty()) {
                Intent logUpdateIntent = new Intent(ACTION_LOG_UPDATE);
                logUpdateIntent.setPackage(getApplication().getPackageName());
                logUpdateIntent.putStringArrayListExtra(EXTRA_LOG_DATA, new ArrayList<>(logBroadcastBuffer));
                sendBroadcast(logUpdateIntent);
                logBroadcastBuffer.clear();
                Log.d(TAG, "Broadcasted a batch of logs.");
            }
        }
    };
    private LogFileManager logFileManager;
    private Process xrayProcess;
    private ParcelFileDescriptor tunFd = null;

    private static native void TProxyStartService(String config_path, int fd);

    private static native void TProxyStopService();

    private static native long[] TProxyGetStats();

    public static String getNativeLibraryDir(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null");
            return null;
        }
        try {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            if (applicationInfo != null) {
                String nativeLibraryDir = applicationInfo.nativeLibraryDir;
                Log.d(TAG, "Native Library Directory: " + nativeLibraryDir);
                return nativeLibraryDir;
            } else {
                Log.e(TAG, "ApplicationInfo is null");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting native library dir", e);
            return null;
        }
    }

    private static @NonNull String getTproxyConf(Preferences prefs) {
        String tproxy_conf = "misc:\n" + "  task-stack-size: " + prefs.getTaskStackSize() + "\n" + "tunnel:\n" + "  mtu: " + prefs.getTunnelMtu() + "\n";
        tproxy_conf += "socks5:\n" + "  port: " + prefs.getSocksPort() + "\n" + "  address: '" + prefs.getSocksAddress() + "'\n" + "  udp: '" + (prefs.getUdpInTcp() ? "tcp" : "udp") + "'\n";
        if (!prefs.getSocksUsername().isEmpty() && !prefs.getSocksPassword().isEmpty()) {
            tproxy_conf += "  username: '" + prefs.getSocksUsername() + "'\n";
            tproxy_conf += "  password: '" + prefs.getSocksPassword() + "'\n";
        }
        return tproxy_conf;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        logFileManager = new LogFileManager(this);
        Log.d(TAG, "TProxyService created.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_DISCONNECT.equals(action)) {
                stopXray();
                return START_NOT_STICKY;
            }
        }
        logFileManager.clearLogs();
        startXray();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(broadcastLogsRunnable);
        broadcastLogsRunnable.run();
        Log.d(TAG, "TProxyService destroyed.");
        System.exit(0);
    }

    @Override
    public void onRevoke() {
        stopXray();
        super.onRevoke();
    }

    private void startXray() {
        startService();
        executorService.execute(() -> {
            Process xrayProcess;
            try {
                String libraryDir = getNativeLibraryDir(getApplicationContext());
                String xrayPath = libraryDir + "/libxray.so";
                Preferences prefs = new Preferences(getApplicationContext());
                String selectedConfigPath = prefs.getSelectedConfigPath();

                ProcessBuilder processBuilder = getProcessBuilder(xrayPath);
                xrayProcess = processBuilder.start();

                if (selectedConfigPath != null && new File(selectedConfigPath).exists()) {
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(selectedConfigPath);
                         java.io.OutputStream os = xrayProcess.getOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            os.write(buffer, 0, length);
                        }
                        os.flush();
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing config to xray stdin", e);
                    }
                } else {
                    Log.w(TAG, "No selected config file found or file does not exist: " + selectedConfigPath);
                }

                this.xrayProcess = xrayProcess;
                InputStream inputStream = xrayProcess.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (logFileManager != null) {
                        logFileManager.appendLog(line);
                        synchronized (logBroadcastBuffer) {
                            logBroadcastBuffer.add(line);
                            if (!handler.hasCallbacks(broadcastLogsRunnable)) {
                                handler.postDelayed(broadcastLogsRunnable, BROADCAST_DELAY_MS);
                                Log.d(TAG, "Scheduled log broadcast.");
                            }
                        }
                    }
                }
                Log.d(TAG, "xray executed with exit");
            } catch (InterruptedIOException e) {
                Log.d(TAG, "Xray process interrupted");
            } catch (Exception e) {
                Log.e(TAG, "Error executing xray", e);
            } finally {
                stopXray();
            }
        });
    }

    private ProcessBuilder getProcessBuilder(String xrayPath) {
        File filesDir = getApplicationContext().getFilesDir();
        List<String> command = new ArrayList<>();
        command.add(xrayPath);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Map<String, String> environment = processBuilder.environment();
        environment.put("XRAY_LOCATION_ASSET", filesDir.getPath());
        processBuilder.directory(filesDir);
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    private void stopXray() {
        executorService.shutdown();
        if (xrayProcess != null) xrayProcess.destroy();
        Log.d(TAG, "Xray process stopped.");
        stopService();
    }

    public void startService() {
        if (tunFd != null) return;
        Preferences prefs = new Preferences(this);
        Builder builder = getVpnBuilder(prefs);
        tunFd = builder.establish();
        if (tunFd == null) {
            stopXray();
            return;
        }
        File tproxy_file = new File(getCacheDir(), "tproxy.conf");
        try {
            tproxy_file.createNewFile();
            FileOutputStream fos = new FileOutputStream(tproxy_file, false);
            String tproxy_conf = getTproxyConf(prefs);
            fos.write(tproxy_conf.getBytes());
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            stopXray();
        }
        TProxyStartService(tproxy_file.getAbsolutePath(), tunFd.getFd());
        Intent successIntent = new Intent(ACTION_START);
        successIntent.setPackage(getApplication().getPackageName());
        sendBroadcast(successIntent);
        String channelName = "socks5";
        initNotificationChannel(channelName);
        createNotification(channelName);
    }

    private @NonNull Builder getVpnBuilder(Preferences prefs) {
        String session = "";
        Builder builder = new Builder();
        builder.setBlocking(false);
        builder.setMtu(prefs.getTunnelMtu());
        if (prefs.getBypassLan()) {
            builder.addRoute("10.0.0.0", 8);
            builder.addRoute("172.16.0.0", 12);
            builder.addRoute("192.168.0.0", 16);
        }
        if (prefs.getHttpProxyEnabled()) {
            builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", prefs.getHttpPort()));
        }
        if (prefs.getIpv4()) {
            String addr = prefs.getTunnelIpv4Address();
            int prefix = prefs.getTunnelIpv4Prefix();
            String dns = prefs.getDnsIpv4();
            builder.addAddress(addr, prefix);
            builder.addRoute("0.0.0.0", 0);
            if (!dns.isEmpty()) builder.addDnsServer(dns);
            session += "IPv4";
        }
        if (prefs.getIpv6()) {
            String addr = prefs.getTunnelIpv6Address();
            int prefix = prefs.getTunnelIpv6Prefix();
            String dns = prefs.getDnsIpv6();
            builder.addAddress(addr, prefix);
            builder.addRoute("::", 0);
            if (!dns.isEmpty()) builder.addDnsServer(dns);
            if (!session.isEmpty()) session += " + ";
            session += "IPv6";
        }
        boolean disallowSelf = true;
        if (prefs.getGlobal()) {
            session += "/Global";
        } else {
            for (String appName : prefs.getApps()) {
                try {
                    builder.addAllowedApplication(appName);
                    disallowSelf = false;
                } catch (NameNotFoundException ignored) {
                }
            }
            session += "/per-App";
        }
        if (disallowSelf) {
            String selfName = getApplicationContext().getPackageName();
            try {
                builder.addDisallowedApplication(selfName);
            } catch (NameNotFoundException ignored) {
            }
        }
        builder.setSession(session);
        return builder;
    }

    public void stopService() {
        if (tunFd == null) {
            exit();
            return;
        }
        stopForeground(true);
        TProxyStopService();
        try {
            tunFd.close();
        } catch (IOException ignored) {
        }
        tunFd = null;
        exit();
    }

    private void createNotification(String channelName) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, channelName);
        Notification notify = notification.setContentTitle(getString(R.string.app_name)).setSmallIcon(R.drawable.ic_stat_name).setContentIntent(pi).build();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notify);
        } else {
            startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }
    }

    private void exit() {
        Intent stopIntent = new Intent(ACTION_STOP);
        stopIntent.setPackage(getApplication().getPackageName());
        sendBroadcast(stopIntent);
        stopSelf();
    }

    private void initNotificationChannel(String channelName) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        CharSequence name = getString(R.string.app_name);
        NotificationChannel channel = new NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
    }
}