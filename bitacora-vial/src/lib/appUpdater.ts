import { Capacitor } from '@capacitor/core';
import { Network } from '@capacitor/network';
import { APP_VERSION } from '../version';
import AppUpdater from './nativeAppUpdater';

const REPO = 'bastsuacampos-droid/app';
const RELEASES_URL = `https://api.github.com/repos/${REPO}/releases/latest`;

export interface UpdateInfo {
  version: string;
  apkUrl: string;
  notes: string;
}

interface GithubAsset {
  name: string;
  browser_download_url: string;
}

interface GithubRelease {
  tag_name: string;
  body: string | null;
  assets: GithubAsset[];
}

function parseVersion(v: string): number[] {
  return v
    .trim()
    .replace(/^v/i, '')
    .split('.')
    .map((n) => parseInt(n, 10) || 0);
}

/** True if `remote` is a strictly higher version than `local` (dotted numeric, optional leading "v"). */
export function isNewerVersion(remote: string, local: string): boolean {
  const r = parseVersion(remote);
  const l = parseVersion(local);
  const len = Math.max(r.length, l.length);
  for (let i = 0; i < len; i += 1) {
    const rv = r[i] ?? 0;
    const lv = l[i] ?? 0;
    if (rv !== lv) return rv > lv;
  }
  return false;
}

/** Checks the repo's latest GitHub release; returns null if there's no newer version or no .apk asset attached. */
export async function checkForUpdate(): Promise<UpdateInfo | null> {
  const res = await fetch(RELEASES_URL, { headers: { Accept: 'application/vnd.github+json' } });
  if (!res.ok) return null;

  const data = (await res.json()) as GithubRelease;
  const tag = data.tag_name ?? '';
  if (!tag || !isNewerVersion(tag, APP_VERSION)) return null;

  const asset = data.assets.find((a) => a.name.toLowerCase().endsWith('.apk'));
  if (!asset) return null;

  return {
    version: tag.replace(/^v/i, ''),
    apkUrl: asset.browser_download_url,
    notes: data.body ?? '',
  };
}

export async function isOnWifi(): Promise<boolean> {
  if (!Capacitor.isNativePlatform()) return false;
  const status = await Network.getStatus();
  return status.connected && status.connectionType === 'wifi';
}

export type InstallStartResult = 'started' | 'needs-permission' | 'unsupported';

/** Kicks off the native download; on completion the OS shows its own install-confirmation prompt
 * (Android never allows a fully silent install from outside Play Store). */
export async function downloadAndInstallUpdate(update: UpdateInfo): Promise<InstallStartResult> {
  if (!Capacitor.isNativePlatform()) return 'unsupported';

  const perm = await AppUpdater.canInstallPackages();
  if (!perm.value) return 'needs-permission';

  await AppUpdater.downloadAndInstall({ url: update.apkUrl, fileName: `bitacora-vial-${update.version}.apk` });
  return 'started';
}

export async function requestInstallPermission(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  await AppUpdater.requestInstallPermission();
}
