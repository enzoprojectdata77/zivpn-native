package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileLoaded
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.util.*

class ConfigurationModule(service: Service) : Module<ConfigurationModule.LoadException>(service) {
    data class LoadException(val message: String)

    private val store = ServiceStore(service)
    private val reload = Channel<Unit>(Channel.CONFLATED)

    override suspend fun run() {
        val broadcasts = receiveBroadcast {
            addAction(Intents.ACTION_PROFILE_CHANGED)
            addAction(Intents.ACTION_OVERRIDE_CHANGED)
        }

        var loaded: UUID? = null

        reload.trySend(Unit)

        while (true) {
            val changed: UUID? = select {
                broadcasts.onReceive {
                    if (it.action == Intents.ACTION_PROFILE_CHANGED)
                        UUID.fromString(it.getStringExtra(Intents.EXTRA_UUID))
                    else
                        null
                }
                reload.onReceive {
                    null
                }
            }

            try {
                val current = store.activeProfile
                    ?: throw NullPointerException("No profile selected")

                if (current == loaded && changed != null && changed != loaded)
                    continue

                loaded = current

                val active = ImportedDao().queryByUUID(current)
                    ?: throw NullPointerException("No profile selected")

                val configFile = service.importedDir.resolve(active.uuid.toString()).resolve("config.yaml")
                if (configFile.exists()) {
                    var content = configFile.readText()
                    if (!content.contains("ZIVPN-CORE-NATIVE")) {
                        val proxyDef = "  - name: \"ZIVPN-CORE-NATIVE\"\n    type: socks5\n    server: 127.0.0.1\n    port: 7777"
                        val groupDef = "  - name: \"ZIVPN-AUTO\"\n    type: select\n    proxies:\n      - \"ZIVPN-CORE-NATIVE\""
                        val ruleDef = "  - MATCH,ZIVPN-AUTO"
                        
                        // Inject proxy
                        if (content.contains("proxies:")) {
                            content = content.replace("proxies:", "proxies:\n$proxyDef")
                        } else {
                            content = "proxies:\n$proxyDef\n" + content
                        }

                        // Inject proxy group
                        if (content.contains("proxy-groups:")) {
                            content = content.replace("proxy-groups:", "proxy-groups:\n$groupDef")
                        } else {
                            // If no proxy-groups, add it after proxies
                            content = content + "\nproxy-groups:\n$groupDef"
                        }
                        
                        // Inject rule
                        if (content.contains("rules:")) {
                            // Replace rules: with rules: \n - MATCH... 
                            // BUT wait, MATCH should be at the END usually, or Beginning?
                            // If we put it at beginning, it overrides everything.
                            content = content.replace("rules:", "rules:\n$ruleDef")
                        } else {
                            content += "\nrules:\n$ruleDef"
                        }
                        
                        configFile.writeText(content)
                    }
                }

                Clash.load(service.importedDir.resolve(active.uuid.toString())).await()

                val remove = SelectionDao().querySelections(active.uuid)
                    .filterNot { Clash.patchSelector(it.proxy, it.selected) }
                    .map { it.proxy }

                SelectionDao().removeSelections(active.uuid, remove)

                StatusProvider.currentProfile = active.name

                service.sendProfileLoaded(current)

                Log.d("Profile ${active.name} loaded")
            } catch (e: Exception) {
                return enqueueEvent(LoadException(e.message ?: "Unknown"))
            }
        }
    }
}