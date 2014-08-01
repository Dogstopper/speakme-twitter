package mystech.speakmetwitterplugin.app.twitter;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import mystech.speakme.plugin.actions.ActionBase;
import mystech.speakme.plugin.actions.ActionHandlerInterfaces;
import mystech.speakme.plugin.actions.AsyncAction;
import twitter4j.IDs;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.api.FriendsFollowersResources;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterHandler {
    public static final String CONSUMER_KEY = "BHi8Bu5wU0lu6zp7FUsqA";
    public static final String CONSUMER_SECRET = "ydMyJFs1lFAXPLljodPmRUub6zZn7e6ZS5GpsuOXA";
    private SharedPreferences prefs = null;
    private HashMap<Long, Pair<String,String>> screenToUserMap = new HashMap<Long, Pair<String,String>>();

    public String m_consumerKey;
    public String m_consumerSecret;
    public String m_accessToken;
    public String m_accessSecret;
    public RequestToken m_requestToken;
    private Twitter m_twitter;

    private abstract class TwitterAction extends AsyncAction {

        @Override
        public void onActionStarted() {
            if (actionStartedHandler != null) {
                actionStartedHandler.onActionStarted(this);
            }
        }

        @Override
        public void onActionComplete() {
            if (actionCompleteHandler != null) {
                actionCompleteHandler.onActionComplete(this);
            }
        }

        @Override
        public void onActionError(Exception e) {
            if (actionErrorHandler != null) {
                actionErrorHandler.onActionError(this,e);
            }
        }

        @Override
        public void onActionSuccess() {
            if (actionSuccessHandler != null) {
                actionSuccessHandler.onActionSuccess(this);
            }
        }
    }

    TwitterHandler(SharedPreferences prefs) {
        m_consumerKey = CONSUMER_KEY;
        m_consumerSecret = CONSUMER_SECRET;
        m_accessToken = "";
        m_accessSecret = "";

        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthConsumerKey(m_consumerKey)
                .setOAuthConsumerSecret(m_consumerSecret);

        TwitterFactory tf = new TwitterFactory(cb.build());
        m_twitter  = tf.getInstance();

        this.prefs = prefs;
    }

    TwitterHandler(SharedPreferences prefs, String accessToken, String accessSecret) {
        m_consumerKey = CONSUMER_KEY;
        m_consumerSecret = CONSUMER_SECRET;

        authenticate(accessToken, accessSecret);
        this.prefs = prefs;

        Map<String, String> storedNames = (Map<String, String>)prefs.getAll();
        storedNames.remove("token");
        storedNames.remove("tokenSecret");

        Map<Long, Pair<String,String>> parsedNames = new HashMap<Long, Pair<String, String>>();
        for (String id : storedNames.keySet()) {
            String[] names = storedNames.get(id).split(",");
            Pair<String, String> namesPair = new Pair<String, String>(names[0], names[1]);
            parsedNames.put(Long.parseLong(id), namesPair);
        }
        setUserCache(parsedNames);
        Log.d("TWITTER", "# of loaded names: " + parsedNames.size());


        // Allows it to load in the background and still allow the other
        // AsyncTasks to run. In Honeycomb, they are run serially, rather
        // than in parallel.
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//            loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//        } else {
//            loader.execute();
//        }
     }

    public void setUserCache(Map<Long,Pair<String,String>> map) {
        screenToUserMap.putAll(map);
    }

    public void authenticate(String token, String secret) {
        m_accessToken = token;
        m_accessSecret = secret;

        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setOAuthConsumerKey(m_consumerKey)
                .setOAuthConsumerSecret(m_consumerSecret)
                .setOAuthAccessToken(m_accessToken)
                .setOAuthAccessTokenSecret(m_accessSecret);

        TwitterFactory tf = new TwitterFactory(cb.build());
        m_twitter  = tf.getInstance();
    }

    public void authenticate(AccessToken at) {
        authenticate(at.getToken(), at.getTokenSecret());
    }

    public String getAuthorizationUrl() {
        String url = "";
        try {
            // get request token.
            // this will throw IllegalStateException if access token is already available
            m_requestToken = m_twitter.getOAuthRequestToken();
            url = m_requestToken.getAuthorizationURL();
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get timeline: " + te.getMessage());
            System.exit(-1);
        }

        return url;
    }

    //Returns null if user did not authorize app or entered incorrect pin
    public AccessToken getAccessTokenUsingPin(String pin) {
        AccessToken accessToken = null;

        try {
            if (pin.length() > 0) {
                accessToken = m_twitter.getOAuthAccessToken(m_requestToken, pin);
            }
        } catch (TwitterException te) {
            if (401 == te.getStatusCode()) {
                Log.d("TwitterHandler", "Unable to get the access token.");
            } else {
                //ERROR
            }

            return null;
        }

        return accessToken;
    }

    //The callback is called when the tweet is complete. It passes true on success, false otherwise
    public TwitterAction tweet(final String text, final long inReplyTo) {
        class TweetTask extends TwitterAction {
            public void executeAction() {
                try {
                    StatusUpdate st = new StatusUpdate(text);
                    if (inReplyTo != -1) {
                        st.inReplyToStatusId(inReplyTo);
                    }
                    twitter4j.Status status = m_twitter.updateStatus(st);
                    onActionComplete();
                } catch (TwitterException e) {
                    Log.e("LazyTweeter", "ERROR: " + e);
                    onActionError(e);
                }
            }
        }

        return new TweetTask();
    }

    private ResponseList<Status> cache = null;
    public String getFeedTweet(int tweetNum) {
        final int cacheSize = 120;
        Log.d("TWITTER", "Getting tweet #:" + tweetNum);
        int page = tweetNum/cacheSize+1;
        int tweet = tweetNum%cacheSize;

        if (cache == null) {
            try {
                cache = m_twitter.getHomeTimeline(new Paging(page,cacheSize));
            } catch (TwitterException e) {
                e.printStackTrace();
            }
        }

        if (tweetNum >= cache.size()) {
            long id = cache.get(cache.size()-1).getId();
            try {
                cache = m_twitter.getHomeTimeline(new Paging(   id));
            } catch (TwitterException e) {
                e.printStackTrace();
            }
        }

        Status s = cache.get(tweet);
        if (s.isRetweet()) {
            return s.getUser().getName() + " retweeted " + s.getText().replaceAll("RT", "");
        }
        else {
            return s.getUser().getName() + " said " + s.getText();
        }
    }

    public long getReplyId(int tweetNum) {
        if (cache != null) {
            if (tweetNum < cache.size()) {
                long id = cache.get(tweetNum).getUser().getId();
                return id;
            }
        }
        return -1;
    }

    public boolean retweet(int tweetNum) {
        if (cache != null) {
            if (tweetNum < cache.size()) {
                try {
                    m_twitter.retweetStatus(cache.get(tweetNum).getId());
                    return true;
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public boolean reply(int tweetNum, String text) {
        if (cache != null) {
            if (tweetNum < cache.size()) {
                try {
                    Status replyTo = cache.get(tweetNum);
                    String screenName = replyTo.getUser().getScreenName();

                    StatusUpdate st = new StatusUpdate(screenName + ": " + text);
                    st.inReplyToStatusId(replyTo.getId());
                    m_twitter.updateStatus(st);
                    return true;
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    //The callback is run and passed the result when verification is complete
    public AsyncAction verifyCredentials() {
        class VerifyAction extends TwitterAction
        {
            @Override
            public void executeAction() {
                try {
                    m_twitter.verifyCredentials(); //This throws an exception when verification fails
                    onActionSuccess();
                } catch(TwitterException e) {
                    if(e.getErrorCode() != 215) //215 is the error code for the access token is invalid
                        Log.e("LazyTweeter", "ERROR: " + e);
                    onActionError(e);
                }
            }
        }
        return new VerifyAction();
    }

    // O(n) or a call to the web.
    public String getUserFromScreenName(String screenName) throws TwitterException {
        for (Pair<String,String> pair: screenToUserMap.values()) {
            if (pair.first.equalsIgnoreCase(screenName)) {
                return pair.second;
            }
        }

        User user = m_twitter.showUser(screenName);
        Pair<String,String> userPair = new Pair<String, String>(user.getScreenName(), user.getName());
        screenToUserMap.put(user.getId(), userPair);
        prefs.edit().putString(user.getId() +"", user.getScreenName()+","+user.getName()).commit();
        return user.getName();
    }

    // O(1) or a call to the web
    public String getUsernameFromID(long id) throws  TwitterException{
        if (screenToUserMap.containsKey(id)) {
            return screenToUserMap.get(id).first;
        }
        else {
            User user = m_twitter.showUser(id);
            Pair<String,String> userPair = new Pair<String, String>(user.getScreenName(), user.getName());
            screenToUserMap.put(user.getId(), userPair);
            prefs.edit().putString(id +"", user.getScreenName()+","+user.getName()).commit();
            return user.getScreenName();
        }
    }

    // O(1) or a call to the web
    public String getNameFromID(long id) throws TwitterException {
        if (screenToUserMap.containsKey(id)) {
            return screenToUserMap.get(id).second;
        }
        else {
            User user = m_twitter.showUser(id);
            Pair<String,String> userPair = new Pair<String, String>(user.getScreenName(), user.getName());
            screenToUserMap.put(user.getId(), userPair);
            prefs.edit().putString(id +"", user.getScreenName()+","+user.getName()).commit();
            return user.getName();
        }
    }

    public void favoritePost(int tweetNum) throws TwitterException {
        long tweetId = cache.get(tweetNum).getId();
        m_twitter.createFavorite(tweetId);
    }


//    AsyncTask loader = new AsyncTask<Object, Integer, Void>(){
    public AsyncAction getLoaderAction() {
        final HashMap<Long, Pair<String, String>> copyScreenToUserMap =
                (HashMap<Long, Pair<String,String>>) screenToUserMap.clone();

        final TwitterAction loader = new TwitterAction() {
            @Override
            public void executeAction() {
                FriendsFollowersResources ffRes = m_twitter.friendsFollowers();
                ArrayList<Long> ids = new ArrayList();
                try {
                    IDs tempIDs = ffRes.getFollowersIDs(-1);

                    Log.d("TWITTERIFY", Arrays.toString(tempIDs.getIDs()));

                    long[] longArray = tempIDs.getIDs();
                    for (long l : longArray) {
                        if (!copyScreenToUserMap.containsKey(l)) {
                            ids.add(l);
                        }
                    }
                    while (tempIDs.hasNext()) {
                        longArray = tempIDs.getIDs();
                        for (long l : longArray) {
                            if (!copyScreenToUserMap.containsKey(l)) {
                                ids.add(l);
                            }
                        }
                        tempIDs = ffRes.getFollowersIDs(tempIDs.getNextCursor());
                    }
                    tempIDs = ffRes.getFriendsIDs(-1);
                    longArray = tempIDs.getIDs();
                    for (long l : longArray) {
                        if (!copyScreenToUserMap.containsKey(l)) {
                            ids.add(l);
                        }
                    }
                    while (tempIDs.hasNext()) {
                        longArray = tempIDs.getIDs();
                        for (long l : longArray) {
                            if (!copyScreenToUserMap.containsKey(l)) {
                                ids.add(l);
                            }
                        }
                        tempIDs = ffRes.getFriendsIDs(tempIDs.getNextCursor());
                    }
                    for (long id : ids) {
                        if (!copyScreenToUserMap.containsKey(id)) {
                            String screenName = getUsernameFromID(id);
                            String name = getNameFromID(id);
                            Pair<String, String> map = new Pair<String, String>(screenName, name);
                            copyScreenToUserMap.put(id, map);
                        }

                    }
                    Log.d("TWITTER", "# of discovered names: " + ids.size());

                } catch (TwitterException e) {
                    Log.d("TWITTERIFY", e.getMessage());
                }
            }
        };
        loader.registerOnCompleteListener(new ActionHandlerInterfaces.IHandleActionComplete() {
            @Override
            public void onActionComplete(ActionBase actionBase) {
                screenToUserMap.putAll(copyScreenToUserMap);
                for (long id : copyScreenToUserMap.keySet()) {
                    String screenName = copyScreenToUserMap.get(id).first;
                    String name = copyScreenToUserMap.get(id).second;
                    prefs.edit().putString(id +"", screenName+","+name).commit();
                }
                Log.d("TWITTER", "# of discovered names: " + copyScreenToUserMap.keySet().size());
                loader.onActionSuccess();
            }
        });
        return loader;
    }


    // --------- String manipulation -------
    public String twitterify(String text) {

        int nextIndex = 0;
        do {
            // Replace hashtags appropriately.
            final String[] hashtagStrings = new String[]{"hashtag", "has tag", "hash tag"};
            List<Integer> discoveries = new LinkedList<Integer>();
            for (String poss: hashtagStrings) {
                discoveries.add(0,text.toLowerCase().indexOf(poss,nextIndex));
            }
            int hashtagIndex = findNonNegativeMin(discoveries);

            discoveries.clear();
            discoveries = new LinkedList<Integer>();
            for (String poss: hashtagStrings) {
                discoveries.add(0, text.toLowerCase().indexOf(poss, hashtagIndex+1));
            }
            nextIndex = findNonNegativeMin(discoveries);

            Log.d("TWITTER TWEET", "text: " + text);
            Log.d("TWITTER TWEET", "Hashtag index: " + hashtagIndex);
            Log.d("TWITTER TWEET", "NExtIndex:" + nextIndex);
            if (hashtagIndex != -1 && nextIndex != -1) {
                String fixed = fixHashtags(text.substring(hashtagIndex, nextIndex));
                text = text.substring(0,hashtagIndex) + fixed +
                        text.substring(nextIndex).replaceAll("[H|h]ash[\\s]*tag","");
            }
        } while (nextIndex != -1);

        Log.d("TWITTERIFY", screenToUserMap.entrySet().toString());
        for (long id : screenToUserMap.keySet()) {
            String screenName = screenToUserMap.get(id).first;
            String name = screenToUserMap.get(id).second.toLowerCase();
            String firstChar = name.charAt(0) + "";

            Log.d("TWITTERIFY", "Testing name: " + name);
            String[] nameParts = name.split(" ");
            String firstName = nameParts[0];
            String lastName = nameParts[nameParts.length - 1];

            Log.d("TWITTERIFY", "lastName: " + lastName + "\tfirstName: " + firstName);

            if (nameParts.length > 1) {
                if (text.toLowerCase().contains(firstName.replaceAll("[^a-z0-9]", "")) &&
                    text.toLowerCase().contains(lastName.replaceAll("[^a-z0-9]", ""))) {
                    int firstNameIndex = text.indexOf(firstName);
                    int lastNameIndex = text.indexOf(lastName);
                    if ((lastNameIndex - firstNameIndex) <= name.length()) {
                        text = text.toLowerCase().replaceAll(firstName + ".*" + lastName,
                                "@" + screenName);

                    }
                }
            }

            if (nameParts.length == 1) {
                if (text.toLowerCase().contains(firstName.replaceAll("[^a-z0-9]", ""))) {
                    text = text.toLowerCase().replaceAll(firstName,
                            "@" + screenName);
                }
            }
        }

        if (text.length() > 1) {
            String fChar = text.charAt(0) + "";
            String rest = text.substring(1);
            String part = fChar.toUpperCase() + rest;
            text = part;
        } else {
            text = text.toUpperCase();
        }

        return text;

    }

    private int findNonNegativeMin(List<Integer> list)
    {
        Log.d("HASHTAGIFY", Arrays.toString(list.toArray()));
        int min = Integer.MAX_VALUE;
        for (int item : list) {
            if (item < min && item >= 0) {
                min = item;
            }
        }
        if (min == Integer.MAX_VALUE)
            min = -1;
        return min;
    }

    private String fixHashtags(String hashtagString) {
        hashtagString = hashtagString.replaceAll("hashtag", "#");
        hashtagString = hashtagString.replaceAll("hash tag", "#");
        String[] parts = hashtagString.split(" ");
        StringBuffer sb = new StringBuffer();
        for (String s : parts) {
            if (s.length() > 1) {
                String firstChar = s.charAt(0) + "";
                String rest = s.substring(1);
                String part = firstChar.toUpperCase() + rest;
                sb.append(part);
            } else {
                sb.append(s.toUpperCase());
            }
        }
        return sb.toString();
    }
}