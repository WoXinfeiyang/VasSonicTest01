package com.fxj.VasSonicTest01;

import android.annotation.TargetApi;
import android.app.Activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.tencent.sonic.sdk.SonicConfig;
import com.tencent.sonic.sdk.SonicEngine;
import com.tencent.sonic.sdk.SonicSession;
import com.tencent.sonic.sdk.SonicSessionConfig;

public class MainActivity extends Activity {

    private static final String TAG=MainActivity.class.getSimpleName()+"_fxj";

    private Button btnGo;
    private EditText etUrl;
    private WebView webView;

    private SonicSession sonicSession;

    private String url="http://www.baidu.com";

    private SonicSessionClientImpl sonicSessionClient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnGo=findViewById(R.id.btn_go);
        etUrl=findViewById(R.id.et_url);


        // step 1: init sonic engine if necessary, or maybe u can do this when application created
        if (!SonicEngine.isGetInstanceAllowed()) {
            SonicEngine.createInstance(new SonicRuntimeImpl(getApplication()), new SonicConfig.Builder().build());
        }

        long initWebViewTimeStart=System.currentTimeMillis();
        webView=findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, MainActivity.this.url);
                Log.d(TAG,"##WebViewClient.onPageFinished##url="+url+",sonicSession="+sonicSession);
                if (sonicSession != null&&sonicSession.getSessionClient()!=null) {
                    sonicSession.getSessionClient().pageFinish(url);
                }
            }

            @TargetApi(21)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return shouldInterceptRequest(view, request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                Log.d(TAG,"##WebViewClient.shouldInterceptRequest##url="+url+",sonicSession="+sonicSession);
                if (sonicSession != null) {
                    //step 6: Call sessionClient.requestResource when host allow the application
                    // to return the local data .
                    return (WebResourceResponse) sonicSession.getSessionClient().requestResource(url);
                }
                return null;
            }
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG,"##WebChromeClient.onProgressChanged##newProgress="+newProgress);
            }
        });

        // init webview settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        long initWebViewTimeEnd=System.currentTimeMillis();
        Log.d(TAG,"init WebView time cost="+(initWebViewTimeEnd-initWebViewTimeStart)+"ms,initWebViewTimeEnd="+initWebViewTimeEnd+",initWebViewTimeStart="+initWebViewTimeStart);

        this.btnGo.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                toLoadUrl();
            }
        });
    }

    private void toLoadUrl(){
        // step 2: Create SonicSession
        url=this.etUrl.getText().toString();
        if(TextUtils.isEmpty(url)||!url.startsWith("http://")&&!url.startsWith("https://")){
            Toast.makeText(MainActivity.this,"请检查输入url是否正确",Toast.LENGTH_LONG).show();
            return;
        }
        sonicSession = SonicEngine.getInstance().createSession(url,  new SonicSessionConfig.Builder().build());
        sonicSessionClient = new SonicSessionClientImpl();
        Log.d(TAG,"##toLoadUrl##url="+url+",sonicSession="+sonicSession+",sonicSessionClient="+sonicSessionClient);
        if (null != sonicSession&&null!=sonicSessionClient&&webView!=null) {
            sonicSession.bindClient(sonicSessionClient);
            sonicSessionClient.bindWebView(webView);
            sonicSessionClient.clientReady();
            Toast.makeText(MainActivity.this,"使用VasSonic加载H5页面,url="+url,Toast.LENGTH_LONG).show();
        } else {
            webView.loadUrl(url);
            Toast.makeText(MainActivity.this,"使用WebView加载H5页面,url="+url,Toast.LENGTH_LONG).show();
        }
    }
}