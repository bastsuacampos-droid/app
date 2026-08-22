import { registerPlugin } from '@capacitor/core';

export interface FileOpenerPlugin {
  /** Writes the base64 data to a cache file and launches Android's "abrir con" chooser for it. */
  open(options: { data: string; fileName: string; mimeType: string }): Promise<void>;
  /** Writes the base64 data to a cache file and launches Android's share sheet (ACTION_SEND)
   * for it — for exports the user wants to save or send elsewhere, not view in place. */
  share(options: { data: string; fileName: string; mimeType: string }): Promise<void>;
}

const FileOpener = registerPlugin<FileOpenerPlugin>('FileOpener');

export default FileOpener;
