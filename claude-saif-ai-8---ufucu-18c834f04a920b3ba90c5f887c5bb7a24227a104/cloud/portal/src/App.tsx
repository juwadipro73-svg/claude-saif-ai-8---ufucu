/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import { Navbar } from './components/Navbar';
import { FileVaultCenter } from './components/FileVaultCenter';
import { AdModalGateway } from './components/AdModalGateway';
import {
  TopLeaderboardAd,
  LeftSkyscraperAd,
  RightSkyscraperAd,
  InContentBannerAd,
} from './components/SponsorBanners';
import { VaultData, FileItem } from './types';

export default function App() {
  const [vault, setVault] = useState<VaultData | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [vaultError, setVaultError] = useState<string | null>(null);

  // Ad Gateway Modal state
  const [isAdModalOpen, setIsAdModalOpen] = useState<boolean>(false);
  const [targetDownloadFile, setTargetDownloadFile] = useState<FileItem | undefined>(undefined);

  // Fetch Vault Data based on URL query (?v=...&token=...) or default demo
  const loadVaultFromUrl = useCallback(async () => {
    setIsLoading(true);
    setVaultError(null);

    const urlParams = new URLSearchParams(window.location.search);
    const vaultIdParam = urlParams.get('v') || 'saif_demo_vault_101';
    const tokenParam = urlParams.get('token') || '';

    try {
      const query = tokenParam ? `?token=${encodeURIComponent(tokenParam)}` : '';
      const res = await fetch(`/api/vault/${vaultIdParam}${query}`);
      const data = await res.json();

      if (!res.ok) {
        setVaultError(data.message || 'Vault could not be decrypted or link was already burned.');
        setVault(null);
      } else {
        setVault(data);
      }
    } catch (err: any) {
      setVaultError('Unable to connect to SAIF AI vault network.');
      setVault(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadVaultFromUrl();
  }, [loadVaultFromUrl]);

  // When user taps Download on a file
  const handleInitiateDownload = (file?: FileItem) => {
    setTargetDownloadFile(file);
    setIsAdModalOpen(true);
  };

  // When Ad completes and triggers auto-download
  const handleAdCompleted = async () => {
    if (!vault) return;

    try {
      const res = await fetch(`/api/vault/${vault.vaultId}/ad-complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ claim: vault.currentDynamicToken || vault.vaultId }),
      });

      // Gateway mode: server streams the file directly with Content-Disposition
      if (res.headers.get('content-disposition')) {
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        // Extract filename from Content-Disposition header
        const disp = res.headers.get('content-disposition') || '';
        const match = disp.match(/filename="?([^";]+)"?/);
        a.download = match?.[1] || targetDownloadFile?.name || 'saif-ai-file';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        setVault((prev) => (prev ? { ...prev, isBurned: true } : null));
        return;
      }

      // Demo mode: server returns JSON with base64 data
      const data = await res.json();
      if (data.isBurned) {
        setVault((prev) => (prev ? { ...prev, isBurned: true } : null));
      }
      // Trigger download for each file in demo mode
      if (Array.isArray(data.files)) {
        data.files.forEach((f: { name: string; mimeType: string; data: string }) => {
          const link = document.createElement('a');
          link.download = f.name;
          if (f.data?.startsWith('data:')) {
            link.href = f.data;
          } else {
            const blob = new Blob([f.data || ''], { type: f.mimeType || 'application/octet-stream' });
            link.href = URL.createObjectURL(blob);
          }
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);
        });
      }
    } catch (e) {
      console.error('Failed to complete download', e);
    }
  };

  return (
    <div className="min-h-screen bg-[#05070f] text-slate-100 flex flex-col selection:bg-cyan-500 selection:text-black">
      {/* Clean Top Navbar */}
      <Navbar />

      {/* Top Leaderboard Fake Ad Banner */}
      <TopLeaderboardAd />

      {/* Main Single-Page Download Centerpiece with Side Ads */}
      <div className="flex-1 flex justify-center items-start max-w-[1500px] w-full mx-auto px-2 sm:px-4 pb-12 relative">
        {/* Left Skyscraper Fake Ad Banner */}
        <LeftSkyscraperAd />

        {/* Center Container: Single Dedicated File Download Portal */}
        <main className="flex-1 max-w-4xl w-full flex flex-col items-center">
          <FileVaultCenter
            vault={vault}
            isLoading={isLoading}
            error={vaultError}
            onInitiateDownload={handleInitiateDownload}
            onRefreshVault={loadVaultFromUrl}
          />

          {/* In-Content Fake Ad Banner under the file */}
          <InContentBannerAd />
        </main>

        {/* Right Skyscraper Fake Ad Banner */}
        <RightSkyscraperAd />
      </div>

      {/* Download Ad Gateway Modal (Plays fake ad on download) */}
      <AdModalGateway
        isOpen={isAdModalOpen}
        onClose={() => setIsAdModalOpen(false)}
        targetFile={targetDownloadFile}
        allFiles={vault?.files}
        adDurationSeconds={5}
        autoDownloadOnFinish={true}
        onAdCompleted={handleAdCompleted}
        isOneTime={vault?.securityMode === 'onetime' || vault?.burnOnDownload}
      />

      {/* Minimal Footer */}
      <footer className="border-t border-slate-900 bg-slate-950/80 py-4 px-4 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="font-extrabold text-slate-300">SAIF AI DOWNLOADS</span>
            <span>•</span>
            <span>Direct Push Delivery Platform</span>
          </div>
          <div className="text-[11px] text-slate-500 font-mono">
            Protected by SAIF AI Multi-Tenant Shield • Replace fake ads with real ad network tags anytime.
          </div>
        </div>
      </footer>
    </div>
  );
}
