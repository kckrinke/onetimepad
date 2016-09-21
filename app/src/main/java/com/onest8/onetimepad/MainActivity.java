package com.onest8.onetimepad;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.zxing.client.android.Intents;
import com.google.zxing.integration.android.IntentIntegrator;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;

import static com.onest8.onetimepad.Utils.readFully;
import static com.onest8.onetimepad.Utils.writeFully;

public class MainActivity extends AppCompatActivity implements  ActionMode.Callback {
    private ArrayList<Entry> entries;
    private EntriesAdapter adapter;
    private View snackView;
    public static int currentEntryIndex = -1;
    public static boolean clipboardExpires = false;
    public static boolean inForeground = true;

    private Handler handler;
    private Runnable handlerTask;

    private static final int PERMISSIONS_REQUEST_CAMERA = 42;

    private String getStringFormat(int sid,Object...arguments) {
        try {
            String strMeatFormat = getResources().getString(sid);
            return String.format(strMeatFormat, arguments);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private void doScanQRCode(){
        new IntentIntegrator(MainActivity.this)
                .setCaptureActivity(CaptureActivityAnyOrientation.class)
                .setOrientationLocked(false)
                .initiateScan();
    }

    private void scanQRCode(){
        // check Android 6 permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            doScanQRCode();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
       if(requestCode == PERMISSIONS_REQUEST_CAMERA) {
           if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
               // permission was granted
               doScanQRCode();
           } else {
               Snackbar.make(snackView, R.string.msg_camera_permission, Snackbar.LENGTH_LONG).setCallback(new Snackbar.Callback() {
                   @Override
                   public void onDismissed(Snackbar snackbar, int event) {
                       super.onDismissed(snackbar, event);

                       if (entries.isEmpty()) {
                           showNoAccount();
                       }
                   }
               }).show();
           }
       }
       else {
           super.onRequestPermissionsResult(requestCode, permissions, grantResults);
       }
    }

    private Entry nextSelection = null;
    private void showNoAccount(){
        Snackbar noAccountSnackbar = Snackbar.make(snackView, R.string.no_accounts, Snackbar.LENGTH_INDEFINITE);
        noAccountSnackbar.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inForeground = true;
        setTitle(R.string.app_name);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        snackView = toolbar;
        setSupportActionBar(toolbar);

        final ListView listView = (ListView) findViewById(R.id.listView);
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);

        clearPassword();
        entries = loadEntries();

        adapter = new EntriesAdapter();
        adapter.setEntries(entries);

        listView.setAdapter(adapter);

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                nextSelection = entries.get(i);
                startActionMode(MainActivity.this);
                return true;
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == currentEntryIndex) {
                    adapter.setShowOTP(-1);
                    ClipData clip = ClipData.newPlainText("","");
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(clip);
                    currentEntryIndex = -1;
                    return;
                }
                currentEntryIndex = i;
                adapter.setShowOTP(i);
            }
        });

        if(entries == null || entries.isEmpty()){
            showNoAccount();
        }

        handler = new Handler();
        handlerTask = new Runnable()
        {
            @Override
            public void run() {
                int progress =  (int) (System.currentTimeMillis() / 1000) % 30 ;
                if (inForeground) {
                    progressBar.setProgress(progress * 100);
                    ObjectAnimator animation = ObjectAnimator.ofInt(progressBar, "progress", (progress + 1) * 100);
                    animation.setDuration(1000);
                    animation.setInterpolator(new LinearInterpolator());
                    animation.start();
                }

                for(int i =0;i < adapter.getCount(); i++){
                    Entry entry = adapter.getItem(i);
                    entry.setCurrentOTP(TOTPHelper.generate(entry.getSecret()));
                    if (progress <= 1 && clipboardExpires) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("","");
                        clipboard.setPrimaryClip(clip);
                        if (inForeground)
                            Snackbar.make(snackView, R.string.msg_clipboard_cleared, Snackbar.LENGTH_SHORT).show();
                        else
                            Toast.makeText(getApplicationContext(),R.string.msg_clipboard_cleared,Toast.LENGTH_SHORT).show();
                        clipboardExpires = false;
                    }
                }
                adapter.notifyDataSetChanged();

                handler.postDelayed(this, 1000);
            }
        };

        Intent sender = getIntent();
        Uri data = sender.getData();
        if (data != null) {
            addNewAccount(data.toString());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        inForeground = true;
        handler.post(handlerTask);
        if (!isPasswordLoaded())
            promptForPassword();
    }

    @Override
    public void onPause() {
        super.onPause();
        inForeground = false;
        if (!clipboardExpires) {
            handler.removeCallbacks(handlerTask);
        }
    }

    protected void addNewAccount(String uri_data) {
        try {
            Entry e = new Entry(uri_data);
            e.setCurrentOTP(TOTPHelper.generate(e.getSecret()));
            if (entries == null)
                entries = new ArrayList<Entry>();
            entries.add(e);
            saveEntries(entries);
            entries = loadEntries();
            adapter.setEntries(entries);
            adapter.notifyDataSetChanged();
            Snackbar.make(snackView, R.string.msg_account_added, Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            Snackbar.make(snackView, R.string.msg_invalid_qr_code, Snackbar.LENGTH_LONG)
                    .setCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar snackbar, int event) {
                super.onDismissed(snackbar, event);
                if(entries == null || entries.isEmpty()){
                    showNoAccount();
                }
                }
            }).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == IntentIntegrator.REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            addNewAccount(intent.getStringExtra(Intents.Scan.RESULT));
            return;
        }

        if(entries == null || entries.isEmpty()){
            showNoAccount();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


        @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if(id == R.id.action_about){
            try {
                WebView view = new WebView(this);
                view.loadUrl("file:///android_res/raw/about.html");
                new AlertDialog.Builder(this).setView(view).show();
            } catch (Exception e) {}
            return true;
        } else if(id == R.id.action_scan){
            scanQRCode();
        } else if (id == R.id.action_manual) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View manualEntryView = inflater.inflate(R.layout.manual_entry, null, false);
            final EditText issuerLabelEntry = (EditText) manualEntryView.findViewById(R.id.issuerLabelEntry);
            final EditText secretCodeEntry = (EditText) manualEntryView.findViewById(R.id.secretCodeEntry);
            new AlertDialog.Builder(MainActivity.this).setView(manualEntryView)
                    .setTitle(R.string.menu_manual)
                    .setPositiveButton(R.string.button_add, new DialogInterface.OnClickListener() {
                                @TargetApi(11)
                                public void onClick(DialogInterface dialog, int id) {
                                    String label = issuerLabelEntry.getText().toString();
                                    String secret = secretCodeEntry.getText().toString();
                                    if (label.isEmpty()) {
                                        label = "Untitled";
                                        int c = 1;
                                        while (adapter.getEntryByLabel(label) != null) {
                                            label = "Untitled " + String.valueOf(c);
                                            c++;
                                        }
                                    }
                                    if (!secret.isEmpty()) {
                                        String customUri = "otpauth://totp/" + label + "?secret=" + secret;
                                        addNewAccount(customUri);
                                    } else {
                                        Snackbar.make(snackView, R.string.msg_missing_secret, Snackbar.LENGTH_SHORT).show();
                                    }
                                    dialog.cancel();
                                }
                            }
                    )
                    .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                                @TargetApi(11)
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            }
                    )
            .show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.menu_edit, menu);

        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        adapter.setCurrentSelection(nextSelection);
        adapter.notifyDataSetChanged();
        actionMode.setTitle(adapter.getCurrentSelection().getLabel());

        return true;
    }

    @Override
    public boolean onActionItemClicked(final ActionMode actionMode, MenuItem menuItem) {
        int id = menuItem.getItemId();

        if (id == R.id.action_delete) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(getString(R.string.alert_remove) + adapter.getCurrentSelection().getLabel() + "?");
            alert.setMessage(R.string.msg_confirm_delete);

            alert.setPositiveButton(R.string.button_remove, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    entries.remove(adapter.getCurrentSelection());

                    Snackbar.make(snackView, R.string.msg_account_removed, Snackbar.LENGTH_LONG).setCallback(new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar snackbar, int event) {
                            super.onDismissed(snackbar, event);

                            if (entries.isEmpty()) {
                                showNoAccount();
                            }
                        }
                    }).show();

                    actionMode.finish();
                }
            });

            alert.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.cancel();
                    actionMode.finish();
                }
            });

            alert.show();

            return true;
        }
        else if (id == R.id.action_edit) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(R.string.alert_rename);

            final EditText input = new EditText(this);
            input.setText(adapter.getCurrentSelection().getLabel());
            alert.setView(input);

            alert.setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    adapter.getCurrentSelection().setLabel(input.getEditableText().toString());
                    actionMode.finish();
                }
            });

            alert.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.cancel();
                    actionMode.finish();
                }
            });

            alert.show();

            return true;
        }

        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        adapter.setCurrentSelection(null);
        saveEntries(entries);
        entries = loadEntries();
        adapter.setEntries(entries);
        adapter.notifyDataSetChanged();
    }


    final private static String DATA_FILE = "datastore.dat";
    final private static String WELL_KNOWN_SALT = "HimalayanSeaSalt";

    private File _datastore = null;
    private File getDatastoreFile() {
        if (_datastore==null)
            _datastore = new File(getFilesDir() + "/" + DATA_FILE);
        return _datastore;
    }

    public ArrayList<Entry> loadEntries() {
        if (getDatastoreFile().exists()) {
            for (int t = 1; t <= 3; t++) {
                try {
                    if (!isPasswordLoaded()) {
                        if (t == 1)
                            promptForPassword();
                        else
                            promptForPassword(getStringFormat(R.string.msg_remain_tries, 4 - t));
                    }
                    if (isPasswordLoaded()) {
                        AesCbcWithIntegrity.SecretKeys keys = AesCbcWithIntegrity.generateKeyFromPassword(retrievePassword(), WELL_KNOWN_SALT);
                        byte[] cipherTextBytes = readFully(getDatastoreFile());
                        AesCbcWithIntegrity.CipherTextIvMac cipherTextIvMac = new AesCbcWithIntegrity.CipherTextIvMac(new String(cipherTextBytes));
                        String plainText = AesCbcWithIntegrity.decryptString(cipherTextIvMac, keys);
                        JSONArray jsonData = new JSONArray(plainText);
                        ArrayList<Entry> entries = new ArrayList<Entry>();
                        for (int j = 0; j < jsonData.length(); j++) {
                            entries.add(new Entry(jsonData.getJSONObject(j)));
                        }
                        return entries;
                    }
                } catch (Exception e) {
                    clearPassword();
                }
            }
            Toast.makeText(this, getStringFormat(R.string.msg_try_again_failure), Toast.LENGTH_LONG).show();
            finish();
            System.exit(0);
        } else {
            saveEntries(entries);
        }
        return new ArrayList<Entry>();
    }

    public boolean saveEntries(ArrayList<Entry> entries) {
        if (!isPasswordLoaded()) {
            if (getDatastoreFile().exists()) {
                loadEntries();
            } else {
                for (int i = 1; i < 3; i++) {
                    promptForNewPassword();
                    if (isPasswordLoaded()) {
                        break;
                    }
                }
                if (!isPasswordLoaded()) {
                    Toast.makeText(this, getStringFormat(R.string.msg_try_again_invalid), Toast.LENGTH_LONG).show();
                    finish();
                    System.exit(0);
                }
            }
        }
        try {
            JSONArray jsonData = new JSONArray();
            for (Entry e : entries) {
                jsonData.put(e.toJSON());
            }
            AesCbcWithIntegrity.SecretKeys keys = AesCbcWithIntegrity.generateKeyFromPassword(retrievePassword(), WELL_KNOWN_SALT);
            AesCbcWithIntegrity.CipherTextIvMac cipherTextIvMac = AesCbcWithIntegrity.encrypt(jsonData.toString(), keys);
            String ciphertextString = cipherTextIvMac.toString();
            writeFully(getDatastoreFile(), ciphertextString.getBytes());
            return true;
        } catch (Exception e) {
            Toast.makeText(this, getStringFormat(R.string.msg_try_again_unknown), Toast.LENGTH_LONG).show();
            finish();
            System.exit(0);
        }
        return false;
    }

    private void cachePassword(String password) {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pass_word",password);
        editor.putLong("pass_time",System.currentTimeMillis()/1000);
        editor.apply();
        editor.commit();
    }

    private String retrievePassword() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        String value = prefs.getString("pass_word", null);
        return value;
    }
    private long retrievePasswordTime() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        long value = prefs.getLong("pass_time", 0L);
        return value;
    }

    private void clearPassword() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("pass_word");
        editor.remove("pass_time");
        editor.apply();
        editor.commit();
    }

    private boolean isPasswordLoaded() {
        String pass = retrievePassword();
        if (pass == null || pass.length() < 4) {
            return false;
        }
        return true;
    }


    private void promptForPassword() {
        promptForPassword(null);
    }
    private void promptForPassword(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message mesg) {
                throw new RuntimeException();
            }
        };
        LayoutInflater inflater = getLayoutInflater();
        View v = inflater.inflate(R.layout.password_prompt, null);
        final EditText passwordText = (EditText)v.findViewById(R.id.password_entry);
        builder.setView(v);
        if (message != null) {
            builder.setMessage(message);
        }
        builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                cachePassword(passwordText.getText().toString());
                handler.sendMessage(handler.obtainMessage());
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                clearPassword();
                dialog.cancel();
                handler.sendMessage(handler.obtainMessage());
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setTitle(getStringFormat(R.string.app_name));
        dialog.show();
        try { Looper.loop(); }
        catch(RuntimeException e2) {}
        return;
    }


    private void promptForNewPassword() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message mesg) {
                throw new RuntimeException();
            }
        };
        final Context context = this;
        LayoutInflater inflater = getLayoutInflater();
        View v = inflater.inflate(R.layout.new_password_prompt, null);
        final EditText passwordText = (EditText)v.findViewById(R.id.password_entry);
        final EditText confirmText = (EditText)v.findViewById(R.id.confirm_entry);
        builder.setView(v);
        builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String pass = passwordText.getText().toString();
                String conf = confirmText.getText().toString();
                if (pass.equals(conf) && pass.length() >= 4) {
                    cachePassword(pass);
                } else {
                    clearPassword();
                    Toast.makeText(context,getStringFormat(R.string.msg_invalid_passwords),Toast.LENGTH_SHORT);
                }
                handler.sendMessage(handler.obtainMessage());
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                clearPassword();
                dialog.cancel();
                handler.sendMessage(handler.obtainMessage());
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setTitle(getStringFormat(R.string.app_name));
        dialog.show();
        try { Looper.loop(); }
        catch(RuntimeException e2) {}
        return;
    }

}
