import React, { useState } from 'react';
import {
  UploadCloud,
  FileText,
  FileCode,
  Image as ImageIcon,
  CheckCircle2,
  Copy,
  Check,
  ExternalLink,
  Flame,
  Clock,
  Lock,
  Plus,
  Trash2,
  Sparkles,
  Zap,
  Code,
  Shield,
  QrCode
} from 'lucide-react';
import { FileItem } from '../types';
import { formatBytes } from '../utils/cryptoToken';

interface PushSimulatorProps {
  onVaultCreated: (vaultId: string, token: string) => void;
}

export const PushSimulator: React.FC<PushSimulatorProps> = ({ onVaultCreated }) => {
  const [filesList, setFilesList] = useState<Array<{ name: string; size: number; mimeType: string; data: string }>>([
    {
      name: 'invoice_march_user_992.pdf',
      size: 1024 * 180,
      mimeType: 'application/pdf',
      data: 'data:application/pdf;base64,JVBERi0xLjUKJcfsj6IKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovUGFnZXMgMiAwIFIKPj4KZW5kb2JqCg==',
    },
    {
      name: 'generated_user_certificate.png',
      size: 1024 * 512,
      mimeType: 'image/png',
      data: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
    },
  ]);

  const [securityMode, setSecurityMode] = useState<'onetime' | 'rotating' | 'protected'>('onetime');
  const [expiryHours, setExpiryHours] = useState<number>(24);
  const [senderAppName, setSenderAppName] = useState<string>('My External Client App');
  const [isPushing, setIsPushing] = useState<boolean>(false);
  const [pushResult, setPushResult] = useState<{
    vaultId: string;
    accessUrl: string;
    dynamicToken: string;
    rotatingSecret: string;
  } | null>(null);
  const [copiedLink, setCopiedLink] = useState(false);

  // File input handler for real user files
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    Array.from(files).forEach((file) => {
      const reader = new FileReader();
      reader.onload = (event) => {
        const result = event.target?.result as string;
        setFilesList((prev) => [
          ...prev,
          {
            name: file.name,
            size: file.size,
            mimeType: file.type || 'application/octet-stream',
            data: result || '',
          },
        ]);
      };
      reader.readAsDataURL(file);
    });
  };

  const removeFile = (index: number) => {
    setFilesList((prev) => prev.filter((_, idx) => idx !== index));
  };

  const addQuickMockFile = (type: 'pdf' | 'code' | 'image' | 'json') => {
    const randomSuffix = Math.floor(1000 + Math.random() * 9000);
    if (type === 'pdf') {
      setFilesList((prev) => [
        ...prev,
        {
          name: `Client_Document_${randomSuffix}.pdf`,
          size: 1024 * (120 + Math.floor(Math.random() * 500)),
          mimeType: 'application/pdf',
          data: 'data:application/pdf;base64,JVBERi0xLjUKJcfsj6IKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovUGFnZXMgMiAwIFIKPj4KZW5kb2JqCg==',
        },
      ]);
    } else if (type === 'code') {
      const codeText = `// Generated Script ${randomSuffix}\nexport function processData() {\n  return { status: 'authorized', timestamp: ${Date.now()} };\n}`;
      setFilesList((prev) => [
        ...prev,
        {
          name: `user_script_${randomSuffix}.ts`,
          size: codeText.length,
          mimeType: 'text/typescript',
          data: `data:text/typescript;base64,${btoa(codeText)}`,
        },
      ]);
    } else if (type === 'json') {
      const jsonText = JSON.stringify({ userId: `user_${randomSuffix}`, generatedAt: new Date().toISOString(), payload: 'Confidential Payload' }, null, 2);
      setFilesList((prev) => [
        ...prev,
        {
          name: `payload_${randomSuffix}.json`,
          size: jsonText.length,
          mimeType: 'application/json',
          data: `data:application/json;base64,${btoa(jsonText)}`,
        },
      ]);
    }
  };

  const handlePushFiles = async () => {
    if (filesList.length === 0) return;
    setIsPushing(true);
    setPushResult(null);

    try {
      const res = await fetch('/api/push', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          files: filesList,
          securityMode,
          burnOnDownload: securityMode === 'onetime',
          expiryHours,
          senderApp: senderAppName,
        }),
      });

      const data = await res.json();
      if (data.success) {
        setPushResult({
          vaultId: data.vaultId,
          accessUrl: data.accessUrl,
          dynamicToken: data.dynamicToken,
          rotatingSecret: data.rotatingSecret,
        });
      } else {
        alert(data.error || 'Failed to push files');
      }
    } catch (err: any) {
      console.error(err);
      alert('Failed to connect to SAIF AI push API');
    } finally {
      setIsPushing(false);
    }
  };

  const copyFullUrl = () => {
    if (!pushResult) return;
    const full = `${window.location.origin}${pushResult.accessUrl}`;
    navigator.clipboard.writeText(full);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      {/* Title & Explainer */}
      <div className="text-center mb-8">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/30 text-xs font-mono text-cyan-400 mb-3">
          <Zap className="w-3.5 h-3.5" />
          <span>ZERO-LIMIT DIRECT PUSH PORTAL</span>
        </div>
        <h2 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
          Push Files from Your App & Generate Unique Links
        </h2>
        <p className="mt-2 text-sm text-slate-400 max-w-xl mx-auto">
          Simulate how your external mobile app, backend server, or bot pushes files directly to SAIF AI and provides unique one-time links to thousands of concurrent users.
        </p>
      </div>

      {/* Main Form Container */}
      <div className="rounded-2xl bg-slate-900/70 border border-cyan-500/30 p-6 sm:p-8 backdrop-blur-xl shadow-xl">
        {/* Step 1: File Selection & Drop Area */}
        <div className="mb-6">
          <label className="block text-xs font-mono text-cyan-400 uppercase font-semibold mb-2">
            1. Attached Files (No Size Limit & Multi-File Support)
          </label>

          <div className="relative border-2 border-dashed border-cyan-500/30 rounded-xl p-6 text-center hover:border-cyan-400/60 transition-colors bg-black/30">
            <input
              type="file"
              multiple
              onChange={handleFileSelect}
              className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
            />
            <UploadCloud className="w-10 h-10 text-cyan-400 mx-auto mb-2" />
            <p className="text-sm font-semibold text-slate-200">
              Drag & Drop files here, or <span className="text-cyan-400 underline">browse your device</span>
            </p>
            <p className="text-xs text-slate-400 mt-1">
              Supports any format (PDF, PNG, MP4, ZIP, TS, JSON) with zero upload cap.
            </p>
          </div>

          {/* Quick Mock File Generators */}
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="text-[11px] text-slate-400 font-mono">Quick test presets:</span>
            <button
              type="button"
              onClick={() => addQuickMockFile('pdf')}
              className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 border border-slate-700 transition-colors flex items-center gap-1 cursor-pointer"
            >
              <Plus className="w-3 h-3 text-cyan-400" /> + Add Sample PDF
            </button>
            <button
              type="button"
              onClick={() => addQuickMockFile('code')}
              className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 border border-slate-700 transition-colors flex items-center gap-1 cursor-pointer"
            >
              <Plus className="w-3 h-3 text-purple-400" /> + Add Sample Code (.ts)
            </button>
            <button
              type="button"
              onClick={() => addQuickMockFile('json')}
              className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 border border-slate-700 transition-colors flex items-center gap-1 cursor-pointer"
            >
              <Plus className="w-3 h-3 text-emerald-400" /> + Add Sample JSON
            </button>
          </div>

          {/* Selected Files List */}
          {filesList.length > 0 && (
            <div className="mt-4 space-y-2">
              <span className="text-xs font-mono text-slate-400">
                Queued for Push ({filesList.length} files - {formatBytes(filesList.reduce((acc, f) => acc + f.size, 0))}):
              </span>
              <div className="max-h-48 overflow-y-auto space-y-1.5 pr-1">
                {filesList.map((file, idx) => (
                  <div
                    key={idx}
                    className="flex items-center justify-between p-2.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-300"
                  >
                    <div className="flex items-center gap-2 truncate pr-2">
                      <FileText className="w-4 h-4 text-cyan-400 shrink-0" />
                      <span className="truncate font-medium">{file.name}</span>
                      <span className="text-[10px] font-mono text-slate-400">({formatBytes(file.size)})</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => removeFile(idx)}
                      className="text-slate-400 hover:text-rose-400 p-1 transition-colors cursor-pointer"
                      title="Remove file"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Step 2: Security & Link Mode */}
        <div className="mb-6 grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-mono text-cyan-400 uppercase font-semibold mb-2">
              2. Security & Link Policy
            </label>
            <div className="space-y-2">
              <label
                onClick={() => setSecurityMode('onetime')}
                className={`flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-all ${
                  securityMode === 'onetime'
                    ? 'bg-rose-500/10 border-rose-500/40 text-white shadow-sm shadow-rose-500/20'
                    : 'bg-black/30 border-slate-800 text-slate-400 hover:text-slate-200'
                }`}
              >
                <Flame className={`w-5 h-5 shrink-0 mt-0.5 ${securityMode === 'onetime' ? 'text-rose-400' : 'text-slate-500'}`} />
                <div>
                  <div className="text-xs font-bold flex items-center gap-1.5">
                    <span>One-Time Link (OTP Style)</span>
                    <span className="text-[10px] font-mono px-1 rounded bg-rose-500/20 text-rose-300">BURNS</span>
                  </div>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Single use only. Once the recipient downloads the file, link is permanently burned from the server.
                  </p>
                </div>
              </label>

              <label
                onClick={() => setSecurityMode('rotating')}
                className={`flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-all ${
                  securityMode === 'rotating'
                    ? 'bg-cyan-500/10 border-cyan-500/40 text-white shadow-sm shadow-cyan-500/20'
                    : 'bg-black/30 border-slate-800 text-slate-400 hover:text-slate-200'
                }`}
              >
                <Clock className={`w-5 h-5 shrink-0 mt-0.5 ${securityMode === 'rotating' ? 'text-cyan-400' : 'text-slate-500'}`} />
                <div>
                  <div className="text-xs font-bold flex items-center gap-1.5">
                    <span>Dynamic Rotating Token</span>
                    <span className="text-[10px] font-mono px-1 rounded bg-cyan-500/20 text-cyan-300">ROTATES 30s</span>
                  </div>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Link token rotates dynamically every 30 seconds. Prevents scraping, sharing, and stale URL leaks.
                  </p>
                </div>
              </label>
            </div>
          </div>

          <div>
            <label className="block text-xs font-mono text-cyan-400 uppercase font-semibold mb-2">
              3. Link Expiry & Origin App
            </label>
            <div className="space-y-3">
              <div>
                <label className="block text-[11px] text-slate-400 mb-1">Expiration Window:</label>
                <select
                  value={expiryHours}
                  onChange={(e) => setExpiryHours(Number(e.target.value))}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500"
                >
                  <option value={1}>1 Hour (Ephemeral)</option>
                  <option value={24}>24 Hours (Standard)</option>
                  <option value={72}>72 Hours (Extended)</option>
                  <option value={0}>No Expiry (Until downloaded/burned)</option>
                </select>
              </div>

              <div>
                <label className="block text-[11px] text-slate-400 mb-1">Your Origin App Identifier:</label>
                <input
                  type="text"
                  value={senderAppName}
                  onChange={(e) => setSenderAppName(e.target.value)}
                  placeholder="e.g. My Flutter App, Telegram Bot, AI Engine"
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-cyan-500"
                />
              </div>
            </div>
          </div>
        </div>

        {/* Action Button: Push to Vault */}
        <div className="pt-2">
          <button
            onClick={handlePushFiles}
            disabled={isPushing || filesList.length === 0}
            className={`w-full py-4 rounded-xl font-bold text-sm flex items-center justify-center gap-2 transition-all cursor-pointer ${
              filesList.length === 0
                ? 'bg-slate-800 text-slate-500 cursor-not-allowed'
                : 'bg-gradient-to-r from-cyan-500 via-blue-500 to-indigo-600 text-white shadow-lg shadow-cyan-500/25 hover:shadow-cyan-500/40 hover:scale-[1.01]'
            }`}
          >
            {isPushing ? (
              <>
                <div className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin"></div>
                <span>Pushing to SAIF AI Vault & Generating One-Time Link...</span>
              </>
            ) : (
              <>
                <Zap className="w-4 h-4" />
                <span>PUSH {filesList.length} FILE(S) & GENERATE UNIQUE RECIPIENT LINK</span>
              </>
            )}
          </button>
        </div>

        {/* PUSH SUCCESS RESULT BOX */}
        {pushResult && (
          <div className="mt-6 p-5 rounded-xl bg-gradient-to-br from-cyan-950/40 via-slate-900 to-blue-950/40 border border-cyan-400/50 shadow-xl animate-fadeIn">
            <div className="flex items-center gap-2 text-emerald-400 font-mono text-xs font-bold mb-2">
              <CheckCircle2 className="w-4 h-4" />
              <span>FILES PUSHED SUCCESSFULLY! UNIQUE LINK READY</span>
            </div>

            <p className="text-xs text-slate-300 mb-3">
              Your recipient can now open this link. Only they can see these files.
            </p>

            <div className="flex items-center gap-2 p-2.5 rounded-lg bg-black/60 border border-slate-800">
              <span className="font-mono text-xs text-cyan-300 truncate flex-1 select-all">
                {window.location.origin}{pushResult.accessUrl}
              </span>
              <button
                onClick={copyFullUrl}
                className="px-3 py-1.5 rounded-md bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs flex items-center gap-1 transition-all cursor-pointer shrink-0"
              >
                {copiedLink ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedLink ? 'Copied' : 'Copy Link'}</span>
              </button>
            </div>

            <div className="mt-4 flex flex-wrap items-center justify-between gap-3 pt-3 border-t border-slate-800">
              <div className="text-[11px] font-mono text-slate-400 space-y-0.5">
                <div>VAULT ID: <span className="text-slate-200">{pushResult.vaultId}</span></div>
                <div>INITIAL TOKEN: <span className="text-cyan-400">{pushResult.dynamicToken}</span></div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => onVaultCreated(pushResult.vaultId, pushResult.dynamicToken)}
                  className="px-4 py-2 rounded-lg bg-cyan-500/20 hover:bg-cyan-500/30 text-cyan-300 border border-cyan-500/40 text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer"
                >
                  <span>Open in File Vault View</span>
                  <ExternalLink className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
