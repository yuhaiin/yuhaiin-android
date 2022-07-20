package io.github.asutorufa.yuhaiin

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.takisoft.preferencex.SimpleMenuPreference
import io.github.asutorufa.yuhaiin.database.DNS
import io.github.asutorufa.yuhaiin.database.Manager.profile
import io.github.asutorufa.yuhaiin.database.Manager.setOnPreferenceChangeListener


class DnsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.dns, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.preferenceDataStore = (activity as MainActivity).dataStore

        findPreference<EditTextPreference>(resources.getString(R.string.adv_dns_port_key))!!.apply {
            text = profile.dnsPort.toString()
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.dnsPort = newValue.toString().toInt()
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.adv_fake_dns_cidr_key))!!.apply {
            text = profile.fakeDnsCidr
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.fakeDnsCidr = newValue as String
            }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.dns_hijacking))!!.apply {
            isChecked = profile.dnsHijacking
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.dnsHijacking = newValue as Boolean
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.remote_dns_host_key))!!.apply {
            text = profile.remoteDns.host
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.remoteDns.host = newValue as String
            }
        }

        findPreference<SimpleMenuPreference>(resources.getString(R.string.remote_dns_type_key))!!.apply {
            value = dnsTypeToStr(DNS.Type.fromInt(profile.remoteDns.type))
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.remoteDns.type = strToDNSType(newValue as String).value
            }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.remote_dns_proxy_key))!!.apply {
            isChecked = profile.remoteDns.proxy
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.remoteDns.proxy = newValue as Boolean
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

        // localdns
        findPreference<EditTextPreference>(resources.getString(R.string.local_dns_host_key))!!.apply {
            text = profile.localDns.host
            setOnPreferenceChangeListener(this) { _, newValue ->
                profile.localDns.host = newValue as String
            }
        }
        findPreference<SimpleMenuPreference>(resources.getString(R.string.local_dns_type_key))!!.apply {
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
        findPreference<SimpleMenuPreference>(resources.getString(R.string.bootstrap_dns_type_key))!!.apply {
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