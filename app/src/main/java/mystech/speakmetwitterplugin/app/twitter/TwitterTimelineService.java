package mystech.speakmetwitterplugin.app.twitter;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;

import mystech.speakme.plugin.app.SpeakMePluginService;
import twitter4j.TwitterException;

/**
 * Created by stephen on 3/27/14.
 */
public class TwitterTimelineService extends SpeakMePluginService {

    private TwitterHandler handler;

    @Override
    public void onCreate() {
        super.onCreate();

        // Check to see if the user is logged in
        SharedPreferences prefs = getSharedPreferences(AuthorizePluginActivity.PREFS_FILE, MODE_MULTI_PROCESS);
        String token = prefs.getString("token", "");
        String tokenSecret = prefs.getString("tokenSecret", "");
        handler = new TwitterHandler(prefs, token, tokenSecret);
    }

    @Override
    public void performAction(String text) {
        if (checkLoggedIn()) {
            speak("Reading your feed:");
            int tweetNum = 0;
            boolean quit = false;
            while (!quit) {
                speak(convertTweetToText(handler.getFeedTweet(tweetNum), tweetNum));
                String[] results = queryUser(null, "", false, false);
                if (results != null) {
                    for (String res : results) {
                        if (res.toLowerCase().contains("stop") ||
                                res.toLowerCase().contains("quit")) {
                            quit = true;
                            speak("Goodbye.");
                            break;
                        } else if (res.toLowerCase().contains("next")) {
                            quit = false;
                            continue;
                        }
                        if (res.toLowerCase().contains("favorite")) {
                            try {
                                handler.favoritePost(tweetNum);
                                speak("Favorite Successful");
                            } catch (TwitterException e) {
                            }
                        }
                        if (res.toLowerCase().contains("re tweet") ||
                                res.toLowerCase().contains("retweet")) {
                            if (handler.retweet(tweetNum)) {
                                speak("Retweet successful");
                            }

                        }
                        if (res.toLowerCase().contains("reply")) {
                            postReply(queryUser("Please state your reply.",
                                    "I did not understand your query. Please try again.",
                                    true, false)[0], handler.getReplyId(tweetNum));

                            // DO NOT REMOVE. EVEN THOUGH YOU DO NOT SEE A LOOP,
                            // THIS SOMETIMES LOOPS UNEXPECTEDLY.
                            // Somehow, the break stops it from occurring.
                            break;
                        }
                    }
                }
                tweetNum++;
            }
        }
    }

    private String convertTweetToText(String text, int tweetNum) {
        Log.d("TWITTER", "original text: " + text);
        text = text.replaceAll("http(s)?://[a-zA-Z0-9./\\-]*", ". hyperlink.");
        text = text.replaceAll("#", " hashtag ");
//        text = text.replaceAll("@", " at ");

        text = text.replaceAll("@\\s", " at ");
        while (text.contains("@")) {
            int locStart = text.indexOf("@");
            int nextSpace = text.indexOf(" ", locStart);
            if (locStart != -1) {
                if (nextSpace == -1) {
                    nextSpace = text.length();
                }

                String screenname = text.substring(locStart, nextSpace);
                screenname = screenname.replaceAll("@", "");
                char last = screenname.charAt(screenname.length() - 1);
                if (!Character.isLetterOrDigit(last)) {
                    screenname = screenname.substring(0, screenname.length() - 1);
                }
                if (screenname.endsWith("'s")) {
                    screenname = screenname.substring(0, screenname.length() - 2);
                }
                Log.d("TWITTER", "screenName = " + screenname);
                String name = screenname;
                try {
                    name = handler.getUserFromScreenName(screenname);
                } catch (TwitterException e) {
                    e.printStackTrace();
                    break;
                } finally {
                    Log.d("TWITTER", "Name = " + name);
                    text = text.replaceAll("@" + screenname, name);
                }
            }
        }
        return text;
    }

    private boolean checkLoggedIn() {
        // Check that we're logged in.
        ResultCallback cb = new ResultCallback();
        handler.verifyCredentials(cb);
        while (!cb.invoked) {
            synchronized (this) {
                try {
                    wait(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!cb.result) {
            speak("There was an error validating your credentials. Please login" +
                    "by saying, 'Twitter settings'");
            return false;
        }
        return true;
    }

    private void postReply(String text, long inReplyTo) {
        boolean containsYes = false;


        String outputText = handler.twitterify(text);
        do {
            // If we are logged in, then we should tweet!

            speak("You said:");
            speak(outputText);
            String[] poss = queryUser("Sound good? Yes to post, no to change.",
                    "Oops, I didn't catch that. Please repeat Yes, No, or quit", true, false);


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
                        "I'm sorry, I did not catch that.",
                        true, false);
                Log.d("Tweeter", "Possibilities: " + Arrays.toString(tweets));
                if (tweets.length > 0) {
                    outputText = handler.twitterify(tweets[0]);
                } else {
                    outputText = "";
                }
            }
        } while (!containsYes);

        try {
            String username = handler.getUsernameFromID(inReplyTo);
            outputText = "@" + username + ": " + outputText;
        } catch (TwitterException e) {
            speak("Posted but not replied");
        }

        ResultCallback cb = new ResultCallback();
        handler.tweet(outputText, inReplyTo, cb);
        return;
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
    }

    ;

}
