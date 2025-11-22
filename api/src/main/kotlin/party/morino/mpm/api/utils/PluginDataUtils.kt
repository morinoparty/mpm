/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.utils

import org.yaml.snakeyaml.Yaml
import party.morino.mpm.api.model.plugin.PluginData
import java.io.File
import java.util.jar.JarFile

object PluginDataUtils {
    fun getPluginData(file: File): PluginData? {
        val jarFile = JarFile(file)
        // Paper プラグインは新しいフォーマットなので、先にチェックする
        val paperYml = jarFile.getEntry("paper-plugin.yml")
        if (paperYml != null) {
            return getPaperPluginData(jarFile)
        }
        // Bukkit/Spigot プラグインは古いフォーマット
        val pluginYml = jarFile.getEntry("plugin.yml")
        if (pluginYml != null) {
            return getBukkitPluginData(jarFile)
        }
        return null
    }

    private fun getPaperPluginData(jarFile: JarFile): PluginData.PaperPluginData {
        val paperYml = jarFile.getEntry("paper-plugin.yml")
        val paperYmlStream = jarFile.getInputStream(paperYml)
        val paperYmlReader = paperYmlStream.bufferedReader()
        val yaml = Yaml()
        val yamlData = yaml.load<Map<String, Any>>(paperYmlReader)
        val name = (yamlData["name"] ?: "").toString()
        val version = (yamlData["version"] ?: "").toString()
        val main = (yamlData["main"] ?: "").toString()
        val description = (yamlData["description"] ?: "").toString()
        val apiVersion = (yamlData["api-version"] ?: "").toString()
        val bootstrapper = (yamlData["bootstrapper"] ?: "").toString()
        val loader = (yamlData["loader"] ?: "").toString()
        val author = (yamlData["author"] ?: "").toString()
        val website = (yamlData["website"] ?: "").toString()

        return PluginData.PaperPluginData(
            name,
            version,
            main,
            description,
            apiVersion,
            bootstrapper,
            loader,
            author,
            website
        )
    }

    private fun getBukkitPluginData(jarFile: JarFile): PluginData.BukkitPluginData {
        val pluginYml = jarFile.getEntry("plugin.yml")
        val pluginYmlStream = jarFile.getInputStream(pluginYml)
        val pluginYmlReader = pluginYmlStream.bufferedReader()
        val yaml = Yaml()
        val yamlData = yaml.load<Map<String, Any>>(pluginYmlReader)
        val name = (yamlData["name"] ?: "").toString()
        val version = (yamlData["version"] ?: "").toString()
        val main = (yamlData["main"] ?: "").toString()
        val description = (yamlData["description"] ?: "").toString()
        val author = (yamlData["author"] ?: "").toString()
        val website = (yamlData["website"] ?: "").toString()
        val apiVersion = (yamlData["api-version"] ?: "").toString()

        return PluginData.BukkitPluginData(
            name,
            version,
            main,
            description,
            author,
            website,
            apiVersion
        )
    }
}