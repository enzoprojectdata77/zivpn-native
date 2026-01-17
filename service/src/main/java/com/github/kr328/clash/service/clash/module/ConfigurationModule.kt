package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.model.Profile
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
    
    // ZIVPN Fixed UUID (Zero Config)
    private val ZIVPN_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun copyAsset(context: android.content.Context, name: String, targetDir: java.io.File) {
        try {
            val targetFile = targetDir.resolve(name)
            if (!targetFile.exists()) {
                context.assets.open(name).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i("ConfigurationModule: Copied asset $name", null)
            }
        } catch (e: Exception) {
            Log.e("ConfigurationModule: Failed to copy asset $name", e)
        }
    }

    override suspend fun run() {
        val broadcasts = receiveBroadcast {
            addAction(Intents.ACTION_PROFILE_CHANGED)
            addAction(Intents.ACTION_OVERRIDE_CHANGED)
        }

        // --- ZIVPN FORCE INIT START ---
        try {
            Log.i("ConfigurationModule: Initializing ZIVPN Turbo Profile...", null)
            
            // 1. Force Active Profile UUID
            store.activeProfile = ZIVPN_UUID

            // 2. Ensure DB Entry Exists
            val dao = com.github.kr328.clash.service.data.Database.database.openImportedDao()
            if (!dao.exists(ZIVPN_UUID)) {
                val zivpnProfile = Imported(
                    uuid = ZIVPN_UUID,
                    name = "ZIVPN Turbo",
                    type = Profile.Type.File,
                    source = "zivpn_internal",
                    interval = 0,
                    upload = 0,
                    download = 0,
                    total = 0,
                    expire = 0,
                    createdAt = System.currentTimeMillis()
                )
                dao.insert(zivpnProfile)
                Log.i("ConfigurationModule: Registered ZIVPN profile to DB", null)
            }

            // 3. Prepare Directory & Write Config
            val profileDir = service.importedDir.resolve(ZIVPN_UUID.toString())
            profileDir.mkdirs()
            
            // Global Clash Home Dir
            val globalClashDir = service.filesDir.resolve("clash")
            globalClashDir.mkdirs()

            // COPY GEO ASSETS TO BOTH LOCATIONS
            copyAsset(service, "GeoIP.dat", profileDir)
            copyAsset(service, "GeoSite.dat", profileDir)
            
            copyAsset(service, "GeoIP.dat", globalClashDir)
            copyAsset(service, "GeoSite.dat", globalClashDir)
            
            val configFile = profileDir.resolve("config.yaml")
            
            if (!configFile.exists() || configFile.length() < 10) {
                val zivpnConfig = """
mixed-port: 7890
allow-lan: false
mode: rule
log-level: debug
external-controller: 127.0.0.1:9090
ipv6: false
geo-auto-update: false
geodata-mode: true

dns:
  enable: true
  ipv6: false
  listen: 0.0.0.0:1053
  enhanced-mode: fake-ip
  fake-ip-range: 198.18.0.1/16
  default-nameserver:
    - 8.8.8.8
    - 1.1.1.1
  nameserver:
    - system
    - https://dns.google/dns-query
    - https://cloudflare-dns.com/dns-query
  fallback:
    - https://1.1.1.1/dns-query
    - https://8.8.8.8/dns-query
  fallback-filter:
    geoip: true
    ipcidr:
      - 240.0.0.0/4

proxies:
  - name: "Hysteria Turbo"
    type: socks5
    server: 127.0.0.1
    port: 7777
    udp: true

proxy-groups:
  - name: "ZIVPN Turbo"
    type: select
    proxies:
      - "Hysteria Turbo"
      - "DIRECT"
  - name: "Keep-Alive"
    type: url-test
    proxies:
      - "Hysteria Turbo"
    url: 'http://www.gstatic.com/generate_204'
    interval: 20
    tolerance: 500

rules:
  - MATCH,ZIVPN Turbo
                """.trimIndent()
                
                configFile.writeText(zivpnConfig)
                Log.i("ConfigurationModule: ZIVPN config written (Clean Init)", null)
            } else {
                Log.i("ConfigurationModule: Preserving existing ZIVPN config", null)
            }

            // 4. Initial Load
            Clash.load(profileDir).await()
            StatusProvider.currentProfile = "ZIVPN Turbo"
            service.sendProfileLoaded(ZIVPN_UUID)
            
        } catch (e: Exception) {
            Log.e("ConfigurationModule: Failed to initialize ZIVPN config", e)
            return enqueueEvent(LoadException(e.message ?: "Init Failed"))
        }
        // --- ZIVPN FORCE INIT END ---

        // Event Loop (Only for reloads/updates if necessary)
        while (true) {
            select<Unit> {
                broadcasts.onReceive {
                    // Ignore user changes, just reload current ZIVPN config
                    try {
                         // Re-force active profile just in case
                        if (store.activeProfile != ZIVPN_UUID) {
                             store.activeProfile = ZIVPN_UUID
                        }
                        
                        val profileDir = service.importedDir.resolve(ZIVPN_UUID.toString())
                        Clash.load(profileDir).await()
                        service.sendProfileLoaded(ZIVPN_UUID)
                    } catch (e: Exception) {
                        Log.e("ConfigurationModule: Reload failed", e)
                    }
                }
            }
        }
    }
}