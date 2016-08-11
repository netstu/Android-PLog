package org.mym.plog;

import android.text.TextUtils;
import android.util.Log;

import org.mym.plog.config.Logger;
import org.mym.plog.config.PLogConfig;

/**
 * Entry class of log module, settings, and init configs are all here.
 *
 * <p>You don't need to create this class since it is only an utility class, for configs,
 * please use {@link #init(PLogConfig)}. <br>
 * Also, it is strongly recommended to create config instance using
 * {@link org.mym.plog.config.PLogConfig.Builder}, instead of directly call constructor.
 * </p>
 * @author Muyangmin
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public final class PLog {

    /**
     * 0 dalvik.system.VMStack.getThreadStackTrace(Native Method)       <br/>
     * 1 java.lang.Thread.getStackTrace(Thread.java:579)                <br/>
     * 2 {@link #getLineNumAndMethodName(int)}                             <br/>
     * 3 {@link #wrapLogStr(int, String, Object...)}                         <br/>
     * 4 {@link #log(int, int, String, String, Object...)}                   <br/>
     * 5 v, d, i, w, e                                                  <br/>
     * 6 invoker
     */
    private static final int STACK_TRACE_INDEX = 6;

    private static PLogConfig mConfig;

    //The constructor of this class is meaningless, so make it private
    private PLog(){
        //Empty
    }

    /**
     * Check and set {@link #mConfig} field. this operation guarantee the reading of config for
     * later operation is safe.
     */
    private static synchronized void safelySetConfig(PLogConfig config) throws RuntimeException {
        PLogConfig.checkConfigSafe(config);
        mConfig = config;
    }

    /**
     * Init PLog using customized config.
     */
    public static void init(PLogConfig config) {
        safelySetConfig(config);
    }

    public static void v(String msg, Object... params) {
        log(Log.VERBOSE, 0, null, msg, params);
    }

    public static void v(String tag, String msg, Object... params) {
        log(Log.VERBOSE, 0, tag, msg, params);
    }

    public static void d(String msg, Object... params) {
        log(Log.DEBUG, 0, null, msg, params);
    }

    public static void d(String tag, String msg, Object... params) {
        log(Log.DEBUG, 0, tag, msg, params);
    }

    public static void i(String msg, Object... params) {
        log(Log.INFO, 0, null, msg, params);
    }

    public static void i(String tag, String msg, Object... params) {
        log(Log.INFO, 0, tag, msg, params);
    }

    public static void w(String msg, Object... params) {
        log(Log.WARN, 0, null, msg, params);
    }

    public static void w(String tag, String msg, Object... params) {
        log(Log.WARN, 0, tag, msg, params);
    }

    public static void e(String msg, Object... params) {
        log(Log.ERROR, 0, null, msg, params);
    }

    public static void e(String tag, String msg, Object... params) {
        log(Log.ERROR, 0, tag, msg, params);
    }

    /**
     * Print {@link PLogConfig#getEmptyMsg()}, using DEBUG Level,
     * or {@link PLogConfig#getEmptyMsgLevel()} if specified.
     */
    public static void empty() {
        int level = mConfig == null ? Log.DEBUG : mConfig.getEmptyMsgLevel();
        log(level, 0, null, null);
    }

    /**
     * Use this method for skipping some middle methods, if necessary.
     * <p><strong>Note: This method is often not recommend; v,d,i,w,e methods is enough for common
     * use.</strong></p>
     * @param level log level, MUST be one of
     *              {@link Log#VERBOSE}, {@link Log#DEBUG}, {@link Log#INFO},
     *              {@link Log#WARN}, {@link Log#ERROR}.
     * @param stackOffset stack offset, often pass an non-negative integer, the default log is 0.
     * @param tag log tag.
     * @param msg log message.
     * @param params log params, optional.
     */
    public static void logWithStackOffset(int level, int stackOffset, String tag, String msg,
                                          Object... params){
        //Just transfer to log() method, for keep same layer level with v,d,i,w,e methods.
        log(level, stackOffset, tag, msg, params);
    }

    /**
     * Core method : internal implementation.
     */
    private static void log(int level, int stackOffset, String tag, String msg, Object... params) {
        checkInitOrUseDefaultConfig();

        //When tag is empty, using global
        if (TextUtils.isEmpty(tag)) {
            tag = mConfig.getGlobalTag();
        }
        //Only concat when tag is not empty and config is specified to true
        else if (mConfig.isForceConcatGlobalTag()){
            tag = mConfig.getGlobalTag() + "-" + tag;
        }

        //If loggable, print it
        if (mConfig.getController().isLogEnabled(level, tag, msg)) {
            String logContent = wrapLogStr(stackOffset, msg, params);
            Logger logger = mConfig.getLogger();
            switch (level) {
                case Log.VERBOSE:
                    logger.v(tag, logContent);
                    break;
                case Log.DEBUG:
                    logger.d(tag, logContent);
                    break;
                case Log.INFO:
                    logger.i(tag, logContent);
                    break;
                case Log.WARN:
                    logger.w(tag, logContent);
                    break;
                case Log.ERROR:
                    logger.e(tag, logContent);
                    break;
            }
        }
    }

    private static void checkInitOrUseDefaultConfig() {
        if (mConfig == null) {
            init(new PLogConfig.Builder().build());
        }
    }

    private static String wrapLogStr(int stackOffset, String msg, Object... params) {
        String lineInfo = null;
        if (mConfig.isKeepLineNumber()) {
            lineInfo = getLineNumAndMethodName(stackOffset);
        }
        String content;
        if (TextUtils.isEmpty(msg)) {
            content = mConfig.getEmptyMsg();
        } else {
            content = String.format(msg, params);
        }
        if (!TextUtils.isEmpty(lineInfo)) {
            content = lineInfo + content;
        }
        return content;
    }

    private static String getLineNumAndMethodName(int stackOffset) {
        final int TARGET_STACK = STACK_TRACE_INDEX + stackOffset;

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace == null || stackTrace.length < TARGET_STACK) {
            return null;
        }
        StackTraceElement element = stackTrace[TARGET_STACK];
        String className = element.getClassName();
        //parse to simple name
        String pkgPath[] = className.split("\\.");
        if (pkgPath.length > 0) {
            className = pkgPath[pkgPath.length - 1];
        }

        //If log in inner class, then class name contains '$', which cause IDE navigate file
        // function not working.
        int innerclassSymbolIndex = className.indexOf("$");
        //is inner class
        String innerClassName = null;
        if (innerclassSymbolIndex!=-1){
            //skip the first symbol
            innerClassName = className.substring(innerclassSymbolIndex+1);
            className = className.substring(0, innerclassSymbolIndex);
        }

        String methodName = element.getMethodName();
        int lineNum = element.getLineNumber();

        //concat inner classname in method string.
        if (mConfig.isKeepInnerClass() && (!TextUtils.isEmpty(innerClassName))){
            methodName = String.format("$%s#%s()", innerClassName, methodName);
        }
        else{
            methodName = String.format("#%s()", methodName);
        }

        return String.format("[(%s.java:%s)%s]", className, lineNum, methodName);
    }

}
