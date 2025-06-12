package com.simplexray.an

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplexray.an.TProxyService.Companion.getNativeLibraryDir
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class SettingsFragment : PreferenceFragmentCompat(), MenuProvider {
    private lateinit var prefs: Preferences
    private lateinit var configActionListener: OnConfigActionListener
    private lateinit var geoipFilePickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var geositeFilePickerLauncher: ActivityResultLauncher<Array<String>>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnConfigActionListener) {
            configActionListener = context
        } else {
            throw RuntimeException(
                "$context must implement OnConfigActionListener"
            )
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        prefs = Preferences(requireContext())

        geoipFilePickerLauncher =
            registerForActivityResult<Array<String>, Uri>(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    importRuleFile(uri, "geoip.dat")
                } else {
                    Log.d(
                        "SettingsFragment", "Geoip file picking cancelled or failed (URI is null)."
                    )
                }
            }

        geositeFilePickerLauncher =
            registerForActivityResult<Array<String>, Uri>(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    importRuleFile(uri, "geosite.dat")
                } else {
                    Log.d(
                        "SettingsFragment",
                        "Geosite file picking cancelled or failed (URI is null)."
                    )
                }
            }

        findPreference<Preference>("apps")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(activity, AppListActivity::class.java))
                true
            }
        findPreference<Preference>("geoip")?.let { geoip ->
            geoip.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                geoipFilePickerLauncher.launch(arrayOf("*/*"))
                true
            }
        }
        findPreference<Preference>("geosite")?.let { geosite ->
            geosite.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                geositeFilePickerLauncher.launch(arrayOf("*/*"))
                true
            }
        }
        findPreference<Preference>("clear_files")?.let { clearFiles ->
            clearFiles.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rule_file_restore_default_summary)
                    .setMessage(R.string.rule_file_restore_default_message)
                    .setPositiveButton(R.string.confirm) { dialog: DialogInterface, _: Int ->
                        restoreDefaultRuleFile("geoip.dat")
                        restoreDefaultRuleFile("geosite.dat")
                        Toast.makeText(
                            requireContext(),
                            R.string.rule_file_restore_default_success,
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    .show()
                true
            }
        }
        val source = findPreference<Preference>("source")
        if (source != null) {
            source.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val browserIntent =
                    Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.source_url)))
                startActivity(browserIntent)
                true
            }
        }
        val version = findPreference<Preference>("version")
        if (version != null) {
            val packageManager = requireContext().packageManager
            val packageName = requireContext().packageName
            val packageInfo: PackageInfo
            try {
                packageInfo = packageManager.getPackageInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException(e)
            }
            val versionName = packageInfo.versionName
            version.summary = versionName
        }
        val kernel = findPreference<Preference>("kernel")
        if (kernel != null) {
            val libraryDir = getNativeLibraryDir(requireContext())
            val xrayPath = "$libraryDir/libxray.so"
            try {
                val process = Runtime.getRuntime().exec("$xrayPath -version")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val firstLine = reader.readLine()
                process.destroy()
                kernel.summary = firstLine
            } catch (e: IOException) {
                Log.e("SettingsFragment", "Failed to get xray version", e)
                throw RuntimeException(e)
            }
        }
        val socksPortPreference = findPreference<EditTextPreference>("SocksPort")
        val dnsIpv4Preference = findPreference<EditTextPreference>("DnsIpv4")
        val dnsIpv6Preference = findPreference<EditTextPreference>("DnsIpv6")
        val ipv6Preference = findPreference<CheckBoxPreference>("Ipv6")
        if (socksPortPreference != null) {
            socksPortPreference.text = prefs.socksPort.toString()
            socksPortPreference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    val stringValue = newValue as String
                    try {
                        val port = stringValue.toInt()
                        if (port in 1025..65535) {
                            prefs.socksPort = port
                            return@OnPreferenceChangeListener true
                        } else {
                            MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.invalid_port_range)
                                .setPositiveButton(R.string.confirm, null).show()
                            return@OnPreferenceChangeListener false
                        }
                    } catch (e: NumberFormatException) {
                        MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.invalid_port)
                            .setPositiveButton(R.string.confirm, null).show()
                        return@OnPreferenceChangeListener false
                    }
                }
        }
        if (dnsIpv4Preference != null) {
            dnsIpv4Preference.text = prefs.dnsIpv4
            dnsIpv4Preference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                    val stringValue = newValue as String?
                    val matcher = stringValue?.let { IPV4_PATTERN.matcher(it) }
                    if (matcher?.matches() == true) {
                        prefs.dnsIpv4 = stringValue
                        return@OnPreferenceChangeListener true
                    } else {
                        MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.invalid_ipv4)
                            .setPositiveButton(R.string.confirm, null).show()
                        return@OnPreferenceChangeListener false
                    }
                }
        }
        if (dnsIpv6Preference != null) {
            dnsIpv6Preference.text = prefs.dnsIpv6
        }
        if (ipv6Preference != null) {
            if (dnsIpv6Preference != null) {
                dnsIpv6Preference.isEnabled = ipv6Preference.isChecked
            }
            ipv6Preference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    val isChecked = newValue as Boolean
                    if (dnsIpv6Preference != null) {
                        dnsIpv6Preference.isEnabled = isChecked
                    }
                    prefs.ipv6 = isChecked
                    true
                }
        }
        if (dnsIpv6Preference != null) {
            dnsIpv6Preference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                    val stringValue = newValue as String?
                    val matcher = stringValue?.let { IPV6_PATTERN.matcher(it) }
                    if (matcher?.matches() == true) {
                        prefs.dnsIpv6 = stringValue
                        return@OnPreferenceChangeListener true
                    } else {
                        MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.invalid_ipv6)
                            .setPositiveButton(R.string.confirm, null).show()
                        return@OnPreferenceChangeListener false
                    }
                }
        }

        refreshPreferences()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    fun refreshPreferences() {
        findPreference<EditTextPreference>("SocksPort")?.text = prefs.socksPort.toString()
        findPreference<EditTextPreference>("DnsIpv4")?.text = prefs.dnsIpv4
        val dnsIpv6Preference = findPreference<EditTextPreference>("DnsIpv6")
        if (dnsIpv6Preference != null) {
            dnsIpv6Preference.text = prefs.dnsIpv6
            val ipv6Preference = findPreference<CheckBoxPreference>("Ipv6")
            if (ipv6Preference != null) {
                dnsIpv6Preference.isEnabled = ipv6Preference.isChecked
            }
        }
        val ipv6Preference = findPreference<CheckBoxPreference>("Ipv6")
        if (ipv6Preference != null) {
            ipv6Preference.isChecked = prefs.ipv6
        }
        val bypassLanPreference = findPreference<CheckBoxPreference>("BypassLan")
        if (bypassLanPreference != null) {
            bypassLanPreference.isChecked = prefs.bypassLan
        }
        val useTemplatePreference = findPreference<CheckBoxPreference>("UseTemplate")
        if (useTemplatePreference != null) {
            useTemplatePreference.isChecked = prefs.useTemplate
        }
        val httpProxyEnabledPreference = findPreference<CheckBoxPreference>("HttpProxyEnabled")
        if (httpProxyEnabledPreference != null) {
            httpProxyEnabledPreference.isChecked = prefs.httpProxyEnabled
        }

        val geoip = findPreference<Preference>("geoip")
        var isGeoipCustom = false
        if (geoip != null) {
            if (prefs.customGeoipImported) {
                val geoipFile = File(requireContext().filesDir, "geoip.dat")
                if (geoipFile.exists()) {
                    val lastModified = geoipFile.lastModified()
                    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                    geoip.summary =
                        getString(R.string.rule_file_imported_prefix) + " " + sdf.format(
                            Date(
                                lastModified
                            )
                        )
                    isGeoipCustom = true
                } else {
                    geoip.setSummary(R.string.rule_file_missing_error)
                    prefs.customGeoipImported = false
                    Log.e("SettingsFragment", "Custom geoip file expected but not found.")
                }
            } else {
                geoip.setSummary(R.string.rule_file_default)
            }
        }

        val geosite = findPreference<Preference>("geosite")
        var isGeositeCustom = false
        if (geosite != null) {
            if (prefs.customGeositeImported) {
                val geositeFile = File(requireContext().filesDir, "geosite.dat")
                if (geositeFile.exists()) {
                    val lastModified = geositeFile.lastModified()
                    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                    geosite.summary =
                        getString(R.string.rule_file_imported_prefix) + " " + sdf.format(
                            Date(
                                lastModified
                            )
                        )
                    isGeositeCustom = true
                } else {
                    geosite.setSummary(R.string.rule_file_missing_error)
                    prefs.customGeositeImported = false
                    Log.e("SettingsFragment", "Custom geosite file expected but not found.")
                }
            } else {
                geosite.setSummary(R.string.rule_file_default)
            }
        }

        val clearFiles = findPreference<Preference>("clear_files")
        if (clearFiles != null) {
            clearFiles.isEnabled = isGeoipCustom || isGeositeCustom
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        Log.d("SettingsFragment", "onCreateMenu")
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.menu_backup -> {
                configActionListener.performBackup()
                true
            }

            R.id.menu_restore -> {
                configActionListener.performRestore()
                true
            }

            else -> false
        }
    }

    private fun importRuleFile(uri: Uri, filename: String) {
        (configActionListener as? MainActivity)?.executorService?.submit {
            val targetFile = File(requireContext().filesDir, filename)
            try {
                requireContext().contentResolver.openInputStream(uri).use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        if (inputStream == null) {
                            throw IOException("Failed to open input stream for URI: $uri")
                        }
                        val buffer = ByteArray(1024)
                        var read: Int
                        while ((inputStream.read(buffer).also { read = it }) != -1) {
                            outputStream.write(buffer, 0, read)
                        }

                        requireActivity().runOnUiThread {
                            if ("geoip.dat" == filename) {
                                prefs.customGeoipImported = true
                            } else if ("geosite.dat" == filename) {
                                prefs.customGeositeImported = true
                            }
                            refreshPreferences()
                            Toast.makeText(
                                requireContext(),
                                "$filename ${getString(R.string.rule_file_import_success_suffix)}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        Log.d(
                            "SettingsFragment", "Successfully imported $filename from URI: $uri"
                        )
                    }
                }
            } catch (e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.rule_file_import_failed_prefix)} $filename $e.message",
                        Toast.LENGTH_LONG
                    ).show()
                    if ("geoip.dat" == filename) {
                        prefs.customGeoipImported = false
                    } else if ("geosite.dat" == filename) {
                        prefs.customGeositeImported = false
                    }
                    refreshPreferences()
                }
                Log.e("SettingsFragment", "Error importing rule file: $filename", e)
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.rule_file_import_failed_prefix)} $filename $e.message",
                        Toast.LENGTH_LONG
                    ).show()
                    if ("geoip.dat" == filename) {
                        prefs.customGeoipImported = false
                    } else if ("geosite.dat" == filename) {
                        prefs.customGeositeImported = false
                    }
                    refreshPreferences()
                }
                Log.e(
                    "SettingsFragment", "Unexpected error during rule file import: $filename", e
                )
            }
        }
    }

    private fun restoreDefaultRuleFile(filename: String) {
        val targetFile = File(requireContext().filesDir, filename)
        val deleted = targetFile.delete()

        if ("geoip.dat" == filename) {
            prefs.customGeoipImported = false
        } else if ("geosite.dat" == filename) {
            prefs.customGeositeImported = false
        }

        configActionListener.triggerAssetExtraction()

        refreshPreferences()
        Log.d("SettingsFragment", "Restored default for $filename. File deleted: $deleted")
    }

    companion object {
        private const val IPV4_REGEX =
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        private val IPV4_PATTERN: Pattern = Pattern.compile(IPV4_REGEX)
        private const val IPV6_REGEX =
            "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80::(fe80(:[0-9a-fA-F]{0,4})?){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d)|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?\\d)?\\d)\\.){3}(25[0-5]|(2[0-4]|1?\\d)?\\d))$"
        private val IPV6_PATTERN: Pattern = Pattern.compile(IPV6_REGEX)
    }
}