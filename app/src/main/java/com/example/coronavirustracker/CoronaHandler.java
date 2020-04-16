package com.example.coronavirustracker;

import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.Pair;

import com.example.coronavirustracker.database.ContactTrace;
import com.example.coronavirustracker.database.ContactTraceDao;
import com.example.coronavirustracker.database.ContactTraceRoomDatabase;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import android.util.Base64;

import static java.util.Collections.emptyList;

//TODO: Handle expcetions inside of functions instead of outside
public class CoronaHandler {

    private final static String CRYPTO_METHOD = "RSA";
    private final static int CRYPTO_BITS = 2048;


    private boolean determineMatchIndividual(Key key, Signature signature){
        // If it is determined that the signature was made by the key, return true, otherwise, return false.
        return false;
    }

    private Timestamp[] findExposuresGivenInput(Pair<Key, Timestamp>[] keys, Signature[] signature){
        //loops through determineMatchIndividual to find if any of the keys match with any of the signatures
        return null;
    }

    public Timestamp[] findDangerousExposures(){
        // reads the website and hard drive to determine keys and signatures, then uses countExposuresGivenInput to determine number of times. Returns the times when you were exposed
        return null;
    }

    private Signature[] readSignatures(){
        //goes to the website and returns a list of all signatures found online.
        return new Signature[]{};
    }

    public static List<ContactTrace> readContactTrace(Context context){
        // returns a list of all keys found on the hard-drive of the phone.
        ContactTraceRoomDatabase db = ContactTraceRoomDatabase.getDatabase(context);
        ContactTraceDao dao = db.ContactTraceDao();
        return dao.getAll();
    }

    public void writeSignature(Signature signature){
        // Tries to put an inputted signature onto the website. If not sucsessfull, raises an error (error type?)
    }

    public static byte[] generateSignature(Key pvt) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign((PrivateKey) pvt);
        // TODO: Add message being signed
        return sign.sign();
    }

    public static boolean verifySignature(Key pub, byte[] signature) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initVerify((PublicKey) pub);
        return sign.verify(signature);
    }

    public static void writeKey(ContactTrace contactTrace, Context context){
        // Tries to put an inputted key onto the database, as well as the current timestamp. If not sucsessfull, raises an error (error type?)
        ContactTraceRoomDatabase db = ContactTraceRoomDatabase.getDatabase(context);
        ContactTraceDao dao = db.ContactTraceDao();
        dao.insert(contactTrace);
    }


    public static KeyPair generateKeyPair() {
        //Generates a privateKey and a publicKey.
        KeyPairGenerator kpg = null;
        try {
            kpg = KeyPairGenerator.getInstance(CRYPTO_METHOD);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        kpg.initialize(CRYPTO_BITS);
        KeyPair kp = kpg.generateKeyPair();
        return kp;

    }
    public static void writeKeyPair(KeyPair kp, Context context) throws IOException{
        //writes the publickey and privatekey on the hard drive of the phone for future use.
        Key pub = kp.getPublic();
        Key pvt = kp.getPrivate();
        writeToFile(pub, MainActivity.PUBLIC_KEY_LOCATION, context);
        writeToFile(pvt, MainActivity.PRIVATE_KEY_LOCATION, context);
    }
    public static void writeKeyPair(Key pvt, Key pub, Context context) throws IOException{
        //writes the publickey and privatekey on the hard drive of the phone for future use.
        writeToFile(pub, MainActivity.PUBLIC_KEY_LOCATION, context);
        writeToFile(pvt, MainActivity.PRIVATE_KEY_LOCATION, context);
    }


    public static boolean checkIfFileExist(String filename, Context context){
        List<String> files = Arrays.asList(context.fileList());
        return files.contains(filename);
    }

    public static boolean checkIfKeysExist(Context context){
        // Returns true if a publickey and a privatekey exist as files, otherwise returns false.
        return checkIfFileExist(MainActivity.PUBLIC_KEY_LOCATION, context) && checkIfFileExist(MainActivity.PRIVATE_KEY_LOCATION, context);
    }


    public static void writeToFile(Key data, String filename, Context context) throws IOException{
        try (FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE)) {
            fos.write(data.getEncoded());
        }
    }

    public static void writeToFile(byte[] data, String filename, Context context) throws IOException{
        try (FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE)) {
            fos.write(data);
        }
    }

    public static void writeToFile(String data, String filename, Context context) throws IOException{
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(filename, Context.MODE_PRIVATE));
        outputStreamWriter.write(data);
        outputStreamWriter.close();
    }





    public static Pair<Key, Key> readKeyPair(Context context) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        //returns <Privatekey, Publickey>
        //TODO: rewrite to make this not have to read the file twice
        Key pvt = binaryToPrivateKey(readFile(MainActivity.PRIVATE_KEY_LOCATION, context));
        Key pub = binaryToPublicKey(readFile(MainActivity.PUBLIC_KEY_LOCATION, context));
        return new Pair<>(pvt, pub);
    }

    public static byte[] readFile(String filename, Context context) throws IOException {
        int len = findFileLen(filename, context);
        FileInputStream fis = context.openFileInput(filename);
        byte[] fileAsBinary = new byte[len];
        fis.read(fileAsBinary);
        return fileAsBinary;
    }

    public static String readFileAsString(String fileName, Context context) throws IOException {
        String ret = "";
        InputStream inputStream = context.openFileInput(fileName);
        if ( inputStream != null ) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String receiveString = "";
            StringBuilder stringBuilder = new StringBuilder();

            while ( (receiveString = bufferedReader.readLine()) != null ) {
                stringBuilder.append(receiveString);
            }

            inputStream.close();
            ret = stringBuilder.toString();
        }

        return ret;

    }

    private static int findFileLen(String filename, Context context) throws IOException {
        FileInputStream fis = context.openFileInput(filename);
        int len = 0;
        while(fis.read() != -1){
            len ++;
        }
        return len;
    }

    public static Key binaryToPublicKey(byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
        X509EncodedKeySpec ks = new X509EncodedKeySpec(bytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        Key pub = kf.generatePublic(ks);
        return pub;
    }

    public static Key binaryToPrivateKey(byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec(bytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        Key pvt = kf.generatePrivate(ks);
        return pvt;

    }

    public static String KeyToString(Key key){
        return new String(Base64.encode(key.getEncoded(), android.util.Base64.DEFAULT));
    }
    public static String KeyToString(byte[] key){
        return new String(Base64.encode(key, android.util.Base64.DEFAULT));
    }


    public static String generateRandomUUID(){
        return UUID.randomUUID().toString();
    }

    public static boolean uuidsMatch(ParcelUuid[] uuids, UUID targetUUID){
        String targetUUIDString = targetUUID.toString();
        for(ParcelUuid uuid:uuids){
            if(uuid.getUuid().toString().equals(targetUUIDString)){
                return true;
            }
        }
        return false;
    };


}
