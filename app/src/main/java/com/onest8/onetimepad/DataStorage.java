package com.onest8.onetimepad;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;

import static com.onest8.onetimepad.Utils.readFully;
import static com.onest8.onetimepad.Utils.writeFully;

/**
 * Created by kck on 9/14/16.
 *
 * - password prompt
 * - change password
 * - open data store
 * - close data store
 *
 */
public class DataStorage {
    final private static String DATA_FILE = "datastore.dat";

    final private static String _wellknownsalt = "HimalayanSeaSalt";
    private static String _passphrase = "";
    private static File _datastore = null;

    public static File getDatastore(Context context) {
        if (_datastore==null)
            _datastore = new File(context.getFilesDir() + "/" + DATA_FILE);
        return _datastore;
    }

    public static void clearPassword() {
        _passphrase = "";
    }

    private static void promptForPassword(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message mesg) {
                throw new RuntimeException();
            }
        };
        LayoutInflater inflater = ((Activity)context).getLayoutInflater();
        View v = inflater.inflate(R.layout.password_prompt, null);
        final EditText passwordText = (EditText)v.findViewById(R.id.password_entry);
        builder.setView(v);
        builder.setPositiveButton(R.string.zxing_button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                _passphrase = passwordText.getText().toString();
                handler.sendMessage(handler.obtainMessage());
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                handler.sendMessage(handler.obtainMessage());
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        try { Looper.loop(); }
        catch(RuntimeException e2) {}
        return;
    }

    private static boolean _new_password_abort = false;
    private static void promptForNewPassword(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message mesg) {
                throw new RuntimeException();
            }
        };
        LayoutInflater inflater = ((Activity)context).getLayoutInflater();
        View v = inflater.inflate(R.layout.new_password_prompt, null);
        final EditText passwordText = (EditText)v.findViewById(R.id.password_entry);
        final EditText confirmText = (EditText)v.findViewById(R.id.confirm_entry);
        builder.setView(v);
        builder.setPositiveButton(R.string.zxing_button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String pass = passwordText.getText().toString();
                String conf = confirmText.getText().toString();
                if (pass.equals(conf) && pass.length() >= 4) {
                    _passphrase = pass;
                }
                handler.sendMessage(handler.obtainMessage());
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                _new_password_abort = true;
                dialog.cancel();
                handler.sendMessage(handler.obtainMessage());
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        try { Looper.loop(); }
        catch(RuntimeException e2) {}
        return;
    }

    public static ArrayList<Entry> init_and_load(Context context) {
        ArrayList<Entry> entries = new ArrayList<>();
        File datastore = getDatastore(context);
        if (datastore.exists()) {
            boolean do_exit = true;
            for (int i = 2; i >= 0; i--) {
                DataStorage.promptForPassword(context);
                try {
                    entries = DataStorage.load(context);
                    do_exit = false;
                    break;
                } catch (RuntimeException e) {
                    if (i > 0) {
                        Toast.makeText(context, e.getMessage() + " - " + String.valueOf(i) + " more tries!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            if (do_exit) {
                Toast.makeText(context, "Too many failed attempts. OneTimePad exiting now.", Toast.LENGTH_LONG).show();
                ((Activity) context).finish();
                System.exit(0);
            }
        } else {
            boolean do_exit = true;
            while (true) {
                DataStorage.promptForNewPassword(context);
                if (_new_password_abort) {
                    break;
                }
                if (_passphrase == null || _passphrase.isEmpty()) {
                    Toast.makeText(
                            context,
                            "Passwords don't match or are less than 4 characters long. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                } else {
                    do_exit = false;
                    break;
                }
            }
            if (do_exit) {
                Toast.makeText(
                        context,
                        "Password is required. Re-launch to try again.",
                        Toast.LENGTH_LONG
                ).show();
                ((Activity) context).finish();
                System.exit(0);
            }
            store(context,entries);
        }
        return entries;
    }

    public static void store(Context context, ArrayList<Entry> entries) throws RuntimeException {

        if (_passphrase == null || _passphrase.isEmpty()) {
            throw new RuntimeException("Password is blank");
        }

        if (entries==null)
            entries = new ArrayList<Entry>();

        try {
            JSONArray jsonData = new JSONArray();
            for(Entry e: entries){
                jsonData.put(e.toJSON());
            }
            AesCbcWithIntegrity.SecretKeys keys = AesCbcWithIntegrity.generateKeyFromPassword(_passphrase,_wellknownsalt);
            AesCbcWithIntegrity.CipherTextIvMac cipherTextIvMac = AesCbcWithIntegrity.encrypt(jsonData.toString(), keys);
            String ciphertextString = cipherTextIvMac.toString();
            writeFully(getDatastore(context),ciphertextString.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        return;
    }

    public static ArrayList<Entry> load(Context context) throws RuntimeException {

        if (_passphrase == null || _passphrase.isEmpty()) {
            throw new RuntimeException("Password is blank");
        }

        ArrayList<Entry> entries = new ArrayList<>();

        try {
            AesCbcWithIntegrity.SecretKeys keys = AesCbcWithIntegrity.generateKeyFromPassword(_passphrase,_wellknownsalt);
            byte[] cipherTextBytes = readFully(getDatastore(context));
            AesCbcWithIntegrity.CipherTextIvMac cipherTextIvMac = new AesCbcWithIntegrity.CipherTextIvMac(new String(cipherTextBytes));
            String plainText = AesCbcWithIntegrity.decryptString(cipherTextIvMac, keys);
            JSONArray jsonData = new JSONArray(plainText);
            for (int i=0; i < jsonData.length(); i++) {
                entries.add(new Entry(jsonData.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return entries;
    }
}
