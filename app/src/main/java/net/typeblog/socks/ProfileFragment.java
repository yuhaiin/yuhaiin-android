package net.typeblog.socks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.*;
import net.typeblog.socks.util.Constants;
import net.typeblog.socks.util.Profile;
import net.typeblog.socks.util.ProfileManager;
import net.typeblog.socks.util.Utility;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static net.typeblog.socks.BuildConfig.DEBUG;
import static net.typeblog.socks.util.Constants.*;

public class ProfileFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener {
    private final Context context;
    private ProfileManager mManager;
    private Profile mProfile;
    // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
    ActivityResultLauncher<Intent> startVpnLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Utility.startVpn(getActivity(), mProfile);
                    }
                }
            });
    private SwitchCompat mSwitch;
    private IVpnService mBinder;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName p1, IBinder binder) {
            mBinder = IVpnService.Stub.asInterface(binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName p1) {
            mBinder = null;
        }
    };
    private final BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), Constants.INTENT_DISCONNECTED)) {
                if (DEBUG) {
                    Log.d("yuhaiin", "onReceive: DISCONNECTED");
                }

                mSwitch.setChecked(false);
                mSwitch.setEnabled(true);
            } else if (Objects.equals(intent.getAction(), INTENT_CONNECTED)) {
                if (DEBUG) {
                    Log.d("yuhaiin", "onReceive: CONNECTED");
                }
                mSwitch.setChecked(true);
                mSwitch.setEnabled(true);
                requireActivity().bindService(new Intent(requireContext(), SocksVpnService.class), mConnection, 0);
            } else if (Objects.equals(intent.getAction(), INTENT_CONNECTING)) {
                if (DEBUG) {
                    Log.d("yuhaiin", "onReceive: CONNECTING");
                }
                mSwitch.setEnabled(false);
            } else if (Objects.equals(intent.getAction(), INTENT_DISCONNECTING)) {
                if (DEBUG) {
                    Log.d("yuhaiin", "onReceive: DISCONNECTING");
                }
                mSwitch.setEnabled(false);
            }
        }
    };
    private ListPreference mPrefProfile;
    private DropDownPreference mPrefRoutes;
    private EditTextPreference mPrefHttpServerPort;
    private EditTextPreference mPrefSocks5ServerPort;
    private EditTextPreference mPrefUsername;
    private EditTextPreference mPrefPassword;
    private EditTextPreference mPrefFakeDnsCidr;
    private EditTextPreference mPrefDnsPort;
    private MultiSelectListPreference mPrefAppList;
    private SwitchPreference mPrefUserpw, mPrefPerApp, mPrefAppBypass, mPrefIPv6, mPrefAuto;
    private EditTextPreference mPrefYuhaiinHost;

    ProfileFragment(Context context) {
        Log.d("yuhaiin", "ProfileFragment: new profieFragment");
        this.context = context;
    }


    @Override
    public void onStart() {
        super.onStart();
        if (mBinder == null) {
            requireActivity().bindService(new Intent(getActivity(), SocksVpnService.class), mConnection, 0);
        }
        IntentFilter f = new IntentFilter();
        f.addAction(INTENT_DISCONNECTED);
        f.addAction(INTENT_CONNECTED);
        f.addAction(INTENT_CONNECTING);
        f.addAction(INTENT_DISCONNECTING);
        context.registerReceiver(bReceiver, f);
    }

    @Override
    public void onCreate(Bundle saveInstanceState) {
        super.onCreate(saveInstanceState);

        addPreferencesFromResource(R.xml.settings);
        setHasOptionsMenu(true);
        mManager = new ProfileManager(requireActivity().getApplicationContext());
        initPreferences();
        reload();
    }

    @Override
    public void onCreatePreferences(@org.jetbrains.annotations.Nullable Bundle savedInstanceState, @org.jetbrains.annotations.Nullable String rootKey) {
    }

    @NonNull
    @NotNull
    @Override
    public View onCreateView(@NonNull @NotNull LayoutInflater inflater, @Nullable @org.jetbrains.annotations.Nullable ViewGroup container, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        assert container != null;
//        FloatingActionButton fab = container.findViewById(R.id.floatingActionButton);
//        fab.setOnClickListener(v -> Log.d("yuhaiin", "onClick: float button"));
        return super.onCreateView(inflater, container, savedInstanceState);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        context.unregisterReceiver(bReceiver);
    }

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, @NotNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.main, menu);
        MenuItem s = menu.findItem(R.id.switch_main);
        mSwitch = (SwitchCompat) s.getActionView();

        if (mBinder != null) {
            try {
                mSwitch.setChecked(mBinder.isRunning());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        mSwitch.setOnCheckedChangeListener(this);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.prof_add:
                addProfile();
                return true;
            case R.id.prof_del:
                removeProfile();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPreferenceClick(@NotNull Preference p) {
        // TODO: Implement this method
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onPreferenceChange(@NotNull Preference p, Object newValue) {
        if (p == mPrefProfile) {
            String name = newValue.toString();
            mProfile = mManager.getProfile(name);
            mManager.switchDefault(name);
            reload();
            return true;
        } else if (p == mPrefHttpServerPort) {
            if (TextUtils.isEmpty(newValue.toString())) return false;
            mProfile.setHttpServerPort(Integer.parseInt(newValue.toString()));
            resetTextN(mPrefHttpServerPort, newValue);
            return true;
        } else if (p == mPrefSocks5ServerPort) {
            if (TextUtils.isEmpty(newValue.toString())) return false;

            mProfile.setSocks5ServerPort(Integer.parseInt(newValue.toString()));
            resetTextN(mPrefSocks5ServerPort, newValue);
            return true;
        } else if (p == mPrefUserpw) {
            mProfile.setIsUserpw(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefUsername) {
            mProfile.setUsername(newValue.toString());
            resetTextN(mPrefUsername, newValue);
            return true;
        } else if (p == mPrefPassword) {
            mProfile.setPassword(newValue.toString());
            resetTextN(mPrefPassword, newValue);
            return true;
        } else if (p == mPrefRoutes) {
            mProfile.setRoute(newValue.toString());
            resetListN(mPrefRoutes, newValue);
            return true;
        } else if (p == mPrefFakeDnsCidr) {
            mProfile.setFakeDnsCidr(newValue.toString());
            resetTextN(mPrefFakeDnsCidr, newValue);
            return true;
        } else if (p == mPrefDnsPort) {
            if (TextUtils.isEmpty(newValue.toString())) return false;

            mProfile.setDnsPort(Integer.parseInt(newValue.toString()));
            resetTextN(mPrefDnsPort, newValue);
            return true;
        } else if (p == mPrefPerApp) {
            mProfile.setIsPerApp(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefAppBypass) {
            mProfile.setIsBypassApp(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefAppList) {
            mProfile.setAppList((HashSet<String>) newValue);
            updateAppList();
            Log.d("yuhaiin", "appList:\n" + mProfile.getAppList().toString());
            return true;
        } else if (p == mPrefIPv6) {
            mProfile.setHasIPv6(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefAuto) {
            mProfile.setAutoConnect(Boolean.parseBoolean(newValue.toString()));
            return true;
        } else if (p == mPrefYuhaiinHost) {
            mProfile.setYuhaiinHost(newValue.toString());
            resetTextN(mPrefYuhaiinHost, newValue);
            return true;
        }

        return false;
    }

    @Override
    public void onCheckedChanged(CompoundButton p1, boolean checked) {
        if (checked) {
            startVpn();
        } else {
            try {
                stopVpn();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void initPreferences() {

        mPrefProfile = findPreference(PREF_PROFILE);
        mPrefYuhaiinHost = findPreference(PREF_YUHAIIN_HOST);
        mPrefHttpServerPort = findPreference(PREF_HTTP_SERVER_PORT);
        if (mPrefHttpServerPort != null)
            mPrefHttpServerPort.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL));
        mPrefSocks5ServerPort = findPreference(PREF_SOCKS5_SERVER_PORT);
        if (mPrefSocks5ServerPort != null)
            mPrefSocks5ServerPort.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL));
        mPrefUserpw = findPreference(PREF_AUTH_USERPW);
        mPrefUsername = findPreference(PREF_AUTH_USERNAME);
        mPrefPassword = findPreference(PREF_AUTH_PASSWORD);
        mPrefRoutes = findPreference(PREF_ADV_ROUTE);
        mPrefFakeDnsCidr = findPreference(PREF_ADV_FAKE_DNS_CIDR);
        mPrefDnsPort = findPreference(PREF_ADV_DNS_PORT);
        if (mPrefDnsPort != null)
            mPrefDnsPort.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL));
        mPrefPerApp = findPreference(PREF_ADV_PER_APP);
        mPrefAppBypass = findPreference(PREF_ADV_APP_BYPASS);
        mPrefAppList = findPreference(PREF_ADV_APP_LIST);
        mPrefIPv6 = findPreference(PREF_IPV6_PROXY);
        mPrefAuto = findPreference(PREF_ADV_AUTO_CONNECT);

        mPrefProfile.setOnPreferenceChangeListener(this);
        mPrefYuhaiinHost.setOnPreferenceChangeListener(this);
        mPrefHttpServerPort.setOnPreferenceChangeListener(this);
        mPrefSocks5ServerPort.setOnPreferenceChangeListener(this);
        mPrefUserpw.setOnPreferenceChangeListener(this);
        mPrefUsername.setOnPreferenceChangeListener(this);
        mPrefPassword.setOnPreferenceChangeListener(this);
        mPrefRoutes.setOnPreferenceChangeListener(this);
        mPrefFakeDnsCidr.setOnPreferenceChangeListener(this);
        mPrefDnsPort.setOnPreferenceChangeListener(this);
        mPrefPerApp.setOnPreferenceChangeListener(this);
        mPrefAppBypass.setOnPreferenceChangeListener(this);
        mPrefAppList.setOnPreferenceChangeListener(this);
        mPrefIPv6.setOnPreferenceChangeListener(this);
        mPrefAuto.setOnPreferenceChangeListener(this);
    }

    private void reload() {
        if (mProfile == null) {
            mProfile = mManager.getDefault();
        }

        mPrefProfile.setEntries(mManager.getProfiles());
        mPrefProfile.setEntryValues(mManager.getProfiles());
        mPrefProfile.setValue(mProfile.getName());
        mPrefRoutes.setValue(mProfile.getRoute());
        resetList(mPrefProfile, mPrefRoutes);

        mPrefUserpw.setChecked(mProfile.isUserPw());
        mPrefPerApp.setChecked(mProfile.isPerApp());
        mPrefAppBypass.setChecked(mProfile.isBypassApp());
        mPrefIPv6.setChecked(mProfile.hasIPv6());
        mPrefAuto.setChecked(mProfile.autoConnect());

        mPrefHttpServerPort.setText(String.valueOf(mProfile.getHttpServerPort()));
        mPrefSocks5ServerPort.setText(String.valueOf(mProfile.getSocks5ServerPort()));
        mPrefUsername.setText(mProfile.getUsername());
        mPrefPassword.setText(mProfile.getPassword());
        mPrefFakeDnsCidr.setText(mProfile.getFakeDnsCidr());
        mPrefDnsPort.setText(String.valueOf(mProfile.getDnsPort()));
        mPrefYuhaiinHost.setText(mProfile.getYuhaiinHost());

        resetText(mPrefHttpServerPort, mPrefSocks5ServerPort, mPrefUsername, mPrefPassword, mPrefFakeDnsCidr, mPrefDnsPort, mPrefYuhaiinHost);

        updateAppList();
    }

    private void updateAppList() {
        Set<String> selectedApps = mProfile.getAppList();
        Set<String> selectedAndExistsApps = new TreeSet<>();

        Map<String, String> packages = getPackages();
        List<CharSequence> titles = new ArrayList<>();
        List<CharSequence> packageNames = new ArrayList<>();

        for (Map.Entry<String, String> entry : packages.entrySet()) {
            if (selectedApps.contains(entry.getValue())) {
                titles.add(entry.getKey());
                selectedAndExistsApps.add(entry.getValue());
                packageNames.add(entry.getValue());
            }
        }

        for (Map.Entry<String, String> entry : packages.entrySet()) {
            if (!selectedApps.contains(entry.getValue())) {
                titles.add(entry.getKey());
                packageNames.add(entry.getValue());
            }
        }

        mPrefAppList.setEntries(titles.toArray(new CharSequence[0]));
        mPrefAppList.setEntryValues(packageNames.toArray(new CharSequence[0]));
        mProfile.setAppList(selectedAndExistsApps);
    }

    private Map<String, String> getPackages() {
        Map<String, String> packages = new TreeMap<>();
        List<PackageInfo> packageInfos = context.getPackageManager().getInstalledPackages(0);

        for (PackageInfo info : packageInfos) {
            String appName = info.applicationInfo.loadLabel(context.getPackageManager()).toString();
            String packageName = info.packageName;
            packages.put(appName + "\n" + packageName, packageName);
        }

        return packages;
    }

    private void resetList(ListPreference... pref) {
        for (ListPreference p : pref)
            p.setSummary(p.getEntry());
    }

    private void resetListN(ListPreference pref, Object newValue) {
        pref.setSummary(newValue.toString());
    }

    private void resetText(EditTextPreference... pref) {
        for (EditTextPreference p : pref) {
            if (!Objects.equals(p.getKey(), "auth_password")) {
                p.setSummary(p.getText());
            } else {
                if (Objects.requireNonNull(p.getText()).length() > 0)
                    p.setSummary(String.format(Locale.US, String.format(Locale.US, "%%0%dd", p.getText().length()), 0).replace("0", "*"));
                else p.setSummary("");
            }
        }
    }

    private void resetTextN(EditTextPreference pref, Object newValue) {
        if (!Objects.equals(pref.getKey(), "auth_password")) {
            pref.setSummary(newValue.toString());
        } else {
            String text = newValue.toString();
            if (text.length() > 0)
                pref.setSummary(String.format(Locale.US, String.format(Locale.US, "%%0%dd", text.length()), 0).replace("0", "*"));
            else pref.setSummary("");
        }
    }

    private void addProfile() {
        final EditText e = new EditText(getActivity());
        e.setSingleLine(true);


        new AlertDialog.
                Builder(requireActivity()).
                setTitle(R.string.prof_add).
                setView(e).setPositiveButton(android.R.string.ok, (d, which) -> {
                    String name = e.getText().toString().trim();

                    if (!TextUtils.isEmpty(name)) {
                        Profile p = mManager.addProfile(name);

                        if (p != null) {
                            mProfile = p;
                            reload();
                            return;
                        }
                    }

                    Toast.makeText(getActivity(), String.format(getString(R.string.err_add_prof), name), Toast.LENGTH_SHORT).show();
                }).setNegativeButton(android.R.string.cancel, (d, which) -> {

                }).create().show();
    }

    private void removeProfile() {
        new AlertDialog.Builder(requireActivity()).setTitle(R.string.prof_del).setMessage(String.format(getString(R.string.prof_del_confirm), mProfile.getName())).setPositiveButton(android.R.string.ok, (d, which) -> {
            if (!mManager.removeProfile(mProfile.getName())) {
                Toast.makeText(getActivity(), getString(R.string.err_del_prof, mProfile.getName()), Toast.LENGTH_SHORT).show();
            } else {
                mProfile = mManager.getDefault();
                reload();
            }
        }).setNegativeButton(android.R.string.cancel, (d, which) -> {
        }).create().show();
    }


    private void startVpn() {
        Intent i = VpnService.prepare(requireContext());

        if (i != null) {
            startVpnLauncher.launch(i);
//            startActivityForResult(i, 0);
        } else {
            Utility.startVpn(requireContext(), mProfile);
        }
    }


    private void stopVpn() throws RemoteException {
        if (mBinder == null) return;
        mBinder.stop();
    }


}