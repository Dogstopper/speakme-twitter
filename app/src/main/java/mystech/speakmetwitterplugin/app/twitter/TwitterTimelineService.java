package mystech.speakmetwitterplugin.app.twitter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;

import mystech.speakme.plugin.SpeakMePluginService;
import mystech.speakme.plugin.actions.ActionBase;
import mystech.speakme.plugin.actions.ActionHandlerInterfaces;
import mystech.speakme.plugin.actions.AsyncAction;
import mystech.speakme.plugin.utils.UtilInterfaces;
import twitter4j.TwitterException;

/**
 * Created by stephen on 3/27/14.
 */
public class TwitterTimelineService extends SpeakMePluginService implements ActionHandlerInterfaces.IHandleActionSuccess {

    private TwitterHandler handler;

    private AsyncAction verifyAction;
    private int tweetNum = 0;

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
        verifyAction = handler.verifyCredentials();
        verifyAction.registerOnErrorListener(new ActionHandlerInterfaces.IHandleActionError() {
            @Override
            public void onActionError(ActionBase action, Exception e) {
                queueSpeech("There was an error validating your credentials. Please login" +
                        "by saying, 'Twitter login' or 'Twitter settings'");
                stopSelf();
            }
        });
        verifyAction.registerOnSuccessListener(this);
        queueAction(verifyAction,true);
    }

    @Override
    public void onActionSuccess(ActionBase actionBase) {
        if (actionBase.getID() == verifyAction.getID()) {
            queueSpeech("Reading your feed:");
            tweetNum = 0;

            speakNextTweet();
        }
    }

    private void speakNextTweet() {
        queueSpeech(convertTweetToText(handler.getFeedTweet(tweetNum), tweetNum));
        queryUser(null, new UtilInterfaces.IHandleSpeechResults() {
            @Override
            public void resultsAvailable(String[] strings) {
                handleMenu(strings);
            }
        });
        tweetNum++;
    }

    public void handleMenu(String[] results) {
        // String[] results = queryUser(null, "", false, false);
        if (results != null) {
            for (String res : results) {
                if (res.toLowerCase().contains("stop") ||
                        res.toLowerCase().contains("quit")) {
                    queueSpeech("Goodbye.", true, new ActionHandlerInterfaces.IHandleActionSuccess() {
                        @Override
                        public void onActionSuccess(ActionBase actionBase) {
                            shutdown();
                        }
                    });
                    break;
                } else if (res.toLowerCase().contains("next")) {
                    speakNextTweet();
                    break;
                }
                if (res.toLowerCase().contains("favorite")) {
                    try {
                        handler.favoritePost(tweetNum);
                        queueSpeech("Favorite Successful");
                        speakNextTweet();
                    } catch (TwitterException e) {
                        queueSpeech("There was an error. Goodbye", true, new ActionHandlerInterfaces.IHandleActionSuccess() {
                            @Override
                            public void onActionSuccess(ActionBase actionBase) {
                                shutdown();
                            }
                        });
                    }
                }
                if (res.toLowerCase().contains("re tweet") ||
                        res.toLowerCase().contains("retweet")) {
                    if (handler.retweet(tweetNum)) {
                        queueSpeech("Retweet successful");
                        speakNextTweet();
                    }

                }
                if (res.toLowerCase().contains("reply")) {
                    queryUser("Please state your reply.",
                            "I did not understand your query. Please try again.",
                            true, false, null, new UtilInterfaces.IHandleSpeechResults() {
                                @Override
                                public void resultsAvailable(String[] strings) {
                                    postReply(strings[0], handler.getReplyId(tweetNum));
                                    speakNextTweet();
                                }
                            });
                    break;
                }
            }
        }

    }

    @Override
    protected void onInitialized() {

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

    private void postReply(final String outputText, final long replyId)
    {
        // If we are logged in, then we should tweet!
        queueSpeech("You said:");
        queueSpeech(outputText);

        // This handler responds to queries approriately.
        final UtilInterfaces.IHandleSpeechResults verifyAction = new UtilInterfaces.IHandleSpeechResults() {
            @Override
            public void resultsAvailable(String[] strings) {
                boolean containsYes = false;
                boolean containsQuit = false;
                for (String s : strings) {
                    if (s.equalsIgnoreCase("yes")) {
                        containsYes = true;
                    } else if (s.equalsIgnoreCase("quit")) {
                        containsQuit = true;
                    }
                }
                if (containsQuit) {
                    queueSpeech("Cancelling", true, new ActionHandlerInterfaces.IHandleActionSuccess() {
                        @Override
                        public void onActionSuccess(ActionBase action) {
                            Log.d("TWITTER", "DONE");
                            shutdown();
                        }
                    });
                }
                else if (!containsYes) {
                    queryUser("Please repeat your tweet.",
                            "I'm sorry, there seems to be a problem recognizing your voice",
                            true, false, null, new UtilInterfaces.IHandleSpeechResults() {
                                @Override
                                public void resultsAvailable(String[] strings) {
                                    postReply(strings[0], replyId);
                                }
                            }
                    );
                }
                else {
                    performReply(outputText, replyId);
                }

            }
        };
        queryUser("Do you want to post? Yes to post, no to re-record, quit to stop..",
                "I did not understand. Please repeat Yes, No, or quit", true, false,
                null, verifyAction
        );



    }

    private void performReply(String reply, long replyId) {
        ActionBase action = handler.tweet(handler.twitterify(reply), replyId);
        action.registerOnSuccessListener(new ActionHandlerInterfaces.IHandleActionSuccess() {
            @Override
            public void onActionSuccess(ActionBase action) {
                queueSpeech("Tweet Success", true, new ActionHandlerInterfaces.IHandleActionSuccess() {
                    @Override
                    public void onActionSuccess(ActionBase action) {
                        Log.d("TWITTER", "DONE");
                        shutdown();
                    }
                });
            }
        });
        action.registerOnErrorListener(new ActionHandlerInterfaces.IHandleActionError() {
            @Override
            public void onActionError(ActionBase action, Exception e) {
                queueSpeech("Tweet failed - More than 140 character spoken. Try again." , true, new ActionHandlerInterfaces.IHandleActionSuccess() {
                    @Override
                    public void onActionSuccess(ActionBase action) {
                        Log.d("TWITTER", "DONE");
                        shutdown();
                    }
                });
            }
        });
        queueAction(action, true);
    }

}
