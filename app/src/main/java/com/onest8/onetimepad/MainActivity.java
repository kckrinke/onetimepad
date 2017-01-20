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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.android.Intents;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;

import org.apache.commons.codec.binary.Base32;
import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;

import static com.onest8.onetimepad.Utils.readFully;
import static com.onest8.onetimepad.Utils.writeFully;

public class MainActivity extends AppCompatActivity implements  ActionMode.Callback {
    private ArrayList<Entry> entries;
    private EntriesAdapter adapter;
    private View snackView;
    private EditText searchEntry;
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

    public void applySearchFilter() {
        currentEntryIndex = -1;
        adapter.setShowOTP(-1);
        adapter.filter(searchEntry.getText().toString());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inForeground = true;
        setTitle(R.string.app_name);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final ListView listView = (ListView) findViewById(R.id.listView);
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);

        snackView = listView;

        clearPassword();
        entries = loadEntries();

        adapter = new EntriesAdapter();
        adapter.setEntries(entries);

        listView.setAdapter(adapter);

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                nextSelection = adapter.getVisibleEntries().get(i);
                startActionMode(MainActivity.this);
                return true;
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == currentEntryIndex) {
                    adapter.setCurrentSelection(null);
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

        searchEntry = (EditText) findViewById(R.id.searchText);
        searchEntry.setFocusable(true);
        searchEntry.setFocusableInTouchMode(true);
        searchEntry.setVisibility(View.GONE);
        searchEntry.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        MainActivity.this.applySearchFilter();
                    }
                }
        );
        searchEntry.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(final View v, final boolean hasFocus) {
                searchEntry.post(new Runnable() {
                    @Override
                    public void run() {
                        searchEntry.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (v.getId() == R.id.searchText) {
                            if (hasFocus) {
                                imm.showSoftInput(searchEntry, InputMethodManager.SHOW_IMPLICIT);
                            } else {
                                imm.hideSoftInputFromWindow(searchEntry.getWindowToken(), 0);
                            }
                        }
                    }
                });
            }
        });

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
                            popShortToast(R.string.msg_clipboard_cleared);
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
        MainActivity.this.applySearchFilter();
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
            adapter.resetFilter();
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
        } else {
            applySearchFilter();
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
        } else if(id == R.id.action_scan) {
            scanQRCode();
        } else if(id == R.id.action_change_pass) {
            clearPassword();
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.msg_change_existing_pass)
                    .setCancelable(false)
                    .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ArrayList<Entry> entries_cached = loadEntries();
                            if (!isPasswordLoaded()) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle(R.string.app_name)
                                        .setMessage(R.string.msg_try_again_failure)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                MainActivity.this.finish();
                                                System.exit(0);
                                            }
                                        })
                                        .show();
                            }
                            getDatastoreFile().delete();
                            clearPassword();
                            saveEntries(entries_cached);
                        }
                    })
                    .show();

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
        } else if (id == R.id.action_search) {
            searchEntry.setText("");
            adapter.resetFilter();
            if (searchEntry.getVisibility() == View.VISIBLE) {
                searchEntry.setVisibility(View.GONE);
            } else {
                searchEntry.setVisibility(View.VISIBLE);
                searchEntry.requestFocus();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.menu_edit, menu);
        adapter.setIsInActionMode(true);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        adapter.setCurrentSelection(nextSelection);
        adapter.notifyDataSetChanged();
        actionMode.setTitle(adapter.getCurrentSelection().getLabel());
        adapter.setIsInActionMode(true);
        return true;
    }

    @Override
    public boolean onActionItemClicked(final ActionMode actionMode, MenuItem menuItem) {
        int id = menuItem.getItemId();

        if (id == R.id.action_delete) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(getStringFormat(R.string.alert_remove, adapter.getCurrentSelection().getLabel()));
            alert.setMessage(R.string.msg_confirm_delete);

            alert.setPositiveButton(R.string.button_remove, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    entries.remove(adapter.getCurrentSelection());
                    adapter.setEntries(entries);
                    adapter.notifyDataSetChanged();
                    saveEntries(entries);

                    Snackbar.make(snackView, R.string.msg_account_removed, Snackbar.LENGTH_LONG).setCallback(new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar snackbar, int event) {
                            super.onDismissed(snackbar, event);

                            if (entries.isEmpty()) {
                                showNoAccount();
                            }
                        }
                    }).show();

                    adapter.setIsInActionMode(false);
                    actionMode.finish();
                }
            });

            alert.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.cancel();
                    adapter.setIsInActionMode(false);
                    actionMode.finish();
                }
            });

            alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dialog.cancel();
                    adapter.setIsInActionMode(false);
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
                    adapter.setIsInActionMode(false);
                    actionMode.finish();
                }
            });

            alert.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.cancel();
                    adapter.setIsInActionMode(false);
                    actionMode.finish();
                }
            });

            alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dialog.cancel();
                    adapter.setIsInActionMode(false);
                    actionMode.finish();
                }
            });

            alert.show();

            return true;
        } else if (id == R.id.action_qrcode) {
            String b32code = new String(new Base32().encode(adapter.getCurrentSelection().getSecret()));
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(adapter.getCurrentSelection().getLabel());
            alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dialog.cancel();
                    adapter.setIsInActionMode(false);
                    actionMode.finish();
                }
            });
            alert.setMessage(b32code);
            try {
                BitMatrix bitMatrix = new MultiFormatWriter()
                        .encode("otpauth://totp/"
                                + adapter.getCurrentSelection().getLabel()
                                + "?secret=" + b32code,
                                BarcodeFormat.QR_CODE,
                                400,
                                400,
                                null
                        );
                int height = bitMatrix.getHeight();
                int width = bitMatrix.getWidth();
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }
                ImageView image = new ImageView(getApplicationContext());
                image.setImageBitmap(bitmap);

                alert.setView(image);

                alert.show();
            } catch (WriterException e) {
                Log.e("OneTimePad", e.getMessage());
            }

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
        adapter.setIsInActionMode(false);
        applySearchFilter();
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
                    if (isPasswordPromptCancelled()) {
                        popShortToast(R.string.msg_cancel_exit);
                        finish();
                        System.exit(0);
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
            popLongToast(R.string.msg_try_again_failure);
            finish();
            System.exit(0);
        } else {
            saveEntries(new ArrayList<Entry>());
        }
        return new ArrayList<Entry>();
    }

    public boolean saveEntries(ArrayList<Entry> entries) {
        if (!isPasswordLoaded()) {
            if (getDatastoreFile().exists()) {
                loadEntries();
            } else {
                for (int i = 1; i < 4; i++) {
                    if (i==1) promptForNewPassword();
                    else promptForNewPassword(getStringFormat(R.string.msg_remain_tries,(4-i)));
                    if (isPasswordLoaded()) {
                        break;
                    }
                }
                if (isPasswordLoaded()==false) {
                    popLongToast(R.string.msg_unable_save);
                    return false;
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
            popLongToast(R.string.msg_try_again_unknown,e.getMessage());
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
        editor.commit();
        popLogD("password cache cleared");
    }

    private boolean isPasswordLoaded() {
        String pass = retrievePassword();
        if (pass == null || pass.length() < 4) {
            popLogD("isPasswordLoaded? NO");
            return false;
        }
        popLogD("isPasswordLoaded? YES");
        return true;
    }

    private boolean isPasswordPromptCancelled() {
        return _pass_prompt_cancelled;
    }

    private boolean _pass_prompt_cancelled = false;
    private void promptForPassword() {
        promptForPassword(null);
    }
    private void promptForPassword(String message) {
        _pass_prompt_cancelled = false;
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
                _pass_prompt_cancelled = true;
                clearPassword();
                dialog.cancel();
                handler.sendMessage(handler.obtainMessage());
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setTitle(getStringFormat(R.string.app_name));

        // https://turbomanage.wordpress.com/2012/05/02/show-soft-keyboard-automatically-when-edittext-receives-focus/
        // Request focus and show soft keyboard automatically
        passwordText.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        dialog.show();
        try { Looper.loop(); }
        catch(RuntimeException e2) {}
        return;
    }

    private void promptForNewPassword() {
        promptForNewPassword(null);
    }
    private void promptForNewPassword(String message) {
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
        if (message != null) {
            builder.setMessage(message);
        }
        builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String pass = passwordText.getText().toString();
                String conf = confirmText.getText().toString();
                if (pass.equals(conf) && pass.length() >= 4) {
                    cachePassword(pass);
                } else {
                    clearPassword();
                    popShortToast(R.string.msg_invalid_passwords);
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

    public void popLogD(String message) {
        boolean isDebuggable =  ( 0 != ( getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE ) );
        if (isDebuggable)
            Log.d("OneTimePad",message);
    }

    public void popShortToast(int sid, Object...args) {
        popToast(Toast.LENGTH_SHORT,sid,args);
    }
    public void popLongToast(int sid, Object...args) {
        popToast(Toast.LENGTH_LONG,sid,args);
    }
    public void popToast(int duration, int sid, Object...args) {
        popToast(duration,getStringFormat(sid,args));
    }
    public void popToast(int duration, String msg) {
        Toast.makeText(getApplicationContext(),msg,duration).show();
        popLogD("notify: "+msg);
    }

}
