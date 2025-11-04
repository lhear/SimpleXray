package com.simplexray.an.service;

/**
 * Callback interface for VPN state changes
 */
interface IVpnStateCallback {
    /**
     * Called when VPN connection is established
     */
    void onConnected();
    
    /**
     * Called when VPN connection is disconnected
     */
    void onDisconnected();
    
    /**
     * Called when an error occurs
     */
    void onError(String error);
    
    /**
     * Called periodically with traffic updates (uplink, downlink in bytes)
     */
    void onTrafficUpdate(long uplink, long downlink);
}

