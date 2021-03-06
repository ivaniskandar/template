@file:Suppress("ConstantConditionIf")

package substratum.theme.template

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.Toast
import com.github.javiersantos.piracychecker.PiracyChecker
import com.github.javiersantos.piracychecker.PiracyCheckerUtils
import com.github.javiersantos.piracychecker.enums.*
import substratum.theme.template.AdvancedConstants.ORGANIZATION_THEME_SYSTEMS
import substratum.theme.template.AdvancedConstants.OTHER_THEME_SYSTEMS
import substratum.theme.template.AdvancedConstants.SHOW_DIALOG_REPEATEDLY
import substratum.theme.template.AdvancedConstants.SHOW_LAUNCH_DIALOG
import substratum.theme.template.ThemeFunctions.checkApprovedSignature
import substratum.theme.template.ThemeFunctions.getSelfSignature
import substratum.theme.template.ThemeFunctions.getSelfVerifiedPirateTools
import substratum.theme.template.ThemeFunctions.isCallingPackageAllowed

/**
 * NOTE TO THEMERS
 *
 * This class is a TEMPLATE of how you should be launching themes. As long as you keep the structure
 * of launching themes the same, you can avoid easy script crackers by changing how
 * functions/methods are coded, as well as boolean variable placement.
 *
 * The more you play with this the harder it would be to decompile and crack!
 */

class SubstratumLauncher : Activity() {

    private val debug = false
    private val tag = "SubstratumThemeReport"
    private val substratumIntentData = "projekt.substratum.THEME"
    private val getKeysIntent = "projekt.substratum.GET_KEYS"
    private val receiveKeysIntent = "projekt.substratum.RECEIVE_KEYS"

    private lateinit var piracyChecker: PiracyChecker
    private val themePiracyCheck by lazy {
        if (BuildConfig.ENABLE_APP_BLACKLIST_CHECK) {
            getSelfVerifiedPirateTools(applicationContext)
        } else {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* STEP 1: Block hijackers */
        val caller = callingActivity!!.packageName
        val organizationsSystem = ORGANIZATION_THEME_SYSTEMS.contains(caller)
        val supportedSystem = organizationsSystem || OTHER_THEME_SYSTEMS.contains(caller)
        if (!BuildConfig.SUPPORTS_THIRD_PARTY_SYSTEMS && !supportedSystem) {
            Log.e(tag, "This theme does not support the launching theme system. [HIJACK] ($caller)")
            Toast.makeText(this,
                    String.format(getString(R.string.unauthorized_theme_client_hijack), caller),
                    Toast.LENGTH_LONG).show()
            finish()
        }
        if (debug) {
            Log.d(tag, "'$caller' has been authorized to launch this theme. (Phase 1)")
        }

        /* STEP 2: Ensure that our support is added where it belongs */
        val action = intent.action
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        var verified = false
        if ((action == substratumIntentData) or (action == getKeysIntent)) {
            // Assume this called from organization's app
            if (organizationsSystem) {
                verified = when {
                    BuildConfig.ALLOW_THIRD_PARTY_SUBSTRATUM_BUILDS -> true
                    else -> checkApprovedSignature(this, caller)
                }
            }
        } else {
            OTHER_THEME_SYSTEMS
                    .filter { action?.startsWith(prefix = it, ignoreCase = true) ?: false }
                    .forEach { verified = true }
        }
        if (!verified) {
            Log.e(tag, "This theme does not support the launching theme system. ($action)")
            Toast.makeText(this, R.string.unauthorized_theme_client, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (debug) {
            Log.d(tag, "'$action' has been authorized to launch this theme. (Phase 2)")
        }


        /* STEP 3: Do da thang */
        if (SHOW_LAUNCH_DIALOG) run {
            if (SHOW_DIALOG_REPEATEDLY) {
                showDialog()
                sharedPref.edit().remove("dialog_showed").apply()
            } else if (!sharedPref.getBoolean("dialog_showed", false)) {
                showDialog()
                sharedPref.edit().putBoolean("dialog_showed", true).apply()
            } else {
                startAntiPiracyCheck()
            }
        } else {
            startAntiPiracyCheck()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        piracyChecker.destroy()
    }

    private fun startAntiPiracyCheck() {
        if (BuildConfig.BASE_64_LICENSE_KEY.isEmpty() && debug && !BuildConfig.DEBUG) {
            Log.e(tag, PiracyCheckerUtils.getAPKSignature(this))
        }

        piracyChecker = PiracyChecker(this)
        if (BuildConfig.ENFORCE_GOOGLE_PLAY_INSTALL) {
            piracyChecker.enableInstallerId(InstallerID.GOOGLE_PLAY)
        }
        if (BuildConfig.BASE_64_LICENSE_KEY.isNotEmpty()) {
            piracyChecker.enableGooglePlayLicensing(BuildConfig.BASE_64_LICENSE_KEY)
        }
        if (BuildConfig.APK_SIGNATURE_PRODUCTION.isNotEmpty()) {
            piracyChecker.enableSigningCertificate(BuildConfig.APK_SIGNATURE_PRODUCTION)
        }

        piracyChecker.callback(object : PiracyCheckerCallback() {
            override fun allow() {
                val returnIntent = if (intent.action == getKeysIntent) {
                    Intent(receiveKeysIntent)
                } else {
                    Intent()
                }

                val themeName = getString(R.string.ThemeName)
                val themeAuthor = getString(R.string.ThemeAuthor)
                val themePid = packageName
                returnIntent.putExtra("theme_name", themeName)
                returnIntent.putExtra("theme_author", themeAuthor)
                returnIntent.putExtra("theme_pid", themePid)
                returnIntent.putExtra("theme_debug", BuildConfig.DEBUG)
                returnIntent.putExtra("theme_piracy_check", themePiracyCheck)
                returnIntent.putExtra("encryption_key", BuildConfig.DECRYPTION_KEY)
                returnIntent.putExtra("iv_encrypt_key", BuildConfig.IV_KEY)

                val callingPackage = intent.getStringExtra("calling_package_name")
                if (!isCallingPackageAllowed(callingPackage)) {
                    finish()
                } else {
                    returnIntent.`package` = callingPackage
                }

                if (intent.action == substratumIntentData) {
                    setResult(getSelfSignature(applicationContext), returnIntent)
                } else if (intent.action == getKeysIntent) {
                    returnIntent.action = receiveKeysIntent
                    sendBroadcast(returnIntent)
                }
                finish()
            }

            override fun dontAllow(error: PiracyCheckerError, pirateApp: PirateApp?) {
                val parse = String.format(
                        getString(R.string.toast_unlicensed),
                        getString(R.string.ThemeName))
                Toast.makeText(this@SubstratumLauncher, parse, Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        if (!themePiracyCheck) {
            piracyChecker.start()
        } else {
            Toast.makeText(this, R.string.unauthorized, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun showDialog() {
        val dialog = AlertDialog.Builder(this, R.style.DialogStyle)
                .setCancelable(false)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(R.string.launch_dialog_title)
                .setMessage(R.string.launch_dialog_content)
                .setPositiveButton(R.string.launch_dialog_positive) { _, _ -> startAntiPiracyCheck() }
        if (getString(R.string.launch_dialog_negative).isNotEmpty()) {
            if (getString(R.string.launch_dialog_negative_url).isNotEmpty()) {
                dialog.setNegativeButton(R.string.launch_dialog_negative) { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.launch_dialog_negative_url))))
                    finish()
                }
            } else {
                dialog.setNegativeButton(R.string.launch_dialog_negative) { _, _ -> finish() }
            }
        }
        dialog.show()
    }
}