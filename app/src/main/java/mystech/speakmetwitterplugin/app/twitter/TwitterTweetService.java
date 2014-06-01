package mystech.speakmetwitterplugin.app.twitter;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;

import mystech.speakme.plugin.app.SpeakMePluginService;

public class TwitterTweetService extends SpeakMePluginService {
    private TwitterHandler handler;

    public TwitterTweetService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Check to see if the user is logged in
        SharedPreferences prefs = getSharedPreferences(AuthorizePluginActivity.PREFS_FILE,MODE_MULTI_PROCESS);
        String token = prefs.getString("token", "");
        String tokenSecret = prefs.getString("tokenSecret", "");
        handler = new TwitterHandler(prefs, token, tokenSecret);
    }

    @Override
    public void performAction(String text) {
        text = text.replaceFirst("[T|t]witter", "");
        text = text.replaceFirst("[T|t]weet", "");
        text = text.trim();

        // Check that we're logged in.
        ResultCallback cb = new ResultCallback();
        handler.verifyCredentials(cb);
        while (!cb.invoked) {
            synchronized (this){
                try {
                    wait(200);
                } catch(InterruptedException e) {
                    speak("There was an error validating your credentials. Please login" +
                            "by saying, 'Twitter login' or 'Twitter settings'");
                    return;
                }
            }
        }
        if (!cb.result) {
            speak("There was an error validating your credentials. Please login" +
                    "by saying, 'Twitter login' or 'Twitter settings'");
            return;
        }

        boolean containsYes = false;
        String outputText = handler.twitterify(text);
        do {
            // If we are logged in, then we should tweet!

            speak("You said:");
            speak(outputText);
            String[] poss = queryUser("Do you want to post? Yes to post, no to re-record, quit to stop..",
                    "I did not understand. Please repeat Yes, No, or quit", true, false);


            for (String s : poss) {
                if (s.equalsIgnoreCase("yes")) {
                    containsYes = true;
                }
                if (s.equalsIgnoreCase("quit")) {
                    speak("Cancelling");
                    return;
                }
            }
            if (!containsYes) {
                String[] tweets = queryUser("Please repeat your tweet.",
                        "I'm sorry, there seems to be a problem recognizing your voice",
                        true, false);
                Log.d("Tweeter", "Possibilities: " + Arrays.toString(tweets));
                if (tweets.length > 0) {
                    outputText = handler.twitterify(tweets[0]);
                } else {
                    outputText = "";
                }
            }
        } while(!containsYes);


        cb = new ResultCallback();
        handler.tweet(outputText, -1, cb);

        Log.d("TWITTER", "SPOKEN: " + text);
        Log.d("TWITTER", "TWEETED: " + outputText);
        // Determine Success
        while (!cb.invoked) {
            synchronized (this){
                try {
                    wait(200);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (cb.result) {
            speak("Tweet success!");
        } else {
            speak("Tweet failed.");
        }
        Log.d("TWITTER", "DONE");

    }

    class ResultCallback implements BooleanCallback {
        public boolean invoked = false;
        public boolean result = true;

        @Override
        public void run(boolean bool) {
            invoked = true;
            if (!bool) {
                result = false;
            }
        }
    };


}
