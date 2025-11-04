package com.simplexray.an.service;

/**
 * Binder interface for VPN service communication
 * Provides state query and callback registration
 */
interface IVpnServiceBinder {
    /**
     * Check if VPN service is currently connected
     */
    boolean isConnected();
    
    /**
     * Get current traffic stats (uplink, downlink in bytes)
     */
    long[] getTrafficStats();
    
    /**
     * Register callback for state changes
     * Returns true if registration successful
     */
    boolean registerCallback(in IVpnStateCallback callback);
    
    /**
     * Unregister callback
     */
    void unregisterCallback(in IVpnStateCallback callback);
}

