/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.libhook.rules.packageinstaller

import android.text.TextUtils
import android.view.View
import android.widget.TextView
import com.sevtinge.hyperceiler.libhook.R
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import com.sevtinge.hyperceiler.libhook.utils.hookapi.tool.AppsTool.getModuleRes
import com.sevtinge.hyperceiler.libhook.utils.hookapi.tool.findViewByIdName
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createAfterHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createInterceptHook
import java.lang.reflect.Method

object OfflineInstallAuthorization : BaseHook() {
    private lateinit var bindOfflineButton: Method
    private lateinit var onOfflineClick: Method
    private lateinit var authorizeOnce: Method

    override fun useDexKit() = true

    override fun initDexKit(): Boolean {
        bindOfflineButton = requiredMember("BindOfflineButton") { bridge ->
            bridge.findMethod {
                matcher {
                    returnType = "void"
                    paramCount(1)
                    usingEqStrings("install_btn")
                    usingNumbers(16)
                }
            }.single()
        }
        val fragment = bindOfflineButton.declaringClass
        onOfflineClick = requiredMember("OfflineWarningClick") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass = fragment.name
                    returnType = "void"
                    paramTypes(fragment, View::class.java)
                    usingEqStrings("install_btn")
                    addInvoke {
                        name = "<init>"
                        usingEqStrings("offline_unregistered_install_popup")
                    }
                }
            }.single()
        }
        authorizeOnce = requiredMember("RequestSingleAuthorization") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass = "com.miui.packageInstaller.NewInstallerPrepareActivity"
                    returnType = "void"
                    paramCount(0)
                    usingEqStrings("single_authorize_btn", "verify_method")
                }
            }.single()
        }
        return true
    }

    override fun init() {
        bindOfflineButton.createAfterHook { param ->
            val bar = param.args[0] as View
            val button = bar.findViewByIdName("second_button") ?: return@createAfterHook
            if (button.visibility != View.VISIBLE || !button.isEnabled) return@createAfterHook
            val label = button.findViewByIdName("left_button_msg") as? TextView ?: return@createAfterHook
            label.text = offlineLabel(label)
        }
        onOfflineClick.createInterceptHook { chain ->
            val button = chain.getArg(1) as View
            val label = button.findViewByIdName("left_button_msg") as? TextView
            // Keep the stock warning unless the user tapped the visibly labelled authorization action.
            if (!button.isEnabled || label == null || !label.isShown || !TextUtils.equals(label.text, offlineLabel(label))) {
                return@createInterceptHook chain.proceed()
            }
            val activity = callMethod(chain.getArg(0), "getActivity")
            if (!authorizeOnce.declaringClass.isInstance(activity)) return@createInterceptHook chain.proceed()
            authorizeOnce.invoke(activity)
        }
    }

    private fun offlineLabel(view: View): CharSequence =
        getModuleRes(view.context).getText(R.string.package_installer_authorize_offline)
}
