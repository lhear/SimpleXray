package com.simplexray.an

import java.io.File
import java.util.concurrent.ExecutorService

interface OnConfigActionListener {
    fun onEditConfigClick(file: File?)

    fun onDeleteConfigClick(file: File?)

    val executorService: ExecutorService?

    fun switchVpnService()

    fun createNewConfigFileAndEdit()

    fun importConfigFromClipboard()

    fun performBackup()

    fun performRestore()

    fun triggerAssetExtraction()

    fun reloadConfig()
}
