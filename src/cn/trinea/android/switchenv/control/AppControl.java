package cn.trinea.android.switchenv.control;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.os.Environment;
import cn.trinea.android.common.util.FileUtils;
import cn.trinea.android.common.util.ListUtils;
import cn.trinea.android.common.util.ResourceUtils;
import cn.trinea.android.common.util.StringUtils;
import cn.trinea.android.switchenv.entity.App;

/**
 * AppControl
 * 
 * @author <a href="http://www.trinea.cn" target="_blank">Trinea</a> 2013-7-31
 */
public class AppControl {

    public static final String FITST_ADD_HOST_FILE  = new StringBuilder()
                                                            .append(Environment.getExternalStorageDirectory()
                                                                    .getAbsolutePath()).append(File.separator)
                                                            .append("Trinea").append(File.separator)
                                                            .append("switch-env-host.txt").toString();
    public static final String SECOND_ADD_HOST_FILE = "switch-env-host.txt";

    public static final String FITST_APP_INFO_FILE  = new StringBuilder()
                                                            .append(Environment.getExternalStorageDirectory()
                                                                    .getAbsolutePath()).append(File.separator)
                                                            .append("Trinea").append(File.separator)
                                                            .append("switch-env-app-info.txt").toString(); ;
    public static final String SECOND_APP_INFO_FILE = "switch-env-app-info.txt";

    public static final String FILE_ENCODE          = "utf-8";

    public static Map<String, String> getHostMap(Context context) {
        List<String> dataList;
        // read from sdcard first, then assets
        if (FileUtils.isFileExist(FITST_ADD_HOST_FILE)) {
            dataList = FileUtils.readFileToList(FITST_ADD_HOST_FILE, FILE_ENCODE);
        } else {
            dataList = ResourceUtils.geFileToListFromAssets(context, SECOND_ADD_HOST_FILE);
        }
        if (ListUtils.isEmpty(dataList)) {
            return null;
        }

        Map<String, String> hostMap = new LinkedHashMap<String, String>();
        for (String s : dataList) {
            // ignore empty or notes
            if (s == null || s.startsWith("#")) {
                continue;
            }

            int index = s.indexOf(" ");
            if (index == -1) {
                continue;
            }

            String first = s.substring(0, index), second = s.substring(index);
            if (!StringUtils.isEmpty(first) || !StringUtils.isEmpty(second)) {
                hostMap.put(first.trim(), second.trim());
            }
        }
        return hostMap;
    }

    public static List<App> getAppList(Context context) {
        List<String> dataList;
        // read from sdcard first, then assets
        if (FileUtils.isFileExist(FITST_APP_INFO_FILE)) {
            dataList = FileUtils.readFileToList(FITST_APP_INFO_FILE, FILE_ENCODE);
        } else {
            dataList = ResourceUtils.geFileToListFromAssets(context, SECOND_APP_INFO_FILE);
        }
        if (ListUtils.isEmpty(dataList)) {
            return null;
        }

        List<App> appList = new ArrayList<App>();
        for (String s : dataList) {
            // ignore empty or notes
            if (s == null || s.startsWith("#")) {
                continue;
            }

            int index = s.indexOf(" ");
            if (index == -1) {
                continue;
            }

            String packageName = s.substring(0, index), appName = s.substring(index);
            if (!StringUtils.isEmpty(packageName) || !StringUtils.isEmpty(appName)) {
                appList.add(new App(packageName.trim(), appName.trim()));
            }
        }
        return appList;
    }
}
