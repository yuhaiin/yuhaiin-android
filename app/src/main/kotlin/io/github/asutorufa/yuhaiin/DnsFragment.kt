package io.github.asutorufa.yuhaiin

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.transition.platform.MaterialSharedAxis
import io.github.asutorufa.yuhaiin.database.DNS
import io.github.asutorufa.yuhaiin.MainApplication.Companion.profile
import io.github.asutorufa.yuhaiin.MainApplication.Companion.setOnPreferenceChangeListener


class DnsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) =
        setPreferencesFromResource(R.xml.dns, rootKey)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.transitionName = "transition_common"
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true).apply {
            duration = 500L
        }
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false).apply {
            duration = 500L
        }
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)

        preferenceManager.preferenceDataStore = (activity as MainActivity).dataStore

        findPreference<EditTextPreference>(resources.getString(R.string.adv_dns_port_key))!!.apply {
            text = profile.dnsPort.toString()
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.dnsPort = newValue.toString().toInt()
            }
        }

        findPreference<Preference>(resources.getString(R.string.dns_hosts_key))!!.apply {
            setOnPreferenceClickListener {
                findNavController().navigate(DnsFragmentDirections.actionDnsFragmentToHostsListFragment())
                true
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.adv_fake_dns_cidr_key))!!.apply {
            text = profile.fakeDnsCidr
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.fakeDnsCidr = newValue as String
            }
        }
        findPreference<EditTextPreference>(resources.getString(R.string.adv_fake_dnsv6_cidr_key))!!.apply {
            text = profile.fakeDnsv6Cidr
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.fakeDnsv6Cidr = newValue as String
            }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.dns_hijacking))!!.apply {
            isChecked = profile.dnsHijacking
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.dnsHijacking = newValue as Boolean
            }
        }

        // remote dns
        findPreference<EditTextPreference>(resources.getString(R.string.remote_dns_host_key))!!.apply {
            text = profile.remoteDns.host
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.remoteDns.host = newValue as String
            }
        }

        findPreference<ListPreference>(resources.getString(R.string.remote_dns_type_key))!!.apply {
            value = dnsTypeToStr(DNS.Type.fromInt(profile.remoteDns.type))
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.remoteDns.type = strToDNSType(newValue as String).value
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.remote_dns_subnet_key))!!.apply {
            text = profile.remoteDns.subnet
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.remoteDns.subnet = newValue as String
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.remote_dns_tls_server_name_key))!!.apply {
            text = profile.remoteDns.tlsServerName
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.remoteDns.tlsServerName = newValue as String
            }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.remote_dns_resolve_domain_key))!!.apply {
            isChecked = profile.resolveRemoteDomain
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.resolveRemoteDomain = newValue as Boolean
            }
        }

        // localdns
        findPreference<EditTextPreference>(resources.getString(R.string.local_dns_host_key))!!.apply {
            text = profile.localDns.host
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.localDns.host = newValue as String
            }
        }
        findPreference<ListPreference>(resources.getString(R.string.local_dns_type_key))!!.apply {
            value = dnsTypeToStr(DNS.Type.fromInt(profile.localDns.type))
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.localDns.type = strToDNSType(newValue as String).value
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.local_dns_subnet_key))!!.apply {
            text = profile.localDns.subnet
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.localDns.subnet = newValue as String
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.local_dns_tls_server_name_key))!!.apply {
            text = profile.localDns.tlsServerName
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.localDns.tlsServerName = newValue as String
            }
        }


        // bootstrap
        findPreference<EditTextPreference>(resources.getString(R.string.bootstrap_dns_host_key))!!.apply {
            text = profile.bootstrapDns.host
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.bootstrapDns.host = newValue as String
            }
        }
        findPreference<ListPreference>(resources.getString(R.string.bootstrap_dns_type_key))!!.apply {
            value = dnsTypeToStr(DNS.Type.fromInt(profile.bootstrapDns.type))
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.bootstrapDns.type = strToDNSType(newValue as String).value
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.bootstrap_dns_subnet_key))!!.apply {
            text = profile.bootstrapDns.subnet
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.bootstrapDns.subnet = newValue as String
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.bootstrap_dns_tls_server_name_key))!!.apply {
            text = profile.bootstrapDns.tlsServerName
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.bootstrapDns.tlsServerName = newValue as String
            }
        }

    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference, is EditTextPreference, is MultiSelectListPreference ->
                showDialog(preference)

            else -> super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun strToDNSType(str: String): DNS.Type {
        return when (str) {
            resources.getString(R.string.dns_type_doudp) -> DNS.Type.UDP
            resources.getString(R.string.dns_type_dotcp) -> DNS.Type.TCP
            resources.getString(R.string.dns_type_doh) -> DNS.Type.HTTPS
            resources.getString(R.string.dns_type_dot) -> DNS.Type.TLS
            resources.getString(R.string.dns_type_doq) -> DNS.Type.QUIC
            resources.getString(R.string.dns_type_doh3) -> DNS.Type.HTTPS3
            else -> DNS.Type.Reserve
        }
    }

    private fun dnsTypeToStr(type: DNS.Type): String {
        return when (type) {
            DNS.Type.UDP -> resources.getString(R.string.dns_type_doudp)
            DNS.Type.TCP -> resources.getString(R.string.dns_type_dotcp)
            DNS.Type.HTTPS -> resources.getString(R.string.dns_type_doh)
            DNS.Type.TLS -> resources.getString(R.string.dns_type_dot)
            DNS.Type.QUIC -> resources.getString(R.string.dns_type_doq)
            DNS.Type.HTTPS3 -> resources.getString(R.string.dns_type_doh3)
            else -> resources.getString(R.string.dns_type_doudp)
        }
    }
}