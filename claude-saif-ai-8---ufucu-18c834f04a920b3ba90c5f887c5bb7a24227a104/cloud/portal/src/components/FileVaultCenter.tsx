import React, { useState } from 'react';
import {
  FileText,
  FileCode,
  Image as ImageIcon,
  FileArchive,
  Film,
  Music,
  ShieldCheck,
  Flame,
  Clock,
  Download,
  Lock,
  CheckCircle2,
  Fingerprint,
  Layers,
  Sparkles,
  Copy,
  Check
} from 'lucide-react';
import { VaultData, FileItem } from '../types';
import { formatBytes } from '../utils/cryptoToken';

interface FileVaultCenterProps {
  vault: VaultData | null;
  isLoading: boolean;
  error: string | null;
  onInitiateDownload: (file?: FileItem) => void;
  onRefreshVault: () => void;
}

export const FileVaultCenter: React.FC<FileVaultCenterProps> = ({
  vault,
  isLoading,
  error,
  onInitiateDownload,
  onRefreshVault,
}) => {
  const [selectedFileIndex, setSelectedFileIndex] = useState<number>(0);
  const [copiedLink, setCopiedLink] = useState(false);

  const selectedFile: FileItem | undefined = vault?.files?.[selectedFileIndex] || vault?.files?.[0];

  const getCategoryIcon = (category: string) => {
    switch (category) {
      case 'code':
        return <FileCode className="w-10 h-10 text-cyan-400" />;
      case 'image':
        return <ImageIcon className="w-10 h-10 text-purple-400" />;
      case 'archive':
        return <FileArchive className="w-10 h-10 text-amber-400" />;
      case 'video':
        return <Film className="w-10 h-10 text-rose-400" />;
      case 'audio':
        return <Music className="w-10 h-10 text-emerald-400" />;
      default:
        return <FileText className="w-10 h-10 text-blue-400" />;
    }
  };

  const copyCurrentUrl = () => {
    navigator.clipboard.writeText(window.location.href);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  };

  // Loading state
  if (isLoading) {
    return (
      <div className="min-h-[50vh] flex flex-col items-center justify-center p-6">
        <div className="relative w-24 h-24 flex items-center justify-center">
          <div className="absolute inset-0 rounded-full border-2 border-cyan-500/20 border-t-cyan-400 animate-spin"></div>
          <div className="absolute inset-3 rounded-full border-2 border-purple-500/20 border-b-purple-400 animate-spin [animation-duration:2s]"></div>
          <Lock className="w-8 h-8 text-cyan-400 animate-pulse" />
        </div>
        <h3 className="mt-6 text-lg font-bold text-slate-200">
          Decoupling Isolated File Vault...
        </h3>
        <p className="mt-1 text-xs text-slate-400 font-mono">
          Verifying One-Time Access Token with SAIF AI Shield
        </p>
      </div>
    );
  }

  // Burned or Expired error state
  if (error || vault?.isBurned) {
    return (
      <div className="min-h-[50vh] flex flex-col items-center justify-center p-6 max-w-xl mx-auto text-center">
        {/* BIG HEADLINE */}
        <h1 className="text-3xl sm:text-5xl font-black tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-blue-400 to-indigo-400 uppercase mb-6 drop-shadow-lg">
          SAIF AI DOWNLOADS
        </h1>

        <div className="relative mb-6">
          <div className="w-20 h-20 rounded-2xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-center shadow-xl shadow-rose-500/20">
            <Flame className="w-10 h-10 text-rose-500 animate-pulse" />
          </div>
          <span className="absolute -bottom-2 -right-2 px-2 py-0.5 rounded text-[10px] font-mono bg-rose-500 text-white font-bold">
            BURNED
          </span>
        </div>

        <h2 className="text-xl sm:text-2xl font-extrabold text-white tracking-tight">
          One-Time Link Expired / Burned
        </h2>
        <p className="mt-2 text-slate-300 text-sm leading-relaxed max-w-md">
          {error || 'This file link was configured as a one-time single-use link (OTP-style). It has already been downloaded or burned for privacy.'}
        </p>

        <div className="mt-6 p-4 rounded-xl bg-slate-900/80 border border-slate-800 text-left w-full text-xs text-slate-400">
          <div className="flex items-center gap-1.5 font-mono text-cyan-400 font-bold mb-1">
            <ShieldCheck className="w-4 h-4" />
            PROTECTED BY SAIF AI PRIVATE PUSH PROTOCOL
          </div>
          Files are pushed directly from your authorized app via REST API. Each user receives an isolated link that cannot be accessed by anyone else.
        </div>

        <div className="mt-6">
          <button
            onClick={onRefreshVault}
            className="px-5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-medium text-xs border border-slate-700 transition-all cursor-pointer"
          >
            Check Link Again
          </button>
        </div>
      </div>
    );
  }

  // Normal Loaded Vault State
  if (!vault || !selectedFile) {
    return (
      <div className="min-h-[50vh] flex flex-col items-center justify-center p-6 text-center">
        {/* BIG HEADLINE */}
        <h1 className="text-3xl sm:text-5xl font-black tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-blue-400 to-indigo-400 uppercase mb-4 drop-shadow-lg">
          SAIF AI DOWNLOADS
        </h1>
        <div className="w-16 h-16 rounded-2xl bg-slate-800/60 border border-slate-700/60 flex items-center justify-center mb-4">
          <Lock className="w-8 h-8 text-slate-400" />
        </div>
        <h3 className="text-lg font-bold text-slate-200">Waiting for File Push from App</h3>
        <p className="mt-2 text-xs text-slate-400 max-w-md">
          Open a unique link generated by your app (e.g. <code className="text-cyan-400 font-mono">?v=vault_id&token=...</code>) to access and download your file.
        </p>
      </div>
    );
  }

  const isOneTime = vault.securityMode === 'onetime' || vault.burnOnDownload;

  return (
    <div className="w-full max-w-3xl mx-auto px-4 py-4">
      {/* BIG BOLD TITLE IN THE CENTER: SAIF AI DOWNLOADS */}
      <div className="text-center mb-6">
        <h1 className="text-3xl sm:text-5xl md:text-6xl font-black tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-sky-300 to-indigo-400 uppercase drop-shadow-[0_0_35px_rgba(6,182,212,0.35)]">
          SAIF AI DOWNLOADS
        </h1>
        <div className="mt-2 inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/30 text-xs font-mono text-cyan-300">
          <ShieldCheck className="w-3.5 h-3.5 text-cyan-400" />
          <span>SECURE ONE-TIME LINK & INSTANT AD-REWARDED DOWNLOAD</span>
        </div>
      </div>

      {/* CENTERPIECE HOLOGRAPHIC FILE CARD */}
      <div className="relative">
        {/* Glow backdrop behind file center */}
        <div className="absolute -inset-1 rounded-3xl bg-gradient-to-r from-cyan-500/25 via-blue-500/15 to-purple-500/25 blur-xl opacity-80 pointer-events-none"></div>

        <div className="relative rounded-2xl bg-[#090d1a]/95 border border-cyan-500/40 shadow-2xl p-6 sm:p-8 overflow-hidden backdrop-blur-xl">
          {/* Subtle Cyber radar effect */}
          <div className="absolute inset-0 pointer-events-none opacity-20 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-cyan-500/20 via-transparent to-transparent"></div>

          {/* Top Info Bar inside card */}
          <div className="flex flex-wrap items-center justify-between gap-2 pb-4 mb-6 border-b border-slate-800 text-xs font-mono">
            <div className="flex items-center gap-2">
              <span className="text-cyan-400 font-bold">ID: {vault.vaultId}</span>
              <span className="px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 text-[10px]">
                READY
              </span>
            </div>

            <div className="flex items-center gap-2">
              {isOneTime ? (
                <span className="flex items-center gap-1 text-rose-400 font-semibold text-[11px] bg-rose-500/10 px-2 py-0.5 rounded border border-rose-500/20">
                  <Flame className="w-3 h-3 animate-pulse" />
                  ONE-TIME (BURNS AFTER DOWNLOAD)
                </span>
              ) : (
                <span className="flex items-center gap-1 text-cyan-400 font-semibold text-[11px] bg-cyan-500/10 px-2 py-0.5 rounded border border-cyan-500/20">
                  <Clock className="w-3 h-3" />
                  DYNAMIC SHIELD
                </span>
              )}

              <button
                onClick={copyCurrentUrl}
                title="Copy Link"
                className="p-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors cursor-pointer"
              >
                {copiedLink ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              </button>
            </div>
          </div>

          {/* Multiple Files Tab Bar (if bundle has multiple files) */}
          {vault.files.length > 1 && (
            <div className="mb-6 pb-4 border-b border-slate-800">
              <div className="flex items-center justify-between mb-2">
                <span className="flex items-center gap-1.5 text-xs font-mono text-cyan-400 font-semibold">
                  <Layers className="w-3.5 h-3.5" />
                  BUNDLE: {vault.files.length} FILES
                </span>
                <span className="text-xs text-slate-400 font-mono">
                  {formatBytes(vault.files.reduce((acc, f) => acc + f.size, 0))}
                </span>
              </div>

              <div className="flex items-center gap-2 overflow-x-auto pb-1">
                {vault.files.map((file, idx) => (
                  <button
                    key={file.id}
                    onClick={() => setSelectedFileIndex(idx)}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer ${
                      selectedFileIndex === idx
                        ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 shadow-sm shadow-cyan-500/20'
                        : 'bg-slate-900/60 text-slate-400 hover:text-slate-200 border border-slate-800'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-cyan-400"></span>
                    <span className="truncate max-w-[120px]">{file.name}</span>
                    <span className="text-[10px] text-slate-400 font-mono">({formatBytes(file.size)})</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Centered Main File Presentation */}
          <div className="flex flex-col items-center text-center">
            {/* Visual File Icon Hologram */}
            <div className="relative group mb-4">
              <div className="relative w-24 h-24 rounded-2xl bg-gradient-to-b from-cyan-500/10 to-blue-600/10 border border-cyan-400/40 flex items-center justify-center shadow-xl shadow-cyan-500/20 group-hover:scale-105 transition-all">
                <div className="absolute inset-0 rounded-2xl border border-dashed border-cyan-500/40 animate-radar pointer-events-none"></div>
                {getCategoryIcon(selectedFile.category)}
              </div>
              <span className="absolute -top-2 -right-2 px-2 py-0.5 rounded-md text-[10px] font-mono font-bold bg-cyan-500 text-slate-950 uppercase shadow-md">
                {selectedFile.category}
              </span>
            </div>

            {/* File Name & Specifications */}
            <h2 className="text-xl sm:text-2xl font-black text-white tracking-wide max-w-lg break-words">
              {selectedFile.name}
            </h2>

            <div className="mt-2.5 flex flex-wrap items-center justify-center gap-2 text-xs font-mono text-slate-400">
              <span className="px-2.5 py-1 rounded bg-slate-900 border border-slate-800 text-slate-200 font-semibold">
                Size: {formatBytes(selectedFile.size)}
              </span>
              <span className="px-2.5 py-1 rounded bg-slate-900 border border-slate-800">
                Format: {selectedFile.mimeType}
              </span>
              <span className="px-2.5 py-1 rounded bg-slate-900 border border-slate-800 text-emerald-400 flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5" />
                Verified Clean
              </span>
            </div>

            {/* SHA-256 Fingerprint */}
            <div className="mt-3 flex items-center gap-2 px-3 py-1 rounded-lg bg-black/50 border border-slate-800 text-[11px] font-mono text-slate-400 max-w-sm w-full justify-center">
              <Fingerprint className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
              <span className="truncate">CHECKSUM: {selectedFile.hash}</span>
            </div>

            {/* PRIMARY ACTION: AD-REWARDED DOWNLOAD */}
            <div className="mt-7 w-full max-w-md">
              <button
                onClick={() => onInitiateDownload(selectedFile)}
                className="w-full relative group overflow-hidden px-8 py-4 rounded-xl bg-gradient-to-r from-cyan-500 via-blue-500 to-indigo-600 text-white font-black text-base tracking-wide uppercase shadow-2xl shadow-cyan-500/40 hover:shadow-cyan-500/60 hover:scale-[1.02] active:scale-[0.98] transition-all cursor-pointer flex items-center justify-center gap-3"
              >
                <div className="absolute inset-0 bg-white/20 translate-y-full group-hover:translate-y-0 transition-transform duration-300"></div>
                <Download className="w-5 h-5 group-hover:animate-bounce" />
                <span>DOWNLOAD FILE (WATCH AD)</span>
              </button>

              {vault.files.length > 1 && (
                <button
                  onClick={() => onInitiateDownload(undefined)}
                  className="mt-2.5 w-full py-2.5 rounded-lg bg-slate-800/80 hover:bg-slate-700/80 text-xs font-semibold text-cyan-300 border border-cyan-500/20 transition-all flex items-center justify-center gap-2 cursor-pointer"
                >
                  <Layers className="w-3.5 h-3.5" />
                  <span>Download All {vault.files.length} Files</span>
                </button>
              )}

              <p className="mt-2.5 text-[11px] text-slate-400">
                ⚡ Tap Download to view a short sponsor ad. Your file will auto-download immediately after.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
