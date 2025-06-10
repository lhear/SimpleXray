package com.simplexray.an;

import java.io.File;
import java.util.concurrent.ExecutorService;

public interface OnConfigActionListener {
    void onEditConfigClick(File file);

    void onDeleteConfigClick(File file);

    ExecutorService getExecutorService();

    void switchVpnService();

    void createNewConfigFileAndEdit();

    void importConfigFromClipboard();

    void performBackup();

    void performRestore();

    void triggerAssetExtraction();

    void reloadConfig();
}
