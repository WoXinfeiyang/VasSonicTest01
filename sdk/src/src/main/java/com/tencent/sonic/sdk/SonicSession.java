/*
 * Tencent is pleased to support the open source community by making VasSonic available.
 *
 * Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 *
 *
 */

package com.tencent.sonic.sdk;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.tencent.sonic.sdk.download.SonicDownloadCache;
import com.tencent.sonic.sdk.download.SonicDownloadEngine;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In Sonic, <code>SonicSession</code>s are used to manage the entire process,include
 * obtain the latest data from the server, provide local and latest
 * data to kernel, separate html to template and data, build template
 * and data to html and so on. Each url involves one session at a time,
 * that session will be destroyed when the page is destroyed.
 *
 */

public abstract class SonicSession implements Handler.Callback {

    /**
     * Log filter
     */
    public static final String TAG = SonicConstants.SONIC_SDK_LOG_PREFIX + "SonicSession";

    /**
     * The result keyword to page : the value is <code>srcResultCode</code>
     */
    public static final String WEB_RESPONSE_SRC_CODE = "srcCode";

    /**
     * The result keyword to page : the value is <code>finalResultCode</code>
     */
    public static final String WEB_RESPONSE_CODE = "code";


    public static final String WEB_RESPONSE_EXTRA = "extra";


    /**
     * The all data keyword to page
     */
    public static final String WEB_RESPONSE_DATA = "result";


    public static final String DATA_UPDATE_BUNDLE_PARAMS_DIFF = "_diff_data_";


    public static final String WEB_RESPONSE_LOCAL_REFRESH_TIME = "local_refresh_time";


    /**
     * Name of chrome file thread
     */
    public static final String CHROME_FILE_THREAD = "Chrome_FileThread";

    /**
     * Session state : original.
     * <p>
     * This state means session has not start.
     */
    public static final int STATE_NONE = 0;

    /**
     * Session state : running.
     * <p>
     * This state means session has begun to request data from
     * the server and is processing the data.
     *
     */
    public static final int STATE_RUNNING = 1;

    /**
     * Session state : ready.
     * <p>
     * This state means session data is available when the page
     * initiates a resource interception. In other stats the
     * client(kernel) will wait.
     *
     */
    public static final int STATE_READY = 2;

    /**
     * Session state : destroyed.
     * <p>
     * This state means the session is destroyed.
     */
    public static final int STATE_DESTROY = 3;

    /**
     * The value of "cache-offline" in http(s) response headers.
     * <p>
     * This value means sonic server unavailable, the terminal
     * does not take sonic logic for the next period of time,the
     * value of time is defined in {@link SonicConfig#SONIC_UNAVAILABLE_TIME}
     *
     */
    public static final String OFFLINE_MODE_HTTP = "http";

    /**
     * The value of "cache-offline" in http(s) response headers.
     * <p>
     * This value means sonic will save the latest data, but not refresh
     * page.For example, when sonic mode is data update, sonic will not
     * provide the difference data between local and server to page to refresh
     * the content.
     *
     */
    public static final String OFFLINE_MODE_STORE = "store";

    /**
     * The value of "cache-offline" in http(s) response headers.
     * <p>
     * This value means sonic will save the latest data and refresh page content.
     *
     */
    public static final String OFFLINE_MODE_TRUE = "true";

    /**
     * The value of "cache-offline" in http(s) response headers.
     * <p>
     * This value means sonic will refresh page content but not save date, sonic
     * will remove the local data also.
     *
     */
    public static final String OFFLINE_MODE_FALSE = "false";

    /**
     * Sonic mode : unknown.
     */
    public static final int SONIC_RESULT_CODE_UNKNOWN = -1;

    /**
     * Sonic mode : first load.
     */
    public static final int SONIC_RESULT_CODE_FIRST_LOAD = 1000;

    /**
     * Sonic mode : template change.
     */
    public static final int SONIC_RESULT_CODE_TEMPLATE_CHANGE = 2000;

    /**
     * Sonic mode : data update.
     */
    public static final int SONIC_RESULT_CODE_DATA_UPDATE = 200;

    /**
     * Sonic mode : 304.
     */
    public static final int SONIC_RESULT_CODE_HIT_CACHE = 304;

    /**
     * Sonic original mode.
     * <p>
     *  For example, when local data does not exist, the value is
     *  <code>SONIC_RESULT_CODE_FIRST_LOAD</code>
     *
     */
    protected int srcResultCode = SONIC_RESULT_CODE_UNKNOWN;

    /**
     * Sonic final mode.
     * <p>
     *  For example, when local data does not exist, the <code>srcResultCode</code>
     *  value is <code>SONIC_RESULT_CODE_FIRST_LOAD</code>. If the server data is read
     *  finished, sonic will provide the latest data to kernel when the kernel
     *  initiates a resource interception.This effect is the same as loading local data,
     *  so the sonic mode will be set as <code>SONIC_RESULT_CODE_HIT_CACHE</code>
     *
     */
    protected int finalResultCode = SONIC_RESULT_CODE_UNKNOWN;


    protected static final int COMMON_MSG_BEGIN = 0;

    /**
     * The message to record sonic mode.
     */
    protected static final int CLIENT_MSG_NOTIFY_RESULT = COMMON_MSG_BEGIN + 1;

    /**
     * The message of page ready, its means page want to get the latest session data.
     */
    protected static final int CLIENT_MSG_ON_WEB_READY = COMMON_MSG_BEGIN + 2;

    /**
     * The message of forced to destroy the session.
     */
    protected static final int SESSION_MSG_FORCE_DESTROY = COMMON_MSG_BEGIN + 3;


    protected static final int COMMON_MSG_END = COMMON_MSG_BEGIN + 4;


    protected static final int FILE_THREAD_MSG_BEGIN = 0;

    /**
     * The message of saving sonic cache while server close.
     */
    protected static final int FILE_THREAD_SAVE_CACHE_ON_SERVER_CLOSE = FILE_THREAD_MSG_BEGIN + 1;

    /**
     * The message of saving sonic cache while session finish.
     */
    protected static final int FILE_THREAD_SAVE_CACHE_ON_SESSION_FINISHED = FILE_THREAD_MSG_BEGIN + 2;

    /**
     * Resource Intercept State : none
     */
    protected static final int RESOURCE_INTERCEPT_STATE_NONE = 0;

    /**
     * Resource Intercept State : intercepting in file thread
     */
    protected static final int RESOURCE_INTERCEPT_STATE_IN_FILE_THREAD = 1;

    /**
     * Resource Intercept State : intercepting in other thread(may be IOThread or other else)
     */
    protected static final int RESOURCE_INTERCEPT_STATE_IN_OTHER_THREAD = 2;

    /**
     * Session state, include <code>STATE_NONE</code>, <code>STATE_RUNNING</code>,
     * <code>STATE_READY</code> and <code>STATE_DESTROY</code>.
     */
    protected final AtomicInteger sessionState = new AtomicInteger(STATE_NONE);

    /**
     * Whether the client initiates a resource interception.
     */
    protected final AtomicBoolean wasInterceptInvoked = new AtomicBoolean(false);

    /**
     * Whether the client is ready.
     */
    protected final AtomicBoolean clientIsReady = new AtomicBoolean(false);

    /**
     * Whether notify the result to page.
     */
    private final AtomicBoolean wasNotified = new AtomicBoolean(false);

    /**
     * Whether it is waiting for the file to be saved. If it is true, the session can not
     * be destroyed.
     */
    protected final AtomicBoolean isWaitingForSaveFile = new AtomicBoolean(false);

    /**
     * Whether the session is waiting for destroy.
     */
    protected final AtomicBoolean isWaitingForDestroy = new AtomicBoolean(false);

    /**
     * Whether the session is waiting for data. If it is true, the session can not
     * be destroyed.
     */
    protected final AtomicBoolean isWaitingForSessionThread = new AtomicBoolean(false);


    /**
     * Whether the local html is loaded, it is used only the template changes.
     */
    protected final AtomicBoolean wasOnPageFinishInvoked = new AtomicBoolean(false);



    /**
     * Resource intercept state, include <code>RESOURCE_INTERCEPT_STATE_NONE</code>,
     * <code>RESOURCE_INTERCEPT_STATE_IN_FILE_THREAD</code>,
     * <code>RESOURCE_INTERCEPT_STATE_IN_OTHER_THREAD</code>
     * More about it at {https://codereview.chromium.org/1350553005/#ps20001}
     */
    protected final AtomicInteger resourceInterceptState = new AtomicInteger(RESOURCE_INTERCEPT_STATE_NONE);

    /**
     * Indicate current session is reload or not.
     */
    protected final AtomicBoolean clientIsReload = new AtomicBoolean(false);

    /**
     * Session statics var
     */
    protected SonicSessionStatistics statistics = new SonicSessionStatistics();

    /**
     * Sonic server
     */
    protected volatile SonicServer server;

    /**
     * Sonic sub resource downloader
     */
    protected volatile SonicDownloadEngine resourceDownloaderEngine;

    /**
     * The response for client interception.
     */
    protected volatile InputStream pendingWebResourceStream;

    /**
     * The difference data between local and server data.
     */
    protected String pendingDiffData = "";

    /**
     * Log id
     */
    protected static long sNextSessionLogId = new Random().nextInt(263167);

    final public SonicSessionConfig config;

    public final String id;

    /**
     * Whether current session is preload.
     */
    protected boolean isPreload;

    /**
     * The time of current session created.
     */
    public long createdTime;


    /**
     * The integer id of current session
     */
    public final long sId;

    /**
     * The original url
     */
    public String srcUrl;

    protected volatile SonicSessionClient sessionClient;

    protected final Handler mainHandler = new Handler(Looper.getMainLooper(), this);

    protected final CopyOnWriteArrayList<WeakReference<Callback>> stateChangedCallbackList = new CopyOnWriteArrayList<WeakReference<Callback>>();

    protected SonicDiffDataCallback diffDataCallback;

    protected final Handler fileHandler;
    protected List<String> preloadLinks;

    protected final CopyOnWriteArrayList<WeakReference<SonicSessionCallback>> sessionCallbackList = new CopyOnWriteArrayList<WeakReference<SonicSessionCallback>>();

    /**
     * This intent saves all of the initialization param.
     */
    protected final Intent intent = new Intent();

    /**
     * The interface is used to inform the listeners that the state of the
     * session has changed.
     */
    public interface Callback {

        /**
         * When the session's state changes, this method will be invoked.
         *
         * @param session   Current session.
         * @param oldState  The old state.
         * @param newState  The next state.
         * @param extraData Extra data.
         */
        void onSessionStateChange(SonicSession session, int oldState, int newState, Bundle extraData);
    }

    /**
     * Subclasses must implement this to receive messages.
     */
    @Override
    public boolean handleMessage(Message msg) {

        if (SESSION_MSG_FORCE_DESTROY == msg.what) {
            destroy(true);
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleMessage:force destroy.");
            return true;
        }

        if (isDestroyedOrWaitingForDestroy()) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleMessage error: is destroyed or waiting for destroy.");
            return true;
        }

        if (SonicUtils.shouldLog(Log.DEBUG)) {
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") handleMessage: msg what = " + msg.what + ".");
        }

        return false;
    }

    private void saveSonicCacheOnServerClose(SonicServer sonicServer) {
        // if the session has been destroyed or refresh, exit directly
        if(isDestroyedOrWaitingForDestroy()) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") doSaveSonicCache: save session files fail." +
                    " Current session is destroy (" + isDestroyedOrWaitingForDestroy()  + ") or refresh ( " + (sonicServer != server) + ")");
            return;
        }

        String htmlString = sonicServer.getResponseData(false);
        if (SonicUtils.shouldLog(Log.DEBUG)) {
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") onClose:htmlString size:"
                    + (!TextUtils.isEmpty(htmlString) ? htmlString.length() : 0));
        }

        if (!TextUtils.isEmpty(htmlString)) {
            long startTime = System.currentTimeMillis();
            doSaveSonicCache(sonicServer, htmlString);
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose:separate And save ache finish, cost " + (System.currentTimeMillis() - startTime) + " ms.");
        }

        // Current session can be destroyed if it is waiting for destroy.
        isWaitingForSaveFile.set(false);
        if (postForceDestroyIfNeed()) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose: postForceDestroyIfNeed send destroy message.");
        }
    }

    SonicSession(String id, String url, SonicSessionConfig config) {
        this.id = id;
        this.config = config;
        this.sId = (sNextSessionLogId++);
        this.srcUrl = statistics.srcUrl = url.trim();
        this.createdTime = System.currentTimeMillis();

        fileHandler = new Handler(SonicEngine.getInstance().getRuntime().getFileThreadLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case FILE_THREAD_SAVE_CACHE_ON_SERVER_CLOSE: {
                        final SonicServer sonicServer = (SonicServer) msg.obj;
                        saveSonicCacheOnServerClose(sonicServer);
                        return true;
                    }

                    case FILE_THREAD_SAVE_CACHE_ON_SESSION_FINISHED: {
                        final String htmlString = (String)msg.obj;
                        doSaveSonicCache(server, htmlString);
                        return true;
                    }
                }
                return false;
            }
        });

        SonicConfig sonicConfig = SonicEngine.getInstance().getConfig();
        if (sonicConfig.GET_COOKIE_WHEN_SESSION_CREATE) {
            SonicRuntime runtime = SonicEngine.getInstance().getRuntime();
            String cookie = runtime.getCookie(srcUrl);

            if (!TextUtils.isEmpty(cookie)) {
                intent.putExtra(SonicSessionConnection.HTTP_HEAD_FIELD_COOKIE, cookie);
            }
        }

        if (SonicUtils.shouldLog(Log.INFO)) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") create:id=" + id + ", url = " + url + ".");
        }
    }


    /**
     * Start the sonic process
     */
    public void start() {
        if (!sessionState.compareAndSet(STATE_NONE, STATE_RUNNING)) {
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") start error:sessionState=" + sessionState.get() + ".");
            return;
        }

        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") now post sonic flow task.");

        for (WeakReference<SonicSessionCallback> ref : sessionCallbackList) {
            SonicSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSonicSessionStart();
            }
        }
        statistics.sonicStartTime = System.currentTimeMillis();
        isWaitingForSessionThread.set(true);

        SonicEngine.getInstance().getRuntime().postTaskToSessionThread(new Runnable() {
            @Override
            public void run() {
                runSonicFlow(true);
            }
        });

        notifyStateChange(STATE_NONE, STATE_RUNNING, null);
    }

    private void runSonicFlow(boolean firstRequest) {
        if (STATE_RUNNING != sessionState.get()) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") runSonicFlow error:sessionState=" + sessionState.get() + ".");
            return;
        }

        statistics.sonicFlowStartTime = System.currentTimeMillis();

        String cacheHtml = null;
        SonicDataHelper.SessionData sessionData;
        sessionData = getSessionData(firstRequest);

        if (firstRequest) {
            cacheHtml = SonicCacheInterceptor.getSonicCacheData(this);
            statistics.cacheVerifyTime = System.currentTimeMillis();
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") runSonicFlow verify cache cost " + (statistics.cacheVerifyTime - statistics.sonicFlowStartTime) + " ms");
            handleFlow_LoadLocalCache(cacheHtml); // local cache if exist before connection
        }

        boolean hasHtmlCache = !TextUtils.isEmpty(cacheHtml) || !firstRequest;

        final SonicRuntime runtime = SonicEngine.getInstance().getRuntime();
        if (!runtime.isNetworkValid()) {
            //Whether the network is available
            if (hasHtmlCache && !TextUtils.isEmpty(config.USE_SONIC_CACHE_IN_BAD_NETWORK_TOAST)) {
                runtime.postTaskToMainThread(new Runnable() {
                    @Override
                    public void run() {
                        if (clientIsReady.get() && !isDestroyedOrWaitingForDestroy()) {
                            runtime.showToast(config.USE_SONIC_CACHE_IN_BAD_NETWORK_TOAST, Toast.LENGTH_LONG);
                        }
                    }
                }, 1500);
            }
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") runSonicFlow error:network is not valid!");
        } else {
            handleFlow_Connection(hasHtmlCache, sessionData);
            statistics.connectionFlowFinishTime = System.currentTimeMillis();
        }

        // Update session state
        switchState(STATE_RUNNING, STATE_READY, true);

        isWaitingForSessionThread.set(false);

        // Current session can be destroyed if it is waiting for destroy.
        if (postForceDestroyIfNeed()) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") runSonicFlow:send force destroy message.");
        }
    }


    public boolean refresh() {
        if (!sessionState.compareAndSet(STATE_READY, STATE_RUNNING)) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") refresh error:sessionState=" + sessionState.get() + ".");
            return false;
        }

        wasInterceptInvoked.set(false);
        clientIsReload.set(true);

        srcResultCode = finalResultCode = SONIC_RESULT_CODE_UNKNOWN;


        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") now refresh sonic flow task.");

        statistics.sonicStartTime = System.currentTimeMillis();

        for (WeakReference<SonicSessionCallback> ref : sessionCallbackList) {
            SonicSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSonicSessionRefresh();
            }
        }

        isWaitingForSessionThread.set(true);

        SonicEngine.getInstance().getRuntime().postTaskToSessionThread(new Runnable() {
            @Override
            public void run() {
                runSonicFlow(false);
            }
        });

        notifyStateChange(STATE_READY, STATE_RUNNING, null);
        return true;
    }

    protected Intent createConnectionIntent(SonicDataHelper.SessionData sessionData) {
        Intent connectionIntent = new Intent();
        SonicUtils.log(TAG, Log.INFO, String.format("Session (%s) send sonic request, etag=(%s), templateTag=(%s)", id, sessionData.eTag, sessionData.templateTag));
        connectionIntent.putExtra(getCustomHeadFieldEtagKey(), sessionData.eTag);
        connectionIntent.putExtra(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG, sessionData.templateTag);

        String hostDirectAddress = SonicEngine.getInstance().getRuntime().getHostDirectAddress(srcUrl);
        if (!TextUtils.isEmpty(hostDirectAddress)) {
            connectionIntent.putExtra(SonicSessionConnection.DNS_PREFETCH_ADDRESS, hostDirectAddress);
            statistics.isDirectAddress = true;
        }

        SonicRuntime runtime = SonicEngine.getInstance().getRuntime();
        SonicConfig sonicConfig = SonicEngine.getInstance().getConfig();
        if (!sonicConfig.GET_COOKIE_WHEN_SESSION_CREATE) {
            String cookie = runtime.getCookie(srcUrl);
            if (!TextUtils.isEmpty(cookie)) {
                connectionIntent.putExtra(SonicSessionConnection.HTTP_HEAD_FIELD_COOKIE, cookie);
            }
        } else {
            connectionIntent.putExtra(SonicSessionConnection.HTTP_HEAD_FIELD_COOKIE, intent.getStringExtra(SonicSessionConnection.HTTP_HEAD_FIELD_COOKIE));
        }

        String userAgent = runtime.getUserAgent();
        if (!TextUtils.isEmpty(userAgent)) {
            userAgent += " Sonic/" + SonicConstants.SONIC_VERSION_NUM;
        } else {
            userAgent = "Sonic/" + SonicConstants.SONIC_VERSION_NUM;
        }
        connectionIntent.putExtra(SonicSessionConnection.HTTP_HEAD_FILED_USER_AGENT, userAgent);
        return connectionIntent;

    }

    /**
     * Initiate a network request to obtain server data.
     *
     * @param hasCache Indicates local sonic cache is exist or not.
     * @param sessionData  SessionData holds eTag templateTag
     */
    protected void handleFlow_Connection(boolean hasCache, SonicDataHelper.SessionData sessionData) {
        // create connection for current session
        statistics.connectionFlowStartTime = System.currentTimeMillis();

        if (config.SUPPORT_CACHE_CONTROL && statistics.connectionFlowStartTime < sessionData.expiredTime) {
            if (SonicUtils.shouldLog(Log.DEBUG)) {
                SonicUtils.log(TAG, Log.DEBUG,  "session(" + sId + ") won't send any request in " + (sessionData.expiredTime - statistics.connectionFlowStartTime) + ".ms");
            }
            for (WeakReference<SonicSessionCallback> ref : sessionCallbackList) {
                SonicSessionCallback callback = ref.get();
                if (callback != null) {
                    callback.onSessionHitCache();
                }
            }
            return;
        }

        server = new SonicServer(this, createConnectionIntent(sessionData));

        // Connect to web server
        int responseCode = server.connect();
        if (SonicConstants.ERROR_CODE_SUCCESS == responseCode) {
            responseCode = server.getResponseCode();
            // If the page has set cookie, sonic will set the cookie to kernel.
            long startTime = System.currentTimeMillis();
            Map<String, List<String>> headerFieldsMap = server.getResponseHeaderFields();
            if (SonicUtils.shouldLog(Log.DEBUG)) {
                SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") connection get header fields cost = " + (System.currentTimeMillis() - startTime) + " ms.");
            }

            startTime = System.currentTimeMillis();
            setCookiesFromHeaders(headerFieldsMap, shouldSetCookieAsynchronous());
            if (SonicUtils.shouldLog(Log.DEBUG)) {
                SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") connection set cookies cost = " + (System.currentTimeMillis() - startTime) + " ms.");
            }
        }

        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_Connection: respCode = " + responseCode + ", cost " + (System.currentTimeMillis() - statistics.connectionFlowStartTime) + " ms.");

        // Destroy before server response
        if (isDestroyedOrWaitingForDestroy()) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_Connection error: destroy before server response!");
            return;
        }

        // when find preload links in headers
        String preloadLink = server.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_LINK);
        if (!TextUtils.isEmpty(preloadLink)) {
            preloadLinks = Arrays.asList(preloadLink.split(";"));
            handleFlow_PreloadSubResource();
        }

        // When response code is 304
        if (HttpURLConnection.HTTP_NOT_MODIFIED == responseCode) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_Connection: Server response is not modified.");
            handleFlow_NotModified();
            return;
        }

        // When response code is not 304 nor 200
        if (HttpURLConnection.HTTP_OK != responseCode) {
            handleFlow_HttpError(responseCode);
            SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, responseCode);
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_Connection error: response code(" + responseCode + ") is not OK!");
            return;
        }

        String cacheOffline = server.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_Connection: cacheOffline is " + cacheOffline + ".");

        // When cache-offline is "http": which means sonic server is in bad condition, need feed back to run standard http request.
        if (OFFLINE_MODE_HTTP.equalsIgnoreCase(cacheOffline)) {
            if (hasCache) {
                //stop loading local sonic cache.
                handleFlow_ServiceUnavailable();
            }
            long unavailableTime = System.currentTimeMillis() + SonicEngine.getInstance().getConfig().SONIC_UNAVAILABLE_TIME;
            SonicDataHelper.setSonicUnavailableTime(id, unavailableTime);

            for (WeakReference<SonicSessionCallback> ref : sessionCallbackList) {
                SonicSessionCallback callback = ref.get();
                if (callback != null) {
                    callback.onSessionUnAvailable();
                }
            }
            return;
        }

        // When cacheHtml is empty, run First-Load flow
        if (!hasCache) {
            handleFlow_FirstLoad();
            return;
        }

        // Handle cache-offline : false or null.
        if (TextUtils.isEmpty(cacheOffline) || OFFLINE_MODE_FALSE.equalsIgnoreCase(cacheOffline)) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_Connection error: Cache-Offline is empty or false!");
            SonicUtils.removeSessionCache(id);
            return;
        }


        String eTag = server.getResponseHeaderField(getCustomHeadFieldEtagKey());
        String templateChange = server.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_CHANGE);

        // When eTag is empty, run fix logic
        if (TextUtils.isEmpty(eTag) || TextUtils.isEmpty(templateChange)) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_Connection error: eTag is ( " + eTag + " ) , templateChange is ( " + templateChange + " )!");
            SonicUtils.removeSessionCache(id);
            return;
        }

        // When templateChange is false : means data update
        if ("false".equals(templateChange) || "0".equals(templateChange)) {
            handleFlow_DataUpdate(server.getUpdatedData());
        } else {
            handleFlow_TemplateChange(server.getResponseData(clientIsReload.get()));
        }
    }

    @Nullable
    private SonicDataHelper.SessionData getSessionData(boolean firstRequest) {
        SonicDataHelper.SessionData sessionData;
        if (firstRequest) {
            sessionData = SonicDataHelper.getSessionData(id);
        } else {
            //get sessionData from last connection
            if (server != null) {
                sessionData = new SonicDataHelper.SessionData();
                sessionData.eTag = server.getResponseHeaderField(getCustomHeadFieldEtagKey());
                sessionData.templateTag = server.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG);
                if ((TextUtils.isEmpty(sessionData.eTag) || TextUtils.isEmpty(sessionData.templateTag)) && config.SUPPORT_LOCAL_SERVER) {
                    server.separateTemplateAndData();
                    sessionData.eTag = server.getResponseHeaderField(getCustomHeadFieldEtagKey());
                    sessionData.templateTag = server.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG);
                }
                sessionData.sessionId = id;
            } else {
                SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") runSonicFlow error:server is not valid!");
                sessionData = new SonicDataHelper.SessionData();
            }
        }
        return sessionData;
    }

    protected abstract void handleFlow_LoadLocalCache(String cacheHtml);

    /**
     * Handle sonic first {@link SonicSession#SONIC_RESULT_CODE_FIRST_LOAD} logic.
     */
    protected abstract void handleFlow_FirstLoad();


    /**
     * Handle data update {@link SonicSession#SONIC_RESULT_CODE_DATA_UPDATE} logic.
     * @param serverRsp Server response data.
     */
    protected abstract void handleFlow_DataUpdate(String serverRsp);

    /**
     * Handle template update {@link SonicSession#SONIC_RESULT_CODE_TEMPLATE_CHANGE} logic.
     * @param newHtml new Html string from web-server
     */
    protected abstract void handleFlow_TemplateChange(String newHtml);

    protected abstract void handleFlow_HttpError(int responseCode);

    protected abstract void handleFlow_ServiceUnavailable();

    protected void handleFlow_NotModified() {
        Message msg = mainHandler.obtainMessage(CLIENT_MSG_NOTIFY_RESULT);
        msg.arg1 = SONIC_RESULT_CODE_HIT_CACHE;
        msg.arg2 = SONIC_RESULT_CODE_HIT_CACHE;
        mainHandler.sendMessage(msg);

        for (WeakReference<SonicSessionCallback> ref : sessionCallbackList) {
            SonicSessionCallback callback = ref.get();
            if (callback != null) {
                callback.onSessionHitCache();
            }
        }
    }

    /**
     * Handle sub resource preload when find "sonic-link" header in http response.
     */
    private void handleFlow_PreloadSubResource() {
        if (preloadLinks == null || preloadLinks.isEmpty()) {
            return;
        }
        SonicEngine.getInstance().getRuntime().postTaskToThread(new Runnable() {
            @Override
            public void run() {
                if (resourceDownloaderEngine == null) {
                    resourceDownloaderEngine = new SonicDownloadEngine(SonicDownloadCache.getSubResourceCache());
                }
                resourceDownloaderEngine.addSubResourcePreloadTask(preloadLinks);
            }
        }, 0);
    }



    void setIsPreload(String url) {
        this.isPreload = true;
        this.srcUrl = statistics.srcUrl = url.trim();
        if (SonicUtils.shouldLog(Log.INFO)) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") is preload, new url=" + url + ".");
        }
    }

    public boolean isPreload() {
        return isPreload;
    }

    public SonicSessionStatistics getStatistics() {
        return statistics;
    }

    protected boolean addSessionStateChangedCallback(Callback callback) {
        return stateChangedCallbackList.add(new WeakReference<Callback>(callback));
    }

    protected boolean removeSessionStateChangedCallback(Callback callback) {
        return stateChangedCallbackList.remove(new WeakReference<Callback>(callback));
    }


    public boolean addSessionCallback(SonicSessionCallback callback) {
        return sessionCallbackList.add(new WeakReference<SonicSessionCallback>(callback));
    }

    public boolean removeSessionCallback(SonicSessionCallback callback) {
        WeakReference<SonicSessionCallback> ref = null;
        for (WeakReference<SonicSessionCallback> reference : sessionCallbackList) {
            if (reference != null && reference.get() == callback) {
                ref = reference;
                break;
            }
        }

        if (ref != null) {
            return sessionCallbackList.remove(ref);
        } else {
            return false;
        }
    }

    public String getCurrentUrl() {
        return srcUrl;
    }

    public int getFinalResultCode() {
        return finalResultCode;
    }

    public int getSrcResultCode() {
        return srcResultCode;
    }

    public boolean isDestroyedOrWaitingForDestroy() {
        return STATE_DESTROY == sessionState.get() || isWaitingForDestroy.get();
    }

    /**
     * Destroy the session if it is waiting for destroy and it is can be destroyed.
     *
     * @return Return true if the session is waiting for destroy and it is can be destroyed.
     */
    protected boolean postForceDestroyIfNeed() {
        if (isWaitingForDestroy.get() && canDestroy()) {
            mainHandler.sendEmptyMessage(SESSION_MSG_FORCE_DESTROY);
            return true;
        }
        return false;
    }

    protected boolean canDestroy() {
        if (isWaitingForSessionThread.get() || isWaitingForSaveFile.get()) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") canDestroy:false, isWaitingForSessionThread=" + isWaitingForDestroy.get() + ", isWaitingForSaveFile=" + isWaitingForSaveFile.get());
            return false;
        }
        return true;
    }

    protected boolean switchState(int fromState, int toState, boolean notify) {
        if (sessionState.compareAndSet(fromState, toState)) {
            if (notify) {
                synchronized (sessionState) {
                    sessionState.notify();
                }
            }
            notifyStateChange(fromState, toState, null);
            return true;
        }
        return false;
    }

    /**
     * If the kernel obtain inputStream from a <code>SonicSessionStream</code>, the inputStream
     * will be closed when the kernel reads the data.This method is invoked when the sonicSessionStream
     * close.
     *
     * <p>
     *  If the html is read complete, sonic will separate the html to template and data, and save these
     *  data.
     *
     * @param sonicServer The actual server connection of current SonicSession.
     * @param readComplete Whether the html is read complete.
     */
    public void onServerClosed(final SonicServer sonicServer, final boolean readComplete) {
        // if the session has been destroyed, exit directly
        if(isDestroyedOrWaitingForDestroy()) {
            return;
        }

        // set pendingWebResourceStream to null???or it has a problem when client reload the page.
        if (null != pendingWebResourceStream) {
            pendingWebResourceStream = null;
        }

        isWaitingForSaveFile.set(true);
        long onCloseStartTime = System.currentTimeMillis();

        //Separate and save html.
        if (readComplete) {
            String cacheOffline = sonicServer.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);
            if (SonicUtils.needSaveData(config.SUPPORT_CACHE_CONTROL, cacheOffline, sonicServer.getResponseHeaderFields())) {
                SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose:offline->" + cacheOffline + " , post separateAndSaveCache task.");

                Message message = Message.obtain();
                message.what = FILE_THREAD_SAVE_CACHE_ON_SERVER_CLOSE;
                message.obj = sonicServer;
                fileHandler.sendMessageDelayed(message, 1500);
                return;
            }
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose:offline->" + cacheOffline + " , so do not need cache to file.");
        } else {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") onClose error:readComplete = false!" );
        }

        // Current session can be destroyed if it is waiting for destroy.
        isWaitingForSaveFile.set(false);
        if (postForceDestroyIfNeed()) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose: postForceDestroyIfNeed send destroy message in chromium_io thread.");
        }

        if (SonicUtils.shouldLog(Log.DEBUG)) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") onClose cost " + (System.currentTimeMillis() - onCloseStartTime) + " ms.");
        }
    }

    protected void postTaskToSaveSonicCache(final String htmlString) {
        Message msg = Message.obtain();
        msg.what = FILE_THREAD_SAVE_CACHE_ON_SESSION_FINISHED;
        msg.obj = htmlString;

        fileHandler.sendMessageDelayed(msg, 1500);
    }

    protected void doSaveSonicCache(SonicServer sonicServer, String htmlString) {
        // if the session has been destroyed, exit directly
        if(isDestroyedOrWaitingForDestroy() || server == null) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") doSaveSonicCache: save session files fail. Current session is destroy!");
            return;
        }

        long startTime = System.currentTimeMillis();
        String template = sonicServer.getTemplate();
        String updatedData = sonicServer.getUpdatedData();

        if (!TextUtils.isEmpty(htmlString) && !TextUtils.isEmpty(template)) {
            String newHtmlSha1 = sonicServer.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_HTML_SHA1);
            if (TextUtils.isEmpty(newHtmlSha1)) {
                newHtmlSha1 = SonicUtils.getSHA1(htmlString);
            }

            String eTag = sonicServer.getResponseHeaderField(getCustomHeadFieldEtagKey());
            String templateTag = sonicServer.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG);

            Map<String, List<String>> headers = sonicServer.getResponseHeaderFields();
            for (WeakReference<SonicSessionCallback> ref : sessionCallbackList) {
                SonicSessionCallback callback = ref.get();
                if (callback != null) {
                    callback.onSessionSaveCache(htmlString, template, updatedData);
                }
            }

            if (SonicUtils.saveSessionFiles(id, htmlString, template, updatedData, headers)) {
                long htmlSize = new File(SonicFileUtils.getSonicHtmlPath(id)).length();
                SonicUtils.saveSonicData(id, eTag, templateTag, newHtmlSha1, htmlSize, headers);
            } else {
                SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") doSaveSonicCache: save session files fail.");
                SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, SonicConstants.ERROR_CODE_WRITE_FILE_FAIL);
            }
        } else {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") doSaveSonicCache: save separate template and data files fail.");
            SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, SonicConstants.ERROR_CODE_SPLIT_HTML_FAIL);
        }

        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") doSaveSonicCache: finish, cost " + (System.currentTimeMillis() - startTime) + "ms.");
    }

    /**
     * When the session state changes, notify the listeners.
     *
     * @param oldState  The old state.
     * @param newState  The nex state.
     * @param extraData The extra data.
     */
    protected void notifyStateChange(int oldState, int newState, Bundle extraData) {
        Callback callback;
        for (WeakReference<Callback> callbackWeakRef : stateChangedCallbackList) {
            callback = callbackWeakRef.get();
            if (null != callback) {
                callback.onSessionStateChange(this, oldState, newState, extraData);
            }
        }
    }

    /**
     * Record the sonic mode, notify the result to page if necessary.
     *
     * @param srcCode   The original mode.
     * @param finalCode The final mode.
     * @param notify    Whether notify te result to page.
     */
    protected void setResult(int srcCode, int finalCode, boolean notify) {
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ")  setResult: srcCode=" + srcCode + ", finalCode=" + finalCode + ".");
        statistics.originalMode = srcResultCode = srcCode;
        statistics.finalMode = finalResultCode = finalCode;

        if (!notify) return;

        if (wasNotified.get()) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ")  setResult: notify error -> already has notified!");
        }

        if (null == diffDataCallback) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ")  setResult: notify fail as webCallback is not set, please wait!");
            return;
        }

        if (this.finalResultCode == SONIC_RESULT_CODE_UNKNOWN) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ")  setResult: notify fail finalResultCode is not set, please wait!");
            return;
        }

        wasNotified.compareAndSet(false, true);

        final JSONObject json = new JSONObject();
        try {
            if (finalResultCode == SONIC_RESULT_CODE_DATA_UPDATE) {
                JSONObject pendingObject = new JSONObject(pendingDiffData);

                if (!pendingObject.has("local_refresh_time")) {
                    SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") setResult: no any updated data. " + pendingDiffData);
                    pendingDiffData = "";
                    return;
                } else {
                    long timeDelta = System.currentTimeMillis() - pendingObject.optLong("local_refresh_time", 0);
                    if (timeDelta > 30 * 1000) {
                        SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") setResult: notify fail as receive js call too late, " + (timeDelta / 1000.0) + " s.");
                        pendingDiffData = "";
                        return;
                    } else {
                        if (SonicUtils.shouldLog(Log.DEBUG)) {
                            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") setResult: notify receive js call in time: " + (timeDelta / 1000.0) + " s.");
                        }
                        if (timeDelta > 0) json.put("local_refresh_time", timeDelta);
                    }
                }


                pendingObject.remove(WEB_RESPONSE_LOCAL_REFRESH_TIME);
                json.put(WEB_RESPONSE_DATA, pendingObject.toString());
            }
            json.put(WEB_RESPONSE_CODE, finalResultCode);
            json.put(WEB_RESPONSE_SRC_CODE, srcResultCode);

            final JSONObject extraJson = new JSONObject();
            if (server != null) {
                extraJson.put(getCustomHeadFieldEtagKey(), server.getResponseHeaderField(getCustomHeadFieldEtagKey()));
                extraJson.put(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG, server.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG));
                extraJson.put(SonicSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE, server.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE));
            }
            extraJson.put("isReload", clientIsReload);

            json.put(WEB_RESPONSE_EXTRA, extraJson);
        } catch (Throwable e) {
            e.printStackTrace();
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") setResult: notify error -> " + e.getMessage());
        }

        if (SonicUtils.shouldLog(Log.DEBUG)) {
            String logStr = json.toString();
            if (logStr.length() > 512) {
                logStr = logStr.substring(0, 512);
            }
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") setResult: notify now call jsCallback, jsonStr = " + logStr);
        }


        pendingDiffData = null;
        long delta = 0L;
        if (clientIsReload.get()) {
            delta = System.currentTimeMillis() - statistics.diffDataCallbackTime;
            delta = delta >= 2000 ? 0L : delta;
        }

        if (delta > 0L ) {
            delta = 2000L - delta;
            SonicEngine.getInstance().getRuntime().postTaskToMainThread(new Runnable() {
                @Override
                public void run() {
                    if (diffDataCallback != null) {
                        diffDataCallback.callback(json.toString());
                        statistics.diffDataCallbackTime = System.currentTimeMillis();
                    }
                }
            }, delta);
        } else {
            diffDataCallback.callback(json.toString());
            statistics.diffDataCallbackTime = System.currentTimeMillis();
        }

    }

    public boolean bindClient(SonicSessionClient client) {
        if (null == this.sessionClient) {
            this.sessionClient = client;
            client.bindSession(this);
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") bind client.");
            return true;
        }
        return false;
    }

    /**
     * Client informs sonic that it is ready.
     * Client ready means it's webview has been initialized, can start load url or load data.
     *
     * @return True if it is set for the first time
     */
    public boolean onClientReady() {
        return false;
    }

    /**
     * When the webview initiates a resource interception, the client invokes the method to retrieve the data
     *
     * @param url The url of this session
     * @return Return the data to kernel
     */
    public final Object onClientRequestResource(String url) {
        String currentThreadName = Thread.currentThread().getName();
        if (CHROME_FILE_THREAD.equals(currentThreadName)) {
            resourceInterceptState.set(RESOURCE_INTERCEPT_STATE_IN_FILE_THREAD);
        } else {
            resourceInterceptState.set(RESOURCE_INTERCEPT_STATE_IN_OTHER_THREAD);
            if (SonicUtils.shouldLog(Log.DEBUG)) {
                SonicUtils.log(TAG, Log.DEBUG, "onClientRequestResource called in " + currentThreadName + ".");
            }
        }
        Object object = isMatchCurrentUrl(url)
                ? onRequestResource(url)
                : (resourceDownloaderEngine != null ? resourceDownloaderEngine.onRequestSubResource(url, this) : null);
        resourceInterceptState.set(RESOURCE_INTERCEPT_STATE_NONE);
        return object;
    }

    /**
     * Whether should set cookie asynchronous or not , if {@code onClientRequestResource} is calling
     * in IOThread, it should not call set cookie synchronous which will handle in IOThread as it may
     * cause deadlock
     * More about it see {https://issuetracker.google.com/issues/36989494#c8}
     * Fix VasSonic issue {https://github.com/Tencent/VasSonic/issues/90}
     *
     * @return Return the data to kernel
     */
    protected boolean shouldSetCookieAsynchronous() {
        return RESOURCE_INTERCEPT_STATE_IN_OTHER_THREAD == resourceInterceptState.get();
    }

    /**
     * Set cookies to webview from headers
     *
     * @param headers headers from server response
     * @param executeInNewThread whether execute in new thread or not
     * @return Set cookie success or not
     */
    protected boolean setCookiesFromHeaders(Map<String, List<String>> headers, boolean executeInNewThread) {
        if (null != headers) {
            final List<String> cookies = headers.get(SonicSessionConnection.HTTP_HEAD_FILED_SET_COOKIE.toLowerCase());
            if (null != cookies && 0 != cookies.size()) {
                if (!executeInNewThread) {
                    return SonicEngine.getInstance().getRuntime().setCookie(getCurrentUrl(), cookies);
                } else {
                    SonicUtils.log(TAG, Log.INFO, "setCookiesFromHeaders asynchronous in new thread.");
                    SonicEngine.getInstance().getRuntime().postTaskToThread(new Runnable() {
                        @Override
                        public void run() {
                            SonicEngine.getInstance().getRuntime().setCookie(getCurrentUrl(), cookies);
                        }
                    }, 0L);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * When the webview initiates a main resource interception, the client invokes this method to retrieve the data
     *
     * @param url The url of this session
     * @return Return the data to kernel
     */
    protected Object onRequestResource(String url) {
        return null;
    }



    /**
     * Client will call this method to obtain the update data when the page shows the content.
     *
     * @param diffDataCallback  Sonic provides the latest data to the page through this callback
     * @return The result
     */
    public boolean onWebReady(SonicDiffDataCallback diffDataCallback) {
        return false;
    }

    public boolean onClientPageFinished(String url) {
        if (isMatchCurrentUrl(url)) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClientPageFinished:url=" + url + ".");
            wasOnPageFinishInvoked.set(true);
            return true;
        }
        return false;
    }

    /**
     * Whether the incoming url matches the current url,it will
     * ignore url parameters
     *
     * @param url The incoming url.
     * @return Whether the incoming url matches the current url.
     */
    public boolean isMatchCurrentUrl(String url) {
        try {
            Uri currentUri = Uri.parse(srcUrl);
            Uri uri = Uri.parse(url);

            String currentPath = (currentUri.getHost() + currentUri.getPath());
            String pendingPath = uri.getHost() + uri.getPath();

            if (currentUri.getHost().equalsIgnoreCase(uri.getHost())) {
                if (!currentPath.endsWith("/")) currentPath = currentPath + "/";
                if (!pendingPath.endsWith("/")) pendingPath = pendingPath + "/";
                return currentPath.equalsIgnoreCase(pendingPath);
            }
        } catch (Throwable e) {
            SonicUtils.log(TAG, Log.ERROR, "isMatchCurrentUrl error:" + e.getMessage());
        }
        return false;
    }

    /**
     * Get header info with the original url of current session.
     *
     * @return The header info.
     */
    protected HashMap<String, String> getHeaders() {
        if (null != server) {
            return SonicUtils.getFilteredHeaders(server.getResponseHeaderFields());
        }
        return null;
    }

    /**
     * Get the charset from the latest response http header.
     * @return The charset.
     */
    protected String getCharsetFromHeaders() {
        HashMap<String, String> headers = getHeaders();
        return getCharsetFromHeaders(headers);
    }

    public String getCharsetFromHeaders(Map<String, String> headers) {
        String charset = SonicUtils.DEFAULT_CHARSET;
        String key = SonicSessionConnection.HTTP_HEAD_FIELD_CONTENT_TYPE.toLowerCase();
        if (headers != null && headers.containsKey(key)) {
            String headerValue = headers.get(key);
            if (!TextUtils.isEmpty(headerValue) ) {
                charset = SonicUtils.getCharset(headerValue);
            }
        }
        return charset;
    }

    /**
     * Get header info from local cache headers
     *
     * @return The header info.
     */
    protected HashMap<String, String> getCacheHeaders() {
        String headerFilePath = SonicFileUtils.getSonicHeaderPath(id);
        return SonicUtils.getFilteredHeaders(SonicFileUtils.getHeaderFromLocalCache(headerFilePath));
    }

    public SonicSessionClient getSessionClient() {
        return sessionClient;
    }

    protected String getCustomHeadFieldEtagKey() {
        return server != null ? server.getCustomHeadFieldEtagKey() : SonicSessionConnection.CUSTOM_HEAD_FILED_ETAG;
    }

    public void destroy() {
        destroy(false);
    }

    protected void destroy(boolean force) {
        int curState = sessionState.get();
        if (STATE_DESTROY != curState) {

            if (null != sessionClient) {
                sessionClient = null;
            }

            if (null != pendingWebResourceStream) {
                try {
                    pendingWebResourceStream.close();
                } catch (Throwable e) {
                    SonicUtils.log(TAG, Log.ERROR, "pendingWebResourceStream.close error:" + e.getMessage());
                }
                pendingWebResourceStream = null;
            }

            if (null != pendingDiffData) {
                pendingDiffData = null;
            }

            clearSessionData();

            checkAndClearCacheData();

            if (force || canDestroy()) {
                sessionState.set(STATE_DESTROY);
                synchronized (sessionState) {
                    sessionState.notify();
                }

                if (null != server && !force) {
                    server.disconnect();
                    server = null;
                }

                notifyStateChange(curState, STATE_DESTROY, null);

                mainHandler.removeMessages(SESSION_MSG_FORCE_DESTROY);

                stateChangedCallbackList.clear();

                isWaitingForDestroy.set(false);

                for (WeakReference<SonicSessionCallback> ref : sessionCallbackList) {
                    SonicSessionCallback callback = ref.get();
                    if (callback != null) {
                        callback.onSessionDestroy();
                    }
                }
                SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") final destroy, force=" + force + ".");
                return;
            }

            if (isWaitingForDestroy.compareAndSet(false, true)) {
                mainHandler.sendEmptyMessageDelayed(SESSION_MSG_FORCE_DESTROY, 6000);
                SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") waiting for destroy, current state =" + curState + ".");
            }
        }
    }

    protected void clearSessionData() {

    }

    /**
     * check and clear the sonic cache and resource cache
     */
    private void checkAndClearCacheData() {
        SonicEngine.getInstance().getRuntime().postTaskToThread(new Runnable() {
            @Override
            public void run() {
                if (SonicUtils.shouldClearCache(SonicEngine.getInstance().getConfig().SONIC_CACHE_CHECK_TIME_INTERVAL)) {
                    SonicEngine.getInstance().trimSonicCache();
                    SonicUtils.saveClearCacheTime(System.currentTimeMillis());
                }
            }
        }, 50);
    }
}
