package net.typeblog.socks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageInfo;
import android.net.VpnService;
import android.os.*;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.*;
import net.typeblog.socks.util.Constants;
import net.typeblog.socks.util.Profile;
import net.typeblog.socks.util.ProfileManager;
import net.typeblog.socks.util.Utility;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static net.typeblog.socks.util.Constants.*;

public class ProfileFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener,
        CompoundButton.OnCheckedChangeListener {
    private ProfileManager mManager;
    private Profile mProfile;

    private SwitchCompat mSwitch;
    private boolean mRunning = false;
    private boolean mStarting = false, mStopping = false;
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName p1, IBinder binder) {
            mBinder = IVpnService.Stub.asInterface(binder);

            try {
                mRunning = mBinder.isRunning();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (mRunning) {
                updateState();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName p1) {
            mBinder = null;
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        if (mSwitch != null) {
            try {
                checkState();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        IntentFilter f = new IntentFilter();
        f.addAction(INTENT_DISCONNECTED);
        f.addAction(INTENT_CONNECTED);

        context.registerReceiver(bReceiver,f);
    }

    private final BroadcastReceiver bReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), Constants.INTENT_DISCONNECTED)) {
                mSwitch.setOnCheckedChangeListener(null);
                mRunning = false;
                mStarting = false;
                mStopping = false;
                updateState();
            } else if (Objects.equals(intent.getAction(), INTENT_CONNECTED)) {
                mSwitch.setOnCheckedChangeListener(null);
                mRunning = true;
                mStarting = false;
                mStopping = false;
                updateState();
            }
        }
    };

    private final Runnable mStateRunnable = new Runnable() {
        @Override
        public void run() {
            updateState();
            mSwitch.postDelayed(this, 1000);
        }
    };
    private IVpnService mBinder;

    private DropDownPreference mPrefProfile;
    private DropDownPreference mPrefRoutes;
    private EditTextPreference mPrefHttpServerPort;
    private EditTextPreference mPrefSocks5ServerPort;
    private EditTextPreference mPrefUsername;
    private EditTextPreference mPrefPassword;
    private EditTextPreference mPrefFakeDnsCidr;
    private EditTextPreference mPrefDnsPort;
    private MultiSelectListPreference mPrefAppList;
//    private EditTextPreference mPrefUDPGW;mPrefUDP
    private CheckBoxPreference mPrefUserpw, mPrefPerApp, mPrefAppBypass, mPrefIPv6, mPrefAuto;

    private  EditTextPreference mPrefYuhaiinHost;
    private Context context;

    public void setContext(Context context) {
        this.context = context;
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
    public void onCreatePreferences(@org.jetbrains.annotations.Nullable Bundle savedInstanceState, @org.jetbrains.annotations.Nullable String rootKey) {}

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, @NotNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.main, menu);
        MenuItem s = menu.findItem(R.id.switch_main);
        mSwitch = (SwitchCompat) s.getActionView();
        mSwitch.setOnCheckedChangeListener(this);
        try {
            checkState();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
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

    @Override
    public boolean onPreferenceChange(@NotNull Preference p, Object newValue) {
        if (p == mPrefProfile) {
            String name = newValue.toString();
            mProfile = mManager.getProfile(name);
            mManager.switchDefault(name);
            reload();
            return true;
        } else if (p == mPrefHttpServerPort) {
            if (TextUtils.isEmpty(newValue.toString()))
                return false;
            mProfile.setHttpServerPort(Integer.parseInt(newValue.toString()));
            resetTextN(mPrefHttpServerPort, newValue);
            return true;
        } else if (p == mPrefSocks5ServerPort) {
            if (TextUtils.isEmpty(newValue.toString()))
                return false;

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
            if (TextUtils.isEmpty(newValue.toString()))
                return false;

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
            List<String> newValues = new ArrayList<>((HashSet<String>) newValue);
            String appList = TextUtils.join("\n", newValues);
            mProfile.setAppList(appList);
            updateAppList();
            Log.d("setAppList", "appList:\n" + mProfile.getAppList());
            return true;
        } else if (p == mPrefIPv6) {
            mProfile.setHasIPv6(Boolean.parseBoolean(newValue.toString()));
            return true;
//        } else if (p == mPrefUDP) {
//            mProfile.setHasUDP(Boolean.parseBoolean(newValue.toString()));
//            return true;
//        } else if (p == mPrefUDPGW) {
//            mProfile.setUDPGW(newValue.toString());
//            resetTextN(mPrefUDPGW, newValue);
//            return true;
        } else if (p == mPrefAuto) {
            mProfile.setAutoConnect(Boolean.parseBoolean(newValue.toString()));
            return true;
        }else if (p == mPrefYuhaiinHost){
            mProfile.setYuhaiinHost(newValue.toString());
            resetTextN(mPrefYuhaiinHost,newValue);
            return true;
        } else {
            return false;
        }
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

    class StartVpnCallback extends Handler {
        StartVpnCallback(Looper lp) {
          super(lp);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle data = msg.getData();
            if (data != null) {
                boolean success = data.getBoolean("success");
                if (success) {
                    try {
                        checkState();
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                }

                String toast = data.getString("toast");
                if (toast != null) {
                    Toast.makeText(requireActivity().getApplicationContext(), toast, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void initPreferences() {
        mPrefProfile = findPreference(PREF_PROFILE);
        mPrefHttpServerPort = findPreference(PREF_HTTP_SERVER_PORT);
        mPrefSocks5ServerPort = findPreference(PREF_SOCKS5_SERVER_PORT);
        mPrefUserpw = findPreference(PREF_AUTH_USERPW);
        mPrefUsername = findPreference(PREF_AUTH_USERNAME);
        mPrefPassword = findPreference(PREF_AUTH_PASSWORD);
        mPrefRoutes = findPreference(PREF_ADV_ROUTE);
        mPrefFakeDnsCidr = findPreference(PREF_ADV_FAKE_DNS_CIDR);
        mPrefDnsPort = findPreference(PREF_ADV_DNS_PORT);
        mPrefPerApp = findPreference(PREF_ADV_PER_APP);
        mPrefAppBypass = findPreference(PREF_ADV_APP_BYPASS);
        mPrefAppList = findPreference(PREF_ADV_APP_LIST);
        mPrefIPv6 = findPreference(PREF_IPV6_PROXY);
//        mPrefUDP = findPreference(PREF_UDP_PROXY);
//        mPrefUDPGW = findPreference(PREF_UDP_GW);
        mPrefAuto = findPreference(PREF_ADV_AUTO_CONNECT);

        mPrefYuhaiinHost =  findPreference(PREF_YUHAIIN_HOST);

        mPrefProfile.setOnPreferenceChangeListener(this);
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
//        mPrefUDP.setOnPreferenceChangeListener(this);
//        mPrefUDPGW.setOnPreferenceChangeListener(this);
        mPrefAuto.setOnPreferenceChangeListener(this);

        mPrefYuhaiinHost.setOnPreferenceChangeListener(this);
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
//        mPrefUDP.setChecked(mProfile.hasUDP());
        mPrefAuto.setChecked(mProfile.autoConnect());

        mPrefHttpServerPort.setText(String.valueOf(mProfile.getHttpServerPort()));
        mPrefSocks5ServerPort.setText(String.valueOf(mProfile.getSocks5ServerPort()));
        mPrefUsername.setText(mProfile.getUsername());
        mPrefPassword.setText(mProfile.getPassword());
        mPrefFakeDnsCidr.setText(mProfile.getFakeDnsCidr());
        mPrefDnsPort.setText(String.valueOf(mProfile.getDnsPort()));
//        mPrefUDPGW.setText(mProfile.getUDPGW());

        mPrefYuhaiinHost.setText(mProfile.getYuhaiinHost());

        resetText(mPrefHttpServerPort, mPrefSocks5ServerPort,
                mPrefUsername, mPrefPassword, mPrefFakeDnsCidr, mPrefDnsPort,mPrefYuhaiinHost);//mPrefUDPGW

        updateAppList();
    }

    private void updateAppList() {
        HashSet<String> selectedApps = new HashSet<>(Arrays.asList(mProfile.getAppList().split("\n")));
        List<String> selectedAndExistsApps = new ArrayList<>();

        Map<String, String> packages = getPackages();
        CharSequence[] titles = new CharSequence[packages.size()];
        CharSequence[] packageNames = new CharSequence[packages.size()];

        //--------------- 给应用列表排序 ---------------
        int i = 0;

        // 首先添加选中的应用，这样选中的应用就会排在前面
        for (Map.Entry<String, String> entry : packages.entrySet()) {
            if (selectedApps.contains(entry.getValue())) {
                selectedAndExistsApps.add(entry.getValue());
                packageNames[i] = entry.getValue();
                titles[i] = entry.getKey();
                i++;
            }
        }
        // 接下来添加未选中的应用
        for (Map.Entry<String, String> entry : packages.entrySet()) {
            if (!selectedApps.contains(entry.getValue())) {
                packageNames[i] = entry.getValue();
                titles[i] = entry.getKey();
                i++;
            }
        }

        mPrefAppList.setEntries(titles);
        mPrefAppList.setEntryValues(packageNames);

        // 更新存储的AppList（删掉了不存在的应用）
        mProfile.setAppList(TextUtils.join("\n", selectedAndExistsApps));
    }

    private Map<String, String> getPackages() {
        Map<String, String> packages = new TreeMap<String, String>();
        try {
            String myself = context.getApplicationInfo().packageName;
            List<PackageInfo> packageInfos = context.getPackageManager().getInstalledPackages(0);

            // 统计是否重名
            Map<String, Integer> nameCount = new HashMap<String, Integer>();
            for (PackageInfo info : packageInfos) {
                String appName = info.applicationInfo.loadLabel(context.getPackageManager()).toString();
                if (nameCount.containsKey(appName)) {
                    nameCount.put(appName, nameCount.get(appName) + 1);
                } else {
                    nameCount.put(appName, 1);
                }
            }

            for (PackageInfo info : packageInfos) {
                String appName = info.applicationInfo.loadLabel(context.getPackageManager()).toString();
                String packageName = info.packageName;
//                if (!myself.equals(packageName)) {
                    // 重名自动加包名做为后缀
                    if (nameCount.get(appName) > 1) {
                        appName = appName + " (" + packageName + ")";
                    }
                    packages.put(appName, packageName);
//                }
            }
        } catch (Throwable t) {
            t.printStackTrace();;
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
                    p.setSummary(String.format(Locale.US,
                            String.format(Locale.US, "%%0%dd", p.getText().length()), 0)
                            .replace("0", "*"));
                else
                    p.setSummary("");
            }
        }
    }

    private void resetTextN(EditTextPreference pref, Object newValue) {
        if (!Objects.equals(pref.getKey(), "auth_password")) {
            pref.setSummary(newValue.toString());
        } else {
            String text = newValue.toString();
            if (text.length() > 0)
                pref.setSummary(String.format(Locale.US,
                        String.format(Locale.US, "%%0%dd", text.length()), 0)
                        .replace("0", "*"));
            else
                pref.setSummary("");
        }
    }

    private void addProfile() {
        final EditText e = new EditText(getActivity());
        e.setSingleLine(true);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.prof_add)
                .setView(e)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    String name = e.getText().toString().trim();

                    if (!TextUtils.isEmpty(name)) {
                        Profile p = mManager.addProfile(name);

                        if (p != null) {
                            mProfile = p;
                            reload();
                            return;
                        }
                    }

                    Toast.makeText(getActivity(),
                            String.format(getString(R.string.err_add_prof), name),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> {

                })
                .create().show();
    }

    private void removeProfile() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.prof_del)
                .setMessage(String.format(getString(R.string.prof_del_confirm), mProfile.getName()))
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    if (!mManager.removeProfile(mProfile.getName())) {
                        Toast.makeText(getActivity(),
                                getString(R.string.err_del_prof, mProfile.getName()),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        mProfile = mManager.getDefault();
                        reload();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> {})
                .create().show();
    }

    private void checkState() throws RemoteException {
        mRunning = false;
        Log.d("m switch","set dis enabled");
        mSwitch.setEnabled(false);
        mSwitch.setOnCheckedChangeListener(null);

        if (mBinder == null) {
            requireActivity().bindService(new Intent(getActivity(), SocksVpnService.class), mConnection, 0);
            mSwitch.postDelayed(mStateRunnable,1000);
        }else{
            if (mBinder.isRunning()) updateState();
        }
    }

    private void updateState() {
        if (mBinder == null) {
            mRunning = false;
        } else {
            try {
                mRunning = mBinder.isRunning();
            } catch (Exception e) {
                mRunning = false;
            }
        }

        mSwitch.setChecked(mRunning);

        if ((!mStarting && !mStopping) || (mStarting && mRunning) || (mStopping && !mRunning)) {
            Log.d("m switch","set enabled");
            mSwitch.setEnabled(true);
        }

        if (mStarting && mRunning) {
            mStarting = false;
        }

        if (mStopping && !mRunning) {
            mStopping = false;
        }

        mSwitch.setOnCheckedChangeListener(ProfileFragment.this);
    }

//    private final ActivityResultLauncher<Intent> startRegister =  registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
//        if (result.getResultCode() == Activity.RESULT_OK) {
//            Utility.startVpn(getActivity(), mProfile, new StartVpnCallback(Looper.getMainLooper()));
//        }
//    });

    private void startVpn() {
        mStarting = true;
        Intent i = VpnService.prepare(getActivity());

        if (i != null) {
//           startRegister.launch(VpnService.prepare(getActivity()));
            startActivityForResult(i, 0);
        } else {
            onActivityResult(0, Activity.RESULT_OK, null);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            Utility.startVpn(getActivity(), mProfile, new StartVpnCallback(Looper.getMainLooper()));
            try {
                checkState();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void stopVpn() throws RemoteException {
        if (mBinder == null)
            return;

        mStopping = true;

        try {
            mBinder.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mBinder = null;

        requireActivity().unbindService(mConnection);
        checkState();
    }
}
