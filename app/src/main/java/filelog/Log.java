package filelog;

public class Log {

    private static final String DEFAULT_TAG = "J2meLoader";

    public static void d(String tag, String message) {
        android.util.Log.d(DEFAULT_TAG, tag + " : " + message);
    }
}