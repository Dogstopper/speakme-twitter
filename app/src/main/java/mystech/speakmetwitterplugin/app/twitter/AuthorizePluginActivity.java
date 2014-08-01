package mystech.speakmetwitterplugin.app.twitter;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import mystech.speakme.plugin.SpeakMePluginActivity;
import mystech.speakme.plugin.actions.ActionBase;
import mystech.speakme.plugin.actions.ActionHandlerInterfaces;
import mystech.speakme.plugin.actions.AsyncAction;
import twitter4j.auth.AccessToken;

public class AuthorizePluginActivity extends SpeakMePluginActivity {
    public static final String RESULT_SUCCESS = "success";
    public static final String PREFS_FILE = "PREFS";

    private Context mContext = this;
    private WebView mWebView;
    private Handler mHandler;   //Lets Async tasks post Runnables to the UI thread
    private ProgressDialog mPd;
    private TwitterHandler mTwitterHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Toast.makeText(this, "Activity Starting", Toast.LENGTH_LONG).show();
        mWebView = new WebView(this);
        setContentView(mWebView);

        // Initialize mHandler
        mHandler = new Handler();

        // Initialize the TwitterHandler and load authorization url in WebView
        SharedPreferences prefs = getSharedPreferences(PREFS_FILE, MODE_MULTI_PROCESS);
        mTwitterHandler = new TwitterHandler(prefs);
        loadAuthorizationUrlInWebView();
    }

    private void loadAuthorizationUrlInWebView() {
        class GetAuthUrlTask extends AsyncTask<String, Void, Boolean> {
            private Exception exception;

            protected Boolean doInBackground(String... strings) {
                try {
                    final String url = mTwitterHandler.getAuthorizationUrl();
                    Log.d("AUTHORIZE_ACTIVITY", "URL: " + url);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            /* An instance of this class will be registered as a JavaScript interface */
                            class PinGrabberJavaScriptInterface
                            {
                                @JavascriptInterface
                                public void showHTML(String html)
                                {
                                    //Check if we are on the page that has the pin. The pin is enclosed in the code tag.
                                    if(html.indexOf("<code>") != -1) {
                                        String pin = html.substring(html.indexOf("<code>") + 6, html.indexOf("</code>"));
                                        Log.d("AUTHORIZE_ACTIVITY", "Starting Pin");
                                        tryAuthenticateUser(pin);
                                    }
                                }
                            }

                            //Set the Javascript to Android call binder.
                            mWebView.getSettings().setJavaScriptEnabled(true);
                            mWebView.addJavascriptInterface(new PinGrabberJavaScriptInterface(), "HTMLOUT");
                            mWebView.setWebViewClient(new WebViewClient() {
                                @Override
                                public void onPageFinished(WebView view, String url)
                                {
                                    Log.d("AUTHORIZE_ACTIVITY", "onPageFinished()");

                                    /* This call inject JavaScript into the page which just finished loading. */
                                    mWebView.loadUrl("javascript:window.HTMLOUT.showHTML('<head>'+document.getElementsByTagName('html')[0].innerHTML+'</head>');");
                                }
                            });

                            Log.d("AUTHORIZE_ACTIVITY", "Loading URL: " + url);
                            //Load the URL in the WebView
                            mWebView.loadUrl(url);
                        }
                    });
                } catch (Exception e) {
                    this.exception = e;
                    Log.e("SPEAK ME", "ERROR: " + e);
                    return false;
                }
                return true;
            }
        }
        new GetAuthUrlTask().execute();
    }

    private void tryAuthenticateUser(final String pin) {
        class AuthTask extends AsyncTask<String, Void, Boolean> {
            private Exception exception;

            @Override
            protected void onPreExecute() {
                mPd = new ProgressDialog(mContext);
                mPd.setTitle("Authenticating");
                mPd.setMessage("Please wait...");
                mPd.setCancelable(false);
                mPd.setIndeterminate(true);
                mPd.show();
            }

            protected Boolean doInBackground(String... strings) {
                try {
                    final AccessToken accessToken = mTwitterHandler.getAccessTokenUsingPin(pin);
                    if(accessToken != null) {
                        //Set the new access token in mTwitterHandler
                        mTwitterHandler.authenticate(accessToken);

                        //Notify the activity that we have been authenticated
                        mHandler.post(new Runnable(){
                            @Override
                            public void run() {
                                onAuthenticationSuccessful(accessToken);
                            }
                        });
                    } else {
                        //Tell the user that authentication was not successful
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                onAuthenticationFailed();
                            }
                        });
                    }
                } catch (Exception e) {
                    this.exception = e;
                    Log.e("SPEAK ME", "ERROR: " + e);
                    return false;
                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (mPd!=null) {
                    mPd.dismiss();
                    mPd = null;
                }
            }
        }
        new AuthTask().execute();
    }

    private void onAuthenticationSuccessful(AccessToken accessToken) {
        // Save the access token in the preferences
        Log.d("AUTHORIZE_ACTIVITY", "PIN SUCCESS");
        SharedPreferences preferences = getSharedPreferences(PREFS_FILE, MODE_MULTI_PROCESS);
        preferences.edit().putString("token", accessToken.getToken())
                          .putString("tokenSecret", accessToken.getTokenSecret()).commit();
        try{
            // Populate the cache with id/screename_name pairs
            new AsyncTask<Object, Void, Boolean>() {

                @Override
                protected Boolean doInBackground(Object... voids) {
                    SharedPreferences prefs = getSharedPreferences(AuthorizePluginActivity.PREFS_FILE,MODE_MULTI_PROCESS);
                    Map<String, String> map = (Map<String,String>)prefs.getAll();
                    map.remove("token");
                    map.remove("tokenSecret");

                    Map<Long, Pair<String, String>> realMap = new HashMap<Long, Pair<String, String>>();
                    for (String id : map.keySet()) {
                        long l_id = Long.parseLong(id);
                        String pair = map.get(id);

                        String[] a_pair = pair.split(",");
                        Pair<String, String> m_pair = new Pair<String, String>(a_pair[0], a_pair[1]);
                        realMap.put(l_id, m_pair);
                    }
                    mTwitterHandler.setUserCache(realMap);
                    return true;
                }

                @Override
                protected void onPostExecute(Boolean result)
                {
                    if (result) {
                        queueSpeech("Login successful.");
                    }
                }
            }.execute().get();
        } catch(Exception e) {

        }

        queueSpeech("Loading Friends and Contacts.");
        AsyncAction loader = mTwitterHandler.getLoaderAction();
        loader.registerOnSuccessListener(new ActionHandlerInterfaces.IHandleActionSuccess() {
            @Override
            public void onActionSuccess(ActionBase actionBase) {
                // Return the result to the activity that created it (MainActivity)
                Intent returnIntent = new Intent();
                returnIntent.putExtra(AuthorizePluginActivity.RESULT_SUCCESS,true);
                setResult(RESULT_OK, returnIntent);

                finish();
            }
        });
        queueAction(loader, false);
    }

    private void onAuthenticationFailed() {
        // Return the result to the activity that created it (MainActivity)
        Intent returnIntent = new Intent();
        returnIntent.putExtra(AuthorizePluginActivity.RESULT_SUCCESS,false);
        setResult(RESULT_OK,returnIntent);

        queueSpeech("Login unsuccessful.");

        finish();
    }

    @Override
    protected void onInitialized() {

    }

    @Override
    protected void performAction(String s) {

    }
}
