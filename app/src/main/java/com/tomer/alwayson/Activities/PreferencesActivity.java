package com.tomer.alwayson.Activities;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.color.ColorChooserDialog;
import com.android.vending.billing.IInAppBillingService;
import com.tomer.alwayson.ContextConstatns;
import com.tomer.alwayson.Globals;
import com.tomer.alwayson.Prefs;
import com.tomer.alwayson.R;
import com.tomer.alwayson.SecretConstants;
import com.tomer.alwayson.Services.StarterService;
import com.tomer.alwayson.Services.WidgetUpdater;
import com.tomer.alwayson.SettingsFragment;

public class PreferencesActivity extends AppCompatActivity implements ColorChooserDialog.ColorCallback, ContextConstatns {
    Prefs prefs;
    Intent billingServiceIntent;
    private Intent starterServiceIntent;
    private Intent widgetUpdaterService;
    private IInAppBillingService mService;
    private ServiceConnection mServiceConn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(getApplicationContext());
        prefs.apply();
        resetPaymentService();
        if (!prefs.permissionGranting) {
            startActivity(new Intent(getApplicationContext(), Intro.class));
            finish();
        } else {
            setContentView(R.layout.activity_main);
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            getFragmentManager().beginTransaction()
                    .replace(R.id.preferences_holder, new SettingsFragment())
                    .commit();

            handlePermissions();

            starterServiceIntent = new Intent(getApplicationContext(), StarterService.class);
            widgetUpdaterService = new Intent(getApplicationContext(), WidgetUpdater.class);

            billingServiceIntent =
                    new Intent("com.android.vending.billing.InAppBillingService.BIND");
            billingServiceIntent.setPackage("com.android.vending");
            bindService(billingServiceIntent, mServiceConn, Context.BIND_AUTO_CREATE);

            donateButtonSetup();

            stopService(starterServiceIntent);
            startService(starterServiceIntent);

            Globals.colorDialog = new ColorChooserDialog.Builder(this, R.string.settings_text_color)
                    .titleSub(R.string.settings_text_color_desc)
                    .doneButton(R.string.md_done_label)
                    .cancelButton(R.string.md_cancel_label)
                    .backButton(R.string.md_back_label)
                    .preselect(-1)
                    .accentMode(true)
                    .dynamicButtonColor(false);
        }
    }

    private void donateButtonSetup() {
        Button donateButton = (Button) findViewById(R.id.donate);
        assert donateButton != null;
        donateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PreferencesActivity.promptToSupport(PreferencesActivity.this, mService, findViewById(android.R.id.content));
            }
        });

    }

    private void handlePermissions() {
        boolean phonePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA},
                        123);
            }
        }
        if (phonePermission) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1, 2003, 65794, -2);
            lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;

            try {
                View view = new View(getApplicationContext());
                ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(view, lp);
                ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(view);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.System.canWrite(getApplicationContext())) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                }
            } catch (Exception e) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.feedback:
                PackageInfo pInfo = null;
                try {
                    pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
                Intent i = new Intent(Intent.ACTION_SENDTO);
                i.setData(Uri.parse("mailto:")); // only email apps should handle this
                i.putExtra(Intent.EXTRA_EMAIL, new String[]{"tomerosenfeld007@gmail.com"});
                i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
                assert pInfo != null;
                i.putExtra(Intent.EXTRA_TEXT, "version:" + pInfo.versionName + "\n" + "Device:" + Build.MANUFACTURER + " " + Build.MODEL + " " + Build.DEVICE + "\n" + prefs.toString());
                try {
                    startActivity(Intent.createChooser(i, "Send mail..."));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(getApplicationContext(), getString(R.string.error_1_no_email_client), Toast.LENGTH_SHORT).show();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unbindService(mServiceConn);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onColorSelection(@NonNull ColorChooserDialog dialog, @ColorInt int selectedColor) {
        Log.d(ContextConstatns.MAIN_SERVICE_LOG_TAG, String.valueOf(selectedColor));
        prefs.setInt(Prefs.KEYS.TEXT_COLOR.toString(), selectedColor);
    }

    public static void promptToSupport(final Activity context, final IInAppBillingService mService, final View rootView) {
        new MaterialDialog.Builder(context)
                .title(R.string.action_support_the_development)
                .content(R.string.support_how_much)
                .items(R.array.support_items)
                .itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                String googleIAPCode = SecretConstants.getPropertyValue(context, "googleIAPCode");
                                Bundle buyIntentBundle;
                                PendingIntent pendingIntent = null;
                                try {
                                    switch (which) {
                                        case 0:
                                            String IAPID = SecretConstants.getPropertyValue(context, "IAPID");
                                            try {
                                                buyIntentBundle = mService.getBuyIntent(3, context.getPackageName(),
                                                        IAPID, "inapp", googleIAPCode);
                                                pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                                                if (pendingIntent == null)
                                                    Snackbar.make(rootView, context.getString(R.string.thanks), Snackbar.LENGTH_LONG).show();
                                            } catch (RemoteException e) {
                                                Snackbar.make(rootView, context.getString(R.string.unknown_error), Snackbar.LENGTH_LONG).show();
                                                e.printStackTrace();
                                            }
                                            break;
                                        case 1:
                                            String IAPID2 = SecretConstants.getPropertyValue(context, "IAPID2");
                                            try {
                                                buyIntentBundle = mService.getBuyIntent(3, context.getPackageName(),
                                                        IAPID2, "inapp", googleIAPCode);
                                                pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                                                if (pendingIntent == null)
                                                    Snackbar.make(rootView, context.getString(R.string.thanks_great), Snackbar.LENGTH_LONG).show();
                                            } catch (RemoteException e) {
                                                Snackbar.make(rootView, context.getString(R.string.unknown_error), Snackbar.LENGTH_LONG).show();
                                                e.printStackTrace();
                                            }
                                            break;
                                        case 2:
                                            String IAPID3 = SecretConstants.getPropertyValue(context, "IAPID3");
                                            try {
                                                buyIntentBundle = mService.getBuyIntent(3, context.getPackageName(),
                                                        IAPID3, "inapp", googleIAPCode);
                                                pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                                                if (pendingIntent == null)
                                                    Snackbar.make(rootView, context.getString(R.string.thanks_huge), Snackbar.LENGTH_LONG).show();
                                            } catch (RemoteException e) {
                                                Snackbar.make(rootView, context.getString(R.string.unknown_error), Snackbar.LENGTH_LONG).show();
                                                e.printStackTrace();
                                            }
                                            break;
                                        case 3:
                                            String IAPID4 = SecretConstants.getPropertyValue(context, "IAPID4");
                                            try {
                                                buyIntentBundle = mService.getBuyIntent(3, context.getPackageName(),
                                                        IAPID4, "inapp", googleIAPCode);
                                                pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                                                if (pendingIntent == null)
                                                    Snackbar.make(rootView, context.getString(R.string.thanks_crazy), Snackbar.LENGTH_LONG).show();
                                            } catch (RemoteException e) {
                                                Snackbar.make(rootView, context.getString(R.string.unknown_error), Snackbar.LENGTH_LONG).show();
                                                e.printStackTrace();
                                            }
                                            break;
                                    }
                                    context.startIntentSenderForResult(pendingIntent.getIntentSender(),
                                            1001, new Intent(), 0, 0,
                                            0);
                                } catch (Exception e) {
                                    Snackbar.make(rootView, context.getString(R.string.unknown_error), Snackbar.LENGTH_LONG).show();
                                }

                                return true;
                            }
                        }
                ).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            Log.d("Purchase state", String.valueOf(resultCode));
            if (resultCode == RESULT_OK) {
                resetPaymentService();
                Log.d("User bought item", data.getStringExtra("INAPP_PURCHASE_DATA"));
            }
        }
    }

    void resetPaymentService() {
        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                mService = null;
            }

            @Override
            public void onServiceConnected(ComponentName name,
                                           IBinder service) {
                mService = IInAppBillingService.Stub.asInterface(service);
                try {
                    Globals.ownedItems = mService.getPurchases(3, getPackageName(), "inapp", null).getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                    Globals.mService = mService;
                    Log.d("BOUGHT_ITEMS", String.valueOf(Globals.ownedItems));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
    }
}
