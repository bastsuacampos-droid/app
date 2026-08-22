import { registerPlugin } from '@capacitor/core';
import { blobToBase64 } from './archivos';

export interface FileOpenerPlugin {
  /** Appends one base64-decoded chunk to the named cache file — append=false on the first
   * chunk truncates/creates it, true appends. See writeFileChunked() below. */
  writeChunk(options: { fileName: string; data: string; append: boolean }): Promise<void>;
  /** Launches Android's "abrir con" chooser (ACTION_VIEW) for a file already fully written
   * via writeFileChunked(). */
  open(options: { fileName: string; mimeType: string }): Promise<void>;
  /** Launches Android's share sheet (ACTION_SEND) for a file already fully written via
   * writeFileChunked() — for exports the user wants to save or send elsewhere, not view
   * in place. */
  share(options: { fileName: string; mimeType: string }): Promise<void>;
}

const FileOpener = registerPlugin<FileOpenerPlugin>('FileOpener');

export default FileOpener;

// Keep each bridge call's base64 payload well under any WebView/binder message-size limit,
// regardless of the file's total size.
const CHUNK_BYTES = 512 * 1024;

/** Streams `blob` into the native cache file (cacheDir/compartidos/<fileName>) in bounded-size
 * chunks instead of one call carrying the whole thing as base64 — a multi-MB payload (e.g. a
 * respaldo completo with embedded photos) handed to a single Capacitor plugin call has been
 * observed to crash the app, since the JS-to-native bridge isn't built for large single
 * messages. Call FileOpener.open()/.share() with the same fileName once this resolves. */
export async function writeFileChunked(fileName: string, blob: Blob): Promise<void> {
  if (blob.size === 0) {
    await FileOpener.writeChunk({ fileName, data: '', append: false });
    return;
  }
  for (let offset = 0; offset < blob.size; offset += CHUNK_BYTES) {
    const data = await blobToBase64(blob.slice(offset, offset + CHUNK_BYTES));
    await FileOpener.writeChunk({ fileName, data, append: offset > 0 });
  }
}
