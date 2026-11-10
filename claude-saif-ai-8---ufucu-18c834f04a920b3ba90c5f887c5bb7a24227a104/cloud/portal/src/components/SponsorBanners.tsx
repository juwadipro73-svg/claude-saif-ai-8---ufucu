import React, { useState } from 'react';
import { ExternalLink, X, Shield, Zap, Sparkles, Download, ArrowRight, Star } from 'lucide-react';

/**
 * Top Leaderboard Banner Ad (728x90 standard style)
 * You can easily replace the inner contents with your Google AdSense / network ad tag
 */
export const TopLeaderboardAd: React.FC = () => {
  return (
    <div className="w-full max-w-4xl mx-auto my-3 px-4">
      {/* <!-- AD CONTAINER: Replace with AdSense slot --> */}
      <div className="relative rounded-xl overflow-hidden bg-gradient-to-r from-indigo-950/80 via-purple-950/80 to-blue-950/80 border border-purple-500/30 p-3 sm:p-4 shadow-lg flex flex-col sm:flex-row items-center justify-between gap-3 group">
        <div className="absolute top-1 right-2 text-[9px] font-mono tracking-widest uppercase px-1.5 py-0.5 rounded bg-black/60 text-purple-300 border border-purple-500/20">
          Advertisement
        </div>

        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-lg bg-gradient-to-tr from-amber-400 to-orange-500 flex items-center justify-center text-slate-950 font-black shadow-md shadow-orange-500/30 group-hover:scale-105 transition-transform">
            <Zap className="w-6 h-6 text-slate-950 fill-slate-950" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-extrabold text-white">QuantumVPN & Speed Shield 2026</span>
              <span className="hidden md:inline-flex items-center text-[10px] text-amber-400 font-bold bg-amber-500/15 px-1.5 py-0.5 rounded">
                ★ 4.9 RATED
              </span>
            </div>
            <p className="text-xs text-slate-300 mt-0.5">
              Unlock 10x faster download speed and 100% military grade encryption on all links.
            </p>
          </div>
        </div>

        <a
          href="https://google.com"
          target="_blank"
          rel="noopener noreferrer"
          className="px-4 py-2 rounded-lg bg-gradient-to-r from-amber-400 to-orange-500 hover:from-amber-300 hover:to-orange-400 text-slate-950 font-extrabold text-xs tracking-wide uppercase transition-all shadow-md shadow-orange-500/20 flex items-center gap-1.5 shrink-0"
        >
          <span>Install Free</span>
          <ArrowRight className="w-3.5 h-3.5" />
        </a>
      </div>
    </div>
  );
};

/**
 * Left Sidebar Skyscraper Ad (160x600 style banner)
 * Sticky on large desktop displays
 */
export const LeftSkyscraperAd: React.FC = () => {
  return (
    <aside className="hidden xl:flex flex-col w-44 shrink-0 space-y-4 pt-4">
      {/* <!-- AD CONTAINER LEFT --> */}
      <div className="sticky top-20 rounded-xl bg-slate-900/90 border border-cyan-500/30 p-4 shadow-xl flex flex-col items-center text-center overflow-hidden">
        <span className="self-end text-[9px] font-mono uppercase px-1 rounded bg-black/60 text-cyan-400 mb-2">
          Ad
        </span>

        <div className="w-14 h-14 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center mb-3">
          <Download className="w-7 h-7 text-cyan-400 animate-bounce" />
        </div>

        <h4 className="text-xs font-black text-white uppercase tracking-wider">
          Download Accelerator
        </h4>
        <p className="text-[11px] text-slate-400 mt-2 leading-relaxed">
          Boost your download speed by up to 500% with multi-threaded P2P acceleration.
        </p>

        <div className="my-3 py-1.5 px-3 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-[10px] font-mono text-emerald-400 font-bold">
          100% Free
        </div>

        <a
          href="https://google.com"
          target="_blank"
          rel="noopener noreferrer"
          className="w-full py-2.5 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs uppercase transition-all shadow-md shadow-cyan-500/20"
        >
          Download Now
        </a>

        <div className="mt-4 pt-3 border-t border-slate-800 text-[10px] text-slate-500 font-mono">
          Compatible with Windows, Mac, Android, iOS
        </div>
      </div>
    </aside>
  );
};

/**
 * Right Sidebar Skyscraper Ad (160x600 style banner)
 */
export const RightSkyscraperAd: React.FC = () => {
  return (
    <aside className="hidden xl:flex flex-col w-44 shrink-0 space-y-4 pt-4">
      {/* <!-- AD CONTAINER RIGHT --> */}
      <div className="sticky top-20 rounded-xl bg-slate-900/90 border border-rose-500/30 p-4 shadow-xl flex flex-col items-center text-center overflow-hidden">
        <span className="self-end text-[9px] font-mono uppercase px-1 rounded bg-black/60 text-rose-400 mb-2">
          Ad
        </span>

        <div className="w-14 h-14 rounded-2xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-center mb-3">
          <Shield className="w-7 h-7 text-rose-400" />
        </div>

        <h4 className="text-xs font-black text-white uppercase tracking-wider">
          CyberShield Pro 2026
        </h4>
        <p className="text-[11px] text-slate-400 mt-2 leading-relaxed">
          Real-time virus scanner & phishing shield for all your web downloads.
        </p>

        <div className="my-3 py-1.5 px-3 rounded-full bg-rose-500/10 border border-rose-500/30 text-[10px] font-mono text-rose-400 font-bold">
          Free 30-Day Pass
        </div>

        <a
          href="https://google.com"
          target="_blank"
          rel="noopener noreferrer"
          className="w-full py-2.5 rounded-lg bg-rose-500 hover:bg-rose-400 text-white font-bold text-xs uppercase transition-all shadow-md shadow-rose-500/20"
        >
          Scan PC Now
        </a>

        <div className="mt-4 pt-3 border-t border-slate-800 text-[10px] text-slate-500 font-mono">
          Safe & Verified • Zero Spyware
        </div>
      </div>
    </aside>
  );
};

/**
 * In-Content Responsive Banner Ad (Placed below the central download box)
 */
export const InContentBannerAd: React.FC = () => {
  return (
    <div className="w-full max-w-2xl mx-auto my-6 px-2">
      <div className="relative rounded-xl bg-gradient-to-r from-emerald-950/60 via-slate-900 to-teal-950/60 border border-emerald-500/30 p-4 shadow-xl flex flex-col sm:flex-row items-center justify-between gap-3">
        <span className="absolute top-1.5 right-2 text-[9px] font-mono uppercase px-1.5 py-0.5 rounded bg-black/60 text-emerald-400 border border-emerald-500/20">
          Sponsored Link
        </span>

        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center text-emerald-400">
            <Sparkles className="w-5 h-5" />
          </div>
          <div>
            <div className="text-xs sm:text-sm font-bold text-white">
              Cloud Storage Pro - 100GB Free Cloud Drive
            </div>
            <p className="text-[11px] text-slate-400">
              Instant sync, auto-backup, and encrypted file sharing for developers.
            </p>
          </div>
        </div>

        <a
          href="https://google.com"
          target="_blank"
          rel="noopener noreferrer"
          className="px-4 py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs uppercase tracking-wide transition-all shrink-0"
        >
          Claim 100GB
        </a>
      </div>
    </div>
  );
};
