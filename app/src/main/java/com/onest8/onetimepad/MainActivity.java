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
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
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

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements  ActionMode.Callback {
    private ArrayList<Entry> entries;
    private EntriesAdapter adapter;
    private View snackView;
    public static int currentEntryIndex = -1;
    public static View currentEntryView = null;
    public static boolean clipboardExpires = false;
    public static boolean inForeground = true;

    private Handler handler;
    private Runnable handlerTask;

    private static final int PERMISSIONS_REQUEST_CAMERA = 42;

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

        entries = SettingsHelper.load(this);

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
                    currentEntryView = null;
                    return;
                }
                currentEntryView = view;
                currentEntryIndex = i;
                adapter.setShowOTP(i);
            }
        });

        if(entries.isEmpty()){
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
            entries.add(e);
            SettingsHelper.store(this, entries);
            adapter.notifyDataSetChanged();
            Snackbar.make(snackView, R.string.msg_account_added, Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            Snackbar.make(snackView, R.string.msg_invalid_qr_code, Snackbar.LENGTH_LONG).setCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar snackbar, int event) {
                super.onDismissed(snackbar, event);
                if(entries.isEmpty()){
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

        if(entries.isEmpty()){
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
            WebView view = (WebView) LayoutInflater.from(this).inflate(R.layout.dialog_about, null);
            view.loadUrl("file:///android_res/raw/about.html");
            new AlertDialog.Builder(this).setView(view).show();
            return true;
        } else if(id == R.id.action_scan){
            scanQRCode();
        } else if (id == R.id.action_clipboard) {
            ClipboardManager myClipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            ClipData abc = myClipboard.getPrimaryClip();
            if (abc.getItemCount() >= 1) {
                ClipData.Item clipItem = abc.getItemAt(0);
                addNewAccount(clipItem.getText().toString());
            } else {
                Snackbar.make(snackView, R.string.msg_clipboard_no_paste, Snackbar.LENGTH_SHORT).show();
            }
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
        adapter.notifyDataSetChanged();

        SettingsHelper.store(this, entries);
    }
}
