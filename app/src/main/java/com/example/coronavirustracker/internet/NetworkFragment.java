package com.example.coronavirustracker.internet;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.coronavirustracker.CoronaHandler;
import com.example.coronavirustracker.Exposure;
import com.example.coronavirustracker.MainActivity;
import com.example.coronavirustracker.database.ContactTrace;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class NetworkFragment extends Fragment {
    public static final String TAG = "NetworkFragment";

    private static final String URL_KEY = "UrlKey";

    private DownloadCallback<String> callback;
    private DownloadTask downloadTask;
    private String urlString;

    /**
     * Static initializer for NetworkFragment that sets the URL of the host it will be downloading
     * from.
     */
    public static NetworkFragment getInstance(FragmentManager fragmentManager, String url) {
        NetworkFragment networkFragment = (NetworkFragment) fragmentManager
                .findFragmentByTag(NetworkFragment.TAG);
        if (networkFragment == null) {
            networkFragment = new NetworkFragment();
            Bundle args = new Bundle();
            args.putString(URL_KEY, url);
            networkFragment.setArguments(args);
            fragmentManager.beginTransaction().add(networkFragment, TAG).commit();
        }
        return networkFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        setRetainInstance(true);
        super.onCreate(savedInstanceState);
        urlString = getArguments().getString(URL_KEY);
        //TODO: add more?
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Host Activity will handle callbacks from task.
        callback = (DownloadCallback<String>) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Clear reference to host Activity to avoid memory leak.
        callback = null;
    }

    @Override
    public void onDestroy() {
        // Cancel task when Fragment is destroyed.
        cancelDownload();
        super.onDestroy();
    }

    /**
     * Start non-blocking execution of DownloadTask.
     */
    public void startDownload(String URLparams, String requestType) {
        cancelDownload();
        downloadTask = new DownloadTask(callback);
        downloadTask.execute(urlString, URLparams, requestType);
    }

    /**
     * Cancel (and interrupt if necessary) any ongoing DownloadTask execution.
     */
    public void cancelDownload() {
        if (downloadTask != null) {
            downloadTask.cancel(true);
        }
    }





    /**
     * Implementation of AsyncTask designed to fetch data from the network.
     */
    private static class DownloadTask extends AsyncTask<String, Integer, DownloadTask.Result> {


        private DownloadCallback<String> callback;

        DownloadTask(DownloadCallback<String> callback) {
            setCallback(callback);
        }

        void setCallback(DownloadCallback<String> callback) {
            this.callback = callback;
        }

        /**
         * Wrapper class that serves as a union of a result value and an exception. When the download
         * task has completed, either the result value or exception can be a non-null value.
         * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
         */
        static class Result {
            public String resultValue;
            public Exception exception;
            public Result(String resultValue) {
                this.resultValue = resultValue;
            }
            public Result(Exception exception) {
                this.exception = exception;
            }
        }

        /**
         * Cancel background network operation if we do not have network connectivity.
         */
        @Override
        protected void onPreExecute() {
            if (callback != null) {
                NetworkInfo networkInfo = callback.getActiveNetworkInfo();
                if (networkInfo == null || !networkInfo.isConnected() ||
                        (networkInfo.getType() != ConnectivityManager.TYPE_WIFI
                                && networkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                    // If no connectivity, cancel task and update Callback with null data.
                    callback.updateFromDownload(null);
                    cancel(true);
                }
            }
        }

        /**
         * Defines work to perform on the background thread.
         */
        @Override
        protected DownloadTask.Result doInBackground(String... fnInp) {
            Result result = null;
            if (!isCancelled() && fnInp != null && fnInp.length > 0) {
                String urlString = fnInp[0];
                String URLparams = fnInp[1];
                String requestType = fnInp[2];
                try {
                    URL url = new URL(urlString);
                    String resultString = downloadUrl(url, URLparams, requestType);
                    if (resultString != null) {
                        result = new Result(resultString);
                    } else {
                        throw new IOException("No response received.");
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    result = new Result(e);
                }
            }
            return result;
        }

        /**
         * Updates the DownloadCallback with the result.
         */
        @Override
        protected void onPostExecute(Result result) {
            if (result != null && callback != null) {
                if (result.exception != null) {
                    callback.updateFromDownload(result.exception.getMessage());
                } else if (result.resultValue != null) {
                    callback.updateFromDownload(result.resultValue);
                }
                callback.finishDownloading();
            }
        }

        /**
         * Override to add special behavior for cancelled AsyncTask.
         */
        @Override
        protected void onCancelled(Result result) {

        }


        /**
         * Given a URL, sets up a connection and gets the HTTP response body from the server.
         * If the network request is successful, it returns the response body in String form. Otherwise,
         * it will throw an IOException.
         */
        private String downloadUrl(URL url, String URLparams, String requestType) throws IOException {
            //Passing urlParameters will make the connection run through POST and send them. Otherwise, connection ru
            InputStream stream = null;
            HttpURLConnection connection = null; // TODO: use Https
            String result = null;
            try {
                connection = (HttpURLConnection) url.openConnection(); // TODO: use https
                // Timeout for reading InputStream arbitrarily set to 3000ms.
                connection.setReadTimeout(3000);
                // Timeout for connection.connect() arbitrarily set to 3000ms.
                connection.setConnectTimeout(3000);
                connection.setRequestMethod(requestType);
                connection.setDoInput(true);
                // Add urlParamaters to the request
                if(!URLparams.equals("")) {
                    byte[] postData = URLparams.getBytes(StandardCharsets.UTF_8);
                    int postDataLength = postData.length;
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    connection.setRequestProperty("charset", "utf-8");
                    connection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
                    try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                        wr.write(postData);
                    }catch (Exception e){
                        e.printStackTrace();
                        throw e;
                    }
                }

                // Open communications link (network traffic occurs here).
                connection.connect();
                publishProgress(DownloadCallback.Progress.CONNECT_SUCCESS);
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpsURLConnection.HTTP_OK) {
                    throw new IOException("HTTP error code: " + responseCode);
                }
                // Retrieve the response body as an InputStream.
                stream = connection.getInputStream();
                publishProgress(DownloadCallback.Progress.GET_INPUT_STREAM_SUCCESS, 0);
                if(requestType == DownloadCallback.RequestTypes.GET) {
                    if (stream != null) {
                        // Converts Stream to String with max length of 500.
                        result = readStream(stream);
                    }
                }else{
                    result = "Successfully updated website";
                }
        }catch (Exception e){
            e.printStackTrace();
        } finally {
                // Close Stream and disconnect HTTPS connection.
                if (stream != null) {
                    stream.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return result;
        }

        private String readStream(InputStream stream)
                throws IOException {
            Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            int currentChar;
            StringBuilder messageBuilder = new StringBuilder();
            while(true) {//repeats until reaching currentChar = -1, which indicates the end of the file
                StringBuilder signatureStringBuilder = new StringBuilder(); // builds the sugnature
                do{
                    currentChar = reader.read();
                    if (currentChar == (int)','){
                        throw new IOException("String received from server was not properly formatted. Unexpected ','");
                    }
                    if((currentChar != (int) ' ') && (currentChar != (int) '.') && (currentChar != -1)){ //if the current char is a space, it's a php problem so we exclude it. if it's a ., that indicates the end of the section, so we don't use it.
                        signatureStringBuilder.append((char) currentChar);
                    }
                }while ((currentChar != (int) '.') && (currentChar != -1)); // when there is a '.', that indicates the end of the signature, so we move on. When there is a -1, that means the file is over.

                if(currentChar == -1){break;}// -1 indicates the end of the file
                String signatureString = signatureStringBuilder.toString();
                byte[] signature = Base64.decode(signatureString, android.util.Base64.DEFAULT);

                StringBuilder timeStampStringBuilder = new StringBuilder();
                do {
                    currentChar = reader.read();
                    if (currentChar == (int)'.'){
                        throw new IOException("String received from server was not properly formatted. Unexpected '.'");
                    }
                    if((currentChar != (int) ' ') && (currentChar != (int) ','  && (currentChar != -1))){ //if the current char is a space, it's a php problem so we exclude it. if it's a ., that indicates the end of the section, so we don't use it.
                        timeStampStringBuilder.append((char) currentChar);
                    }
                }while ((currentChar != (int) ',') && (currentChar != -1) );
                if(currentChar == -1){break;}// -1 indicates the end of the file
                String timeStampString = timeStampStringBuilder.toString();
                Timestamp verifiedExposureTime  = new Timestamp(Long.parseLong(timeStampString));

                Exposure exposure = checkIfSignatureMatchesDB(signature,verifiedExposureTime);
                if(exposure != null){
                    messageBuilder.append(exposure.getMessage())
                            .append("\n");
                }

            }

            String message = messageBuilder.toString();
            if (!message.equals("")){
                return message;
            }else{
                return "You have not been exposed.";
            }
        }

        @Nullable
        private Exposure checkIfSignatureMatchesDB(byte[] signature, Timestamp verifiedExposureTime){
            //returns an exposure if there was an exposure, otherwise returns null.
            List<ContactTrace> contactTraces = CoronaHandler.readContactTrace(MainActivity.getLocalContext());
            for (ContactTrace trace : contactTraces){
                try {
                    Log.i(TAG, "Key: " + CoronaHandler.KeyToString(trace.getKey().getEncoded()));
                    Log.i(TAG, "Signature: " + CoronaHandler.KeyToString(signature));

                    if (CoronaHandler.verifySignature(trace.getKey(), signature)){
                        return new Exposure(trace.getTimestamp(), verifiedExposureTime);
                    }
                }catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException | InvalidKeySpecException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }





    }

}
