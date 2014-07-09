package cn.trinea.android.switchenv.entity;

/**
 * App
 * 
 * @author <a href="http://www.trinea.cn" target="_blank">Trinea</a> 2013-7-31
 */
public class App {

    private String packageName;
    private String appName;

    public App(String packageName, String appName) {
        this.packageName = packageName;
        this.appName = appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }
}
