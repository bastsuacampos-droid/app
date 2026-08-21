import { registerPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

export interface DownloadProgressEvent {
  percent: number;
}

export interface DownloadErrorEvent {
  message: string;
}

export interface AppUpdaterPlugin {
  /** Whether Android will let this app launch a package installer (API 26+ "unknown sources" toggle). Always true below API 26. */
  canInstallPackages(): Promise<{ value: boolean }>;
  /** Opens the system settings screen where the user grants "install unknown apps" for this app. */
  requestInstallPermission(): Promise<void>;
  /** Downloads the given APK via Android's DownloadManager and, on completion, launches the system installer. */
  downloadAndInstall(options: { url: string; fileName?: string }): Promise<{ started: boolean }>;
  addListener(
    eventName: 'downloadProgress',
    listenerFunc: (event: DownloadProgressEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'downloadError',
    listenerFunc: (event: DownloadErrorEvent) => void,
  ): Promise<PluginListenerHandle>;
}

const AppUpdater = registerPlugin<AppUpdaterPlugin>('AppUpdater');

export default AppUpdater;
