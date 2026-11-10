import React, { useState, useEffect } from 'react';
import {
  Sparkles,
  ExternalLink,
  CheckCircle2,
  Download,
  Shield,
  Zap,
  Volume2,
  VolumeX,
  Play,
  ArrowRight,
  Flame,
} from 'lucide-react';
import confetti from 'canvas-confetti';
import { FileItem } from '../types';

interface AdModalGatewayProps {
  isOpen: boolean;
  onClose: () => void;
  targetFile?: FileItem;
  allFiles?: FileItem[];
  adDurationSeconds?: number;
  autoDownloadOnFinish?: boolean;
  onAdCompleted: () => void;
  isOneTime?: boolean;
}

export const AdModalGateway: React.FC<AdModalGatewayProps> = ({
  isOpen,
  onClose,
  targetFile,
  allFiles,
  adDurationSeconds = 5,
  autoDownloadOnFinish = true,
  onAdCompleted,
  isOneTime = true,
}) => {
  const [countdown, setCountdown] = useState<number>(adDurationSeconds);
  const [isAdFinished, setIsAdFinished] = useState<boolean>(false);
  const [downloadTriggered, setDownloadTriggered] = useState<boolean>(false);
  const [soundMuted, setSoundMuted] = useState<boolean>(true);

  // Reset states when modal opens
  useEffect(() => {
    if (isOpen) {
      setCountdown(adDurationSeconds);
      setIsAdFinished(false);
      setDownloadTriggered(false);
    }
  }, [isOpen, adDurationSeconds]);

  // Countdown timer logic
  useEffect(() => {
    if (!isOpen || isAdFinished) return;

    if (countdown > 0) {
      const timer = setTimeout(() => {
        setCountdown((prev) => prev - 1);
      }, 1000);
      return () => clearTimeout(timer);
    } else {
      setIsAdFinished(true);
      try {
        confetti({
          particleCount: 50,
          spread: 60,
          origin: { y: 0.6 },
          colors: ['#06b6d4', '#3b82f6', '#a855f7'],
        });
      } catch (e) {
        // ignore confetti errors
      }

      if (autoDownloadOnFinish) {
        // Automatically start file download after 600ms
        const autoTimer = setTimeout(() => {
          handleExecuteDownload();
        }, 600);
        return () => clearTimeout(autoTimer);
      }
    }
  }, [isOpen, countdown, isAdFinished, autoDownloadOnFinish]);

  const handleExecuteDownload = () => {
    if (downloadTriggered) return;
    setDownloadTriggered(true);

    const filesToDownload = targetFile ? [targetFile] : (allFiles || []);

    // In gateway/production mode, file.data is absent — the parent (App.tsx)
    // handles the streaming download after the claim is consumed.
    const isGatewayMode = filesToDownload.some((f) => !f.data);
    if (isGatewayMode) {
      onAdCompleted();
      return;
    }

    // Demo mode: file.data contains base64 / data-URI content — download client-side.
    filesToDownload.forEach((file) => {
      const link = document.createElement('a');
      link.download = file.name;

      if (file.data.startsWith('data:')) {
        link.href = file.data;
      } else {
        const blob = new Blob([file.data], { type: file.mimeType || 'application/octet-stream' });
        link.href = URL.createObjectURL(blob);
      }

      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    });

    // Notify parent to burn one-time token or mark completed
    onAdCompleted();
  };

  if (!isOpen) return null;

  const progressPercentage = Math.min(
    100,
    Math.round(((adDurationSeconds - countdown) / adDurationSeconds) * 100)
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn">
      <div className="relative w-full max-w-xl rounded-2xl bg-[#090d1a] border border-cyan-500/40 shadow-2xl shadow-cyan-500/20 overflow-hidden">
        {/* Top Header */}
        <div className="px-5 py-3.5 bg-slate-900/90 border-b border-cyan-500/20 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="flex h-2.5 w-2.5 relative">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-cyan-500"></span>
            </span>
            <span className="text-xs font-mono font-bold tracking-wider text-cyan-400 uppercase">
              SAIF AI Verified Sponsor Gateway
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setSoundMuted(!soundMuted)}
              className="p-1 rounded text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
              title={soundMuted ? 'Unmute' : 'Mute'}
            >
              {soundMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4 text-cyan-400" />}
            </button>
            <span className="text-[11px] font-mono text-slate-400">
              AD #4088
            </span>
            <button
              onClick={onClose}
              className="ml-1 p-1 rounded-md text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
              title="Close Ad Gateway"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Ad Video / Creative Showcase */}
        <div className="relative p-6 bg-gradient-to-br from-slate-950 via-[#070b18] to-slate-900 overflow-hidden">
          {/* Cyber animated grid background */}
          <div className="absolute inset-0 opacity-15 bg-[radial-gradient(#38bdf8_1px,transparent_1px)] [background-size:16px_16px]"></div>

          <div className="relative z-10 flex flex-col items-center text-center">
            {/* Sponsor Badge */}
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-r from-cyan-500/20 to-blue-500/20 border border-cyan-500/40 text-[11px] font-mono text-cyan-300 mb-3">
              <Zap className="w-3.5 h-3.5 text-cyan-400" />
              <span>OFFICIAL SPONSOR OF SAIF AI FREE PUSH VAULT</span>
            </div>

            {/* Ad Headline */}
            <h3 className="text-xl sm:text-2xl font-black text-white tracking-tight leading-snug">
              SAIF AI Neural Cloud Platform
            </h3>
            <p className="mt-2 text-xs sm:text-sm text-slate-300 max-w-md">
              Deploy high-performance AI models, secure file distribution APIs, and quantum-safe webhooks with zero cold starts.
            </p>

            {/* Interactive Ad Graphic / Mock Video Display */}
            <div className="mt-5 w-full rounded-xl bg-slate-900/90 border border-slate-700/80 p-4 shadow-inner relative overflow-hidden group">
              <div className="flex items-center justify-between text-[11px] font-mono text-slate-400 border-b border-slate-800 pb-2 mb-3">
                <span className="flex items-center gap-1 text-cyan-400">
                  <Play className="w-3 h-3 fill-cyan-400" />
                  Live Preview: Enterprise Push SDK v3.2
                </span>
                <span className="text-emerald-400">99.999% SLA</span>
              </div>

              <div className="text-left font-mono text-xs text-slate-300 bg-black/60 rounded p-3 space-y-1">
                <p className="text-cyan-400 font-bold">// 1-Line App Push Integration</p>
                <p className="text-slate-400">const vault = await saifAI.push(&#123;</p>
                <p className="text-purple-300 pl-4">file: payload,</p>
                <p className="text-emerald-300 pl-4">security: 'onetime_otp_burn'</p>
                <p className="text-slate-400">&#125;);</p>
                <p className="text-amber-300">// Returns: https://saif.ai/v/unique_otp</p>
              </div>

              <div className="mt-3 flex items-center justify-between pt-2">
                <span className="text-[11px] text-slate-400">Free Developer Tier Available</span>
                <a
                  href="https://ai.google.dev/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-cyan-400 font-semibold hover:underline"
                >
                  Explore Documentation <ExternalLink className="w-3 h-3" />
                </a>
              </div>
            </div>
          </div>
        </div>

        {/* Bottom Gateway Control & Auto-Download Progress */}
        <div className="p-5 bg-slate-900/95 border-t border-slate-800">
          {/* Progress Bar & Status */}
          <div className="mb-4">
            <div className="flex items-center justify-between text-xs font-mono mb-1.5">
              <span className="text-slate-300 flex items-center gap-1.5">
                {isAdFinished ? (
                  <span className="text-emerald-400 font-bold flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    SPONSOR VERIFIED
                  </span>
                ) : (
                  <span className="text-cyan-400">
                    Watching Sponsor Message... ({countdown}s)
                  </span>
                )}
              </span>
              <span className="text-slate-400 font-semibold">
                {progressPercentage}%
              </span>
            </div>

            <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-cyan-500 to-blue-500 transition-all duration-300 ease-out"
                style={{ width: `${progressPercentage}%` }}
              ></div>
            </div>
          </div>

          {/* Action button */}
          <div className="flex items-center justify-between gap-3">
            <span className="text-[11px] text-slate-400 max-w-[220px]">
              {isAdFinished
                ? downloadTriggered
                  ? 'Download triggered! Check your downloads.'
                  : 'Ad finished! Ready to save file.'
                : 'Free service supported by sponsors.'}
            </span>

            {isAdFinished ? (
              <div className="flex items-center gap-2">
                <button
                  onClick={onClose}
                  className="px-3.5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold text-xs border border-slate-700 transition-all cursor-pointer"
                >
                  Close & View Status
                </button>
                <button
                  onClick={handleExecuteDownload}
                  className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 text-white font-bold text-xs sm:text-sm shadow-lg shadow-emerald-500/25 hover:shadow-emerald-500/40 hover:scale-[1.02] active:scale-[0.98] transition-all cursor-pointer flex items-center gap-2"
                >
                  <Download className="w-4 h-4 animate-bounce" />
                  <span>{downloadTriggered ? 'DOWNLOAD AGAIN' : 'AUTO-DOWNLOADING FILE'}</span>
                </button>
              </div>
            ) : (
              <button
                disabled
                className="px-5 py-2.5 rounded-xl bg-slate-800 text-slate-500 font-semibold text-xs sm:text-sm border border-slate-700/60 cursor-not-allowed flex items-center gap-2"
              >
                <span>Reward unlocks in {countdown}s</span>
              </button>
            )}
          </div>

          {/* One-Time link warning */}
          {isOneTime && (
            <div className="mt-3 pt-3 border-t border-slate-800/80 flex items-center gap-1.5 text-[11px] text-amber-400/90 font-mono">
              <Flame className="w-3.5 h-3.5 text-amber-400 shrink-0" />
              <span>Notice: Once downloaded, this one-time link dissolves immediately from server.</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
