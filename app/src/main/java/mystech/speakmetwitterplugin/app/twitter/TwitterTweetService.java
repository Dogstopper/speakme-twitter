package mystech.speakmetwitterplugin.app.twitter;

import android.app.Notification;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;

import mystech.speakme.plugin.SpeakMePluginService;
import mystech.speakme.plugin.actions.ActionBase;
import mystech.speakme.plugin.actions.ActionHandlerInterfaces;
import mystech.speakme.plugin.actions.AsyncAction;
import mystech.speakme.plugin.utils.UtilInterfaces;

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
    public void performAction(final String text) {
        // Check that we're logged in.
        AsyncAction verifyAction = handler.verifyCredentials();
        verifyAction.registerOnErrorListener(new ActionHandlerInterfaces.IHandleActionError() {
            @Override
            public void onActionError(ActionBase action, Exception e) {
                queueSpeech("There was an error validating your credentials. Please login" +
                        "by saying, 'Twitter login' or 'Twitter settings'");
                stopSelf();
            }
        });
        verifyAction.registerOnSuccessListener(new ActionHandlerInterfaces.IHandleActionSuccess() {
            @Override
            public void onActionSuccess(ActionBase action) {
                String newText = text.replaceFirst("[T|t]witter", "");
                newText = newText.replaceFirst("[T|t]weet", "");
                newText = newText.trim();

                // Handle Speech inputs
                String outputText = handler.twitterify(newText);
                handleSpeechInput(outputText);
            }
        });
        queueAction(verifyAction, true);
    }

    private void handleSpeechInput(final String outputText)
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
                                    handleSpeechInput(strings[0]);
                                }
                            }
                    );
                }
                else {
                    performTweet(new String[]{outputText});
                }

            }
        };
        queryUser("Do you want to post? Yes to post, no to re-record, quit to stop..",
            "I did not understand. Please repeat Yes, No, or quit", true, false,
            null, verifyAction
        );



    }

    private void performTweet(String[] tweets) {
        Log.d("Tweeter", "Possibilities: " + Arrays.toString(tweets));
        if (tweets.length > 0) {
            ActionBase action = handler.tweet(handler.twitterify(tweets[0]), -1);
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

        } else {
            handler.tweet("", -1);
        }
    }

    @Override
    protected void onInitialized() {

    }

}
