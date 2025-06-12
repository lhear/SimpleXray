package com.simplexray.an

import java.io.File

interface OnConfigActionListener {
    fun onEditConfigClick(file: File?)

    fun onDeleteConfigClick(file: File?)

    fun switchVpnService()

    fun createNewConfigFileAndEdit()

    fun importConfigFromClipboard()

    fun performBackup()

    fun performRestore()

    fun triggerAssetExtraction()

    fun reloadConfig()
}
