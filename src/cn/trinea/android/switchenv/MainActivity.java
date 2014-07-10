package cn.trinea.android.switchenv;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import cn.trinea.android.common.util.FileUtils;
import cn.trinea.android.common.util.ListUtils;
import cn.trinea.android.common.util.MapUtils;
import cn.trinea.android.common.util.PackageUtils;
import cn.trinea.android.common.util.PreferencesUtils;
import cn.trinea.android.common.util.ShellUtils;
import cn.trinea.android.common.util.ShellUtils.CommandResult;
import cn.trinea.android.common.util.StringUtils;
import cn.trinea.android.switchenv.constant.Constants;
import cn.trinea.android.switchenv.control.AppControl;
import cn.trinea.android.switchenv.entity.App;

/**
 * Using for switch between test enviroment and online enviroment
 * <ul>
 * <strong>Settings</strong>
 * <li>You can add a new file named switch-env-host.txt in Trinea folder on sdcard, this file means host mapping</li>
 * <li>You can add a new file named switch-env-app-info.txt in Trinea folder on sdcard, this file means app info</li>
 * <li>Source code of trineaandroidcommon.jar, u can get it from <a
 * href="https://github.com/trinea/android-common">trinea-android-common</a></li>
 * <ul>
 * 
 * @author <a href="http://www.trinea.cn" target="_blank">Trinea</a> 2013-7-31
 */
public class MainActivity extends Activity implements OnClickListener {

    private MyHandler           handler;
    private Context             context;

    private PackageManager      packageManager;
    private NotificationManager notiManager;

    private Button              envSwitchBtn;
    private TextView            hostsText;
    private Spinner             packageSpinner;
    private Button              clearAndOpenBtn;
    private Button              clearBtn;

    private String              selectPackageName;
    private int                 selectPosition;

    private Map<String, String> hostMap;
    private List<App>           appList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new MyHandler();
        context = getApplicationContext();
        packageManager = context.getPackageManager();
        notiManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        initView();
        initData();
    }

    private void initView() {
        ActionBar bar = getActionBar();
        bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME);

        envSwitchBtn = (Button)findViewById(R.id.switch_env);
        envSwitchBtn.setOnClickListener(this);
        envSwitchBtn.setEnabled(false);

        hostsText = (TextView)findViewById(R.id.text_hosts);

        clearAndOpenBtn = (Button)findViewById(R.id.button_clear_data_and_open);
        clearAndOpenBtn.setOnClickListener(this);
        clearBtn = (Button)findViewById(R.id.button_clear_data);
        clearBtn.setOnClickListener(this);

        packageSpinner = (Spinner)findViewById(R.id.spinner_package);
        packageSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                App app = appList.get(position);
                if (app != null) {
                    selectPackageName = app.getPackageName();
                    selectPosition = position;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void initData() {
        Context context = getApplicationContext();
        hostMap = AppControl.getHostMap(context);
        appList = AppControl.getAppList(context);
        if (ListUtils.isEmpty(appList)) {
            return;
        }

        String[] dataArray = new String[appList.size()];
        for (int i = 0; i < appList.size(); i++) {
            App app = appList.get(i);
            if (app != null && !StringUtils.isEmpty(app.getAppName()) && !StringUtils.isEmpty(app.getPackageName())) {
                dataArray[i] = app.getAppName() + "(" + app.getPackageName() + ")";
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                dataArray);
        packageSpinner.setAdapter(adapter);
        int selectedPosi = PreferencesUtils.getInt(context, Constants.PREFERENCES_SELECTED_POSITION, 0);
        packageSpinner.setSelection(selectedPosi);
    }

    private void reloadHost() {
        if (MapUtils.isEmpty(hostMap) || ListUtils.isEmpty(appList)) {
            return;
        }

        // init button status
        handler.sendEmptyMessage(isOnlineEnv() ? WHAT_INIT_ONLINE_ENV : WHAT_INIT_TEST_ENV);

        // init host text
        try {
            StringBuilder fileContent = FileUtils.readFile(HOSTS_PATH, "utf-8");
            handler.sendMessage(handler.obtainMessage(WHAT_GET_CONTENT_SUCCESS,
                    fileContent == null ? "" : fileContent.toString()));
        } catch (Exception e) {
            e.printStackTrace();
            handler.sendMessage(handler.obtainMessage(WHAT_GET_CONTENT_FAIL, e.getMessage()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.sendEmptyMessage(WHAT_RELOAD_DATA);
    }

    public void onPause() {
        // save selected position
        PreferencesUtils.putInt(context, Constants.PREFERENCES_SELECTED_POSITION, selectPosition);
        super.onPause();
    }

    /**
     * switch env
     */
    private void switchEnv() {
        envSwitchBtn.setEnabled(false);
        envSwitchBtn.setText(R.string.switching);
        new Thread(new Runnable() {

            @Override
            public void run() {
                if (!modifyHost()) {
                    handler.sendEmptyMessage(WHAT_SET_CONTENT_FAIL);
                }
                reloadHost();
            }
        }).start();
    }

    /**
     * clear selected app data and open it
     * 
     * @param openApp
     */
    private void clearSelectedAppData(final boolean openApp) {
        clearAndOpenBtn.setEnabled(false);
        clearAndOpenBtn.setText(R.string.clear_data_ing);
        clearBtn.setEnabled(false);
        clearBtn.setText(R.string.clear_data_ing);
        ActivityManager am = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(selectPackageName);
        new Thread(new Runnable() {

            @Override
            public void run() {
                String filePath = new StringBuilder().append("/data/data/").append(selectPackageName).toString();
                String[] command;
                command = new String[] {
                        new StringBuilder(32).append("kill $(pgrep ").append(selectPackageName).append(")").toString(),
                        "mount -o rw,remount /system",
                        new StringBuilder().append("rm -R ").append(filePath).append("/cache/* -rf").toString(),
                        new StringBuilder().append("rm -R ").append(filePath).append("/databases/* -rf").toString(),
                        new StringBuilder().append("rm -R ").append(filePath).append("/shared_prefs/* -rf").toString()};
                CommandResult result = ShellUtils.execCommand(command,
                        PackageUtils.isSystemApplication(context) ? false : true);
                // if (result.result == 0) {
                handler.sendMessage(handler.obtainMessage(WHAT_CLEAR_DATA_SUCCESS, openApp ? 1 : 0, 0));
                // } else {
                // handler.sendMessage(handler.obtainMessage(WHAT_CLEAR_DATA_FAIL));
                // }
            }
        }).start();
    }

    /**
     * open app by packageName
     * 
     * @param packageName
     */
    private void openApp(String packageName) {
        Intent openintent = packageManager.getLaunchIntentForPackage(packageName);
        if (openintent != null) {
            startActivity(openintent);
        } else {
            Toast.makeText(context, R.string.open_app_fail, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * if exists domain line than it means test env
     * 
     * @param domainName
     * @return
     */
    private boolean isOnlineEnv() {
        List<String> lineList = null;
        try {
            lineList = FileUtils.readFileToList(HOSTS_PATH, "utf-8");
        } catch (Exception e) {
            e.printStackTrace();
            handler.sendMessage(handler.obtainMessage(WHAT_GET_CONTENT_FAIL, e.getMessage()));
            return true;
        }

        Set<String> domainNameSet = hostMap.keySet();
        if (!ListUtils.isEmpty(lineList)) {
            for (String line : lineList) {
                for (String domainName : domainNameSet) {
                    if (isDomainLine(line, domainName)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * modify hosts file
     * 
     * @param domainName
     * @param testIp
     * @param isOnlineEnv
     * @return
     */
    private boolean modifyHost() {
        List<String> lineList = null;
        try {
            lineList = FileUtils.readFileToList(HOSTS_PATH, "utf-8");
        } catch (Exception e) {
            e.printStackTrace();
            handler.sendMessage(handler.obtainMessage(WHAT_GET_CONTENT_FAIL, e.getMessage()));
            return false;
        }

        boolean isOnlineEnv = true;
        Set<String> domainNameSet = hostMap.keySet();
        if (!ListUtils.isEmpty(lineList)) {
            for (String line : lineList) {
                for (String domainName : domainNameSet) {
                    if (isDomainLine(line, domainName)) {
                        isOnlineEnv = false;
                    }
                }
            }
        }

        boolean isFirstLine = true;
        List<String> commnandList = new ArrayList<String>();
        commnandList.add("mount -o rw,remount /system");
        if (ListUtils.isEmpty(lineList)) {
            if (!isOnlineEnv) {
                return true;
            }
        } else {
            boolean isExist = false;
            for (String line : lineList) {
                if (!StringUtils.isBlank(line)) {
                    for (String domainName : domainNameSet) {
                        if (isDomainLine(line, domainName)) {
                            isExist = true;
                            break;
                        }
                    }

                    if (!isExist) {
                        if (isFirstLine) {
                            isFirstLine = false;
                        }
                        addHostCommand(line, commnandList);
                    }
                }
            }
        }

        // if now is online env, then target is test env
        if (isOnlineEnv) {
            isFirstLine = true;
            Iterator<Entry<String, String>> ite = hostMap.entrySet().iterator();
            while (ite.hasNext()) {
                if (isFirstLine) {
                    isFirstLine = false;
                }
                Entry<String, String> entry = (Map.Entry<String, String>)ite.next();
                addHostCommand(new StringBuilder().append(entry.getValue()).append(" ").append(entry.getKey())
                        .toString(), commnandList);
            }
        }

        if (commnandList.size() == 1) {
            commnandList.add(new StringBuilder().append("echo \" \" > ").append(HOSTS_PATH).toString());
        }
        commnandList.add("chmod 644 " + HOSTS_PATH);
        CommandResult result = ShellUtils.execCommand(commnandList, PackageUtils.isSystemApplication(context) ? false
                : true);
        if (result == null || result.result != 0) {
            Log.e("Modify Host", result == null ? "result is null" : (result.errorMsg == null ? "error msg is null"
                    : result.errorMsg));
            return false;
        }
        return true;
    }

    /**
     * echo host to env, if first one use >, else use >>
     * 
     * @param host
     * @param commnandList
     */
    private void addHostCommand(String host, List<String> commnandList) {
        if (commnandList == null) {
            return;
        }
        String echo = commnandList.size() <= 1 ? "> " : ">> ";
        commnandList.add(new StringBuilder().append("echo \"").append(host).append("\" ").append(echo)
                .append(HOSTS_PATH).toString());
    }

    /**
     * is this line contains domainname
     * 
     * @param lineText
     * @param domainName
     * @return
     */
    private boolean isDomainLine(String lineText, String domainName) {
        if (!StringUtils.isBlank(lineText) && !StringUtils.isEmpty(domainName)) {
            lineText = lineText.trim();
            int lastIndex = lineText.lastIndexOf(domainName);
            return (lastIndex >= 0 && lastIndex + domainName.length() == lineText.length() && (lastIndex == 0 || lineText
                    .charAt(lastIndex - 1) == ' '));
        }
        return false;
    }

    /**
     * show current enviroment
     * 
     * @param rscId
     */
    private void showNotification(int rscId) {
        Notification n = new Notification(R.drawable.switchenv, getResources().getString(rscId),
                (System.currentTimeMillis() + 1000));
        n.flags = Notification.FLAG_NO_CLEAR;
        n.setLatestEventInfo(context, getResources().getString(R.string.app_name), getResources().getString(rscId),
                null);
        n.contentView.setViewVisibility(android.R.id.progress, View.GONE);
        n.contentIntent = PendingIntent.getActivity(context, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        notiManager.notify(0, n);
    }

    @Override
    public void onClick(View v) {
        if (v == null) {
            return;
        }

        int id = v.getId();
        if (id == envSwitchBtn.getId()) {
            switchEnv();
        } else if (id == clearAndOpenBtn.getId()) {
            if (StringUtils.isEmpty(selectPackageName)) {
                Toast.makeText(context, R.string.error_package_not_null, Toast.LENGTH_SHORT).show();
                return;
            }
            clearSelectedAppData(true);
        } else if (id == clearBtn.getId()) {
            if (StringUtils.isEmpty(selectPackageName)) {
                Toast.makeText(context, R.string.error_package_not_null, Toast.LENGTH_SHORT).show();
                return;
            }
            clearSelectedAppData(false);
        }
    }

    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case WHAT_RELOAD_DATA:
                    envSwitchBtn.setEnabled(false);
                    envSwitchBtn.setText(R.string.init);
                    reloadHost();
                    break;
                case WHAT_INIT_TEST_ENV:
                    envSwitchBtn.setEnabled(true);
                    envSwitchBtn.setText(R.string.switch_online_env);
                    showNotification(R.string.test_env);
                    break;
                case WHAT_INIT_ONLINE_ENV:
                    envSwitchBtn.setEnabled(true);
                    envSwitchBtn.setText(R.string.switch_test_env);
                    notiManager.cancel(0);
                    break;
                case WHAT_GET_CONTENT_FAIL:
                    hostsText.setText(getResources().getString(R.string.error_get_hoss) + (String)msg.obj);
                    break;
                case WHAT_SET_CONTENT_FAIL:
                    Toast.makeText(context, R.string.error_set_hoss, Toast.LENGTH_LONG).show();
                    break;
                case WHAT_GET_CONTENT_SUCCESS:
                    String s = (String)msg.obj;
                    if (StringUtils.isEmpty(s)) {
                        hostsText.setText(R.string.null_hosts);
                    } else {
                        hostsText.setText(s);
                    }
                    break;
                case WHAT_CLEAR_DATA_FAIL:
                    Toast.makeText(context, R.string.clear_data_fail, Toast.LENGTH_SHORT).show();
                    clearBtn.setEnabled(true);
                    clearBtn.setText(R.string.clear_data);
                    clearAndOpenBtn.setEnabled(true);
                    clearAndOpenBtn.setText(R.string.clear_and_open_data);
                    break;
                case WHAT_CLEAR_DATA_SUCCESS:
                    clearBtn.setEnabled(true);
                    clearBtn.setText(R.string.clear_data);
                    clearAndOpenBtn.setEnabled(true);
                    clearAndOpenBtn.setText(R.string.clear_and_open_data);
                    if (msg.arg1 == 1) {
                        openApp(selectPackageName);
                    } else {
                        Toast.makeText(context, R.string.clear_data_success, Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static final String HOSTS_PATH               = "/etc/hosts";
    private static final int    WHAT_RELOAD_DATA         = 0;
    private static final int    WHAT_INIT_TEST_ENV       = 1;
    private static final int    WHAT_INIT_ONLINE_ENV     = 2;
    private static final int    WHAT_GET_CONTENT_SUCCESS = 3;
    private static final int    WHAT_GET_CONTENT_FAIL    = 4;
    private static final int    WHAT_SET_CONTENT_FAIL    = 6;
    private static final int    WHAT_CLEAR_DATA_SUCCESS  = 7;
    private static final int    WHAT_CLEAR_DATA_FAIL     = 8;
}
