import React, { useState, useEffect, useRef } from 'react';
import {
  Sparkles,
  Cpu,
  Download,
  CheckCircle2,
  Clock,
  ExternalLink,
  Key,
  Flame,
  AlertCircle,
  Copy,
  Check,
  RefreshCw,
  FolderGit2,
  HardDrive
} from 'lucide-react';
import confetti from 'canvas-confetti';
import { FirebaseAppRecord } from '../types';

interface AppBuilderSectionProps {
  onDownloadWithAd?: (downloadUrl: string, fileName: string) => void;
}

export const AppBuilderSection: React.FC<AppBuilderSectionProps> = ({ onDownloadWithAd }) => {
  const [prompt, setPrompt] = useState<string>('');
  const [githubPat, setGithubPat] = useState<string>(() => {
    return localStorage.getItem('saif_ai_github_pat') || '';
  });
  const [isPatConfigOpen, setIsPatConfigOpen] = useState<boolean>(false);

  // Build Status: 'idle' | 'triggering' | 'building' | 'ready' | 'error'
  const [buildStatus, setBuildStatus] = useState<'idle' | 'triggering' | 'building' | 'ready' | 'error'>('idle');
  const [statusMessage, setStatusMessage] = useState<string>('');
  const [latestAppRecord, setLatestAppRecord] = useState<FirebaseAppRecord | null>(null);
  const [copiedLink, setCopiedLink] = useState<boolean>(false);

  const pollingIntervalRef = useRef<any>(null);
  const buildStartTimeRef = useRef<number>(0);

  // Save PAT to localStorage
  const handleSavePat = (val: string) => {
    setGithubPat(val);
    localStorage.setItem('saif_ai_github_pat', val.trim());
  };

  /**
   * TASK 2 & 3: Listen to Firebase Realtime Database
   * Firebase DB: https://saif-ai-2e541-default-rtdb.firebaseio.com/apps.json
   */
  const checkFirebaseDatabase = async (expectedPrompt?: string): Promise<FirebaseAppRecord | null> => {
    try {
      // Direct REST call to Firebase Realtime DB
      const firebaseDbUrl = 'https://saif-ai-2e541-default-rtdb.firebaseio.com/apps.json';
      const res = await fetch(firebaseDbUrl);
      if (!res.ok) {
        // Fallback to our server proxy if browser encounters any network block
        const proxyRes = await fetch('/api/firebase-latest');
        if (proxyRes.ok) {
          const proxyData = await proxyRes.json();
          return parseFirebaseData(proxyData.data, expectedPrompt);
        }
        return null;
      }

      const data = await res.json();
      return parseFirebaseData(data, expectedPrompt);
    } catch (err) {
      console.warn('Direct Firebase fetch error, trying server proxy:', err);
      try {
        const proxyRes = await fetch('/api/firebase-latest');
        if (proxyRes.ok) {
          const proxyData = await proxyRes.json();
          return parseFirebaseData(proxyData.data, expectedPrompt);
        }
      } catch (e) {
        console.error('All Firebase fetches failed:', e);
      }
      return null;
    }
  };

  // Helper to parse Firebase Realtime DB object or map
  const parseFirebaseData = (data: any, expectedPrompt?: string): FirebaseAppRecord | null => {
    if (!data) return null;

    let targetRecord: FirebaseAppRecord | null = null;

    if (data.appName && data.driveFileId) {
      targetRecord = {
        appName: data.appName,
        driveFileId: data.driveFileId,
        status: data.status || 'Ready to Download',
      };
    } else if (typeof data === 'object') {
      // If Firebase stores a list/dictionary of apps, get the newest entry
      const keys = Object.keys(data);
      if (keys.length > 0) {
        // Check for matching prompt or pick the latest key
        for (let i = keys.length - 1; i >= 0; i--) {
          const item = data[keys[i]];
          if (item && item.driveFileId) {
            targetRecord = {
              appName: item.appName || 'Saif AI Generated App',
              driveFileId: item.driveFileId,
              status: item.status || 'Ready to Download',
            };
            if (expectedPrompt && item.appName?.toLowerCase().includes(expectedPrompt.toLowerCase())) {
              break;
            }
          }
        }
      }
    }

    return targetRecord;
  };

  /**
   * Start polling Firebase Realtime DB while building
   */
  const startFirebaseListener = (activePrompt: string) => {
    // Clear any previous interval
    if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);

    pollingIntervalRef.current = setInterval(async () => {
      const record = await checkFirebaseDatabase(activePrompt);

      if (record && record.driveFileId) {
        const isReady =
          record.status.toLowerCase().includes('ready') ||
          record.status.toLowerCase().includes('download') ||
          record.driveFileId.length > 5;

        if (isReady) {
          setLatestAppRecord(record);
          setBuildStatus('ready');
          setStatusMessage('Ready to Download! Your APK is compiled and ready.');
          clearInterval(pollingIntervalRef.current);
          pollingIntervalRef.current = null;

          try {
            confetti({
              particleCount: 60,
              spread: 70,
              origin: { y: 0.6 },
              colors: ['#06b6d4', '#3b82f6', '#10b981'],
            });
          } catch (e) {
            // ignore
          }
        }
      }

      // Check timeout (after 8 minutes)
      if (Date.now() - buildStartTimeRef.current > 8 * 60 * 1000) {
        clearInterval(pollingIntervalRef.current);
        pollingIntervalRef.current = null;
        if (buildStatus === 'building') {
          setStatusMessage('Build taking longer than usual. Please check GitHub Actions tab.');
        }
      }
    }, 4000); // Check every 4 seconds
  };

  /**
   * TASK 1: Trigger GitHub Action workflow dispatch
   * Repository: shahudgg/SaifAI-Builder, Workflow: build.yml, Input: prompt
   */
  const handleBuildApp = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!prompt.trim()) {
      setStatusMessage('Please enter an app prompt or description.');
      setBuildStatus('error');
      return;
    }

    setBuildStatus('triggering');
    setStatusMessage('Connecting to GitHub Actions (shahudgg/SaifAI-Builder)...');
    buildStartTimeRef.current = Date.now();

    const patToUse = githubPat.trim();

    try {
      // 1. First attempt: call server-side proxy /api/trigger-build (prevents browser CORS)
      const res = await fetch('/api/trigger-build', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: prompt.trim(),
          githubToken: patToUse,
          branch: 'main',
        }),
      });

      const data = await res.json();

      if (res.ok && data.success) {
        setBuildStatus('building');
        setStatusMessage('Building... Please wait (GitHub Actions is compiling your APK)');
        startFirebaseListener(prompt.trim());
        return;
      }

      // If missing token or server reported missing token
      if (data.error === 'MISSING_GITHUB_TOKEN') {
        setIsPatConfigOpen(true);
        setBuildStatus('error');
        setStatusMessage('GitHub Personal Access Token required to trigger workflow. Please enter your PAT below.');
        return;
      }

      // 2. Fallback: try direct browser fetch to GitHub API
      if (patToUse) {
        const githubDirectRes = await fetch(
          'https://api.github.com/repos/shahudgg/SaifAI-Builder/actions/workflows/build.yml/dispatches',
          {
            method: 'POST',
            headers: {
              'Accept': 'application/vnd.github.v3+json',
              'Authorization': `Bearer ${patToUse}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              ref: 'main',
              inputs: {
                prompt: prompt.trim(),
              },
            }),
          }
        );

        if (githubDirectRes.status === 204) {
          setBuildStatus('building');
          setStatusMessage('Building... Please wait (GitHub Actions is compiling your APK)');
          startFirebaseListener(prompt.trim());
          return;
        }
      }

      setBuildStatus('error');
      setStatusMessage(data.message || 'Failed to trigger build. Check your GitHub PAT permissions (needs "repo" / "workflow" scope).');
    } catch (err: any) {
      console.error('Trigger build error:', err);
      setBuildStatus('error');
      setStatusMessage(`Error: ${err.message || 'Could not connect to GitHub API'}`);
    }
  };

  // Initial check on mount: see if an app was already completed in Firebase
  useEffect(() => {
    checkFirebaseDatabase().then((rec) => {
      if (rec && rec.driveFileId) {
        setLatestAppRecord(rec);
      }
    });

    return () => {
      if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);
    };
  }, []);

  /**
   * TASK 3: Direct Download Link generation
   * Format: https://drive.google.com/uc?export=download&id=YOUR_FILE_ID
   */
  const directDownloadUrl = latestAppRecord?.driveFileId
    ? `https://drive.google.com/uc?export=download&id=${latestAppRecord.driveFileId}`
    : '#';

  const copyDownloadUrl = () => {
    if (!directDownloadUrl || directDownloadUrl === '#') return;
    navigator.clipboard.writeText(directDownloadUrl);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  };

  return (
    <div className="w-full max-w-3xl mx-auto px-4 my-6">
      <div className="relative rounded-2xl bg-[#090d1a]/95 border border-cyan-500/40 p-6 sm:p-8 shadow-2xl overflow-hidden backdrop-blur-xl">
        {/* Glow backdrop */}
        <div className="absolute -inset-1 rounded-3xl bg-gradient-to-r from-cyan-500/20 via-blue-500/10 to-purple-500/20 blur-xl opacity-70 pointer-events-none"></div>

        {/* Header Badge */}
        <div className="relative z-10 flex flex-wrap items-center justify-between gap-3 mb-6 pb-4 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/30">
              <Cpu className="w-5 h-5 animate-pulse" />
            </div>
            <div>
              <h3 className="text-base font-extrabold text-white flex items-center gap-2">
                <span>SAIF AI Autonomous App Builder</span>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                  GITHUB + FIREBASE INTEGRATED
                </span>
              </h3>
              <p className="text-xs text-slate-400">
                Trigger GitHub Actions (<code className="text-cyan-300 font-mono">shahudgg/SaifAI-Builder</code>) & listen to Firebase Realtime DB
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setIsPatConfigOpen(!isPatConfigOpen)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-xs text-slate-300 border border-slate-700 transition-colors cursor-pointer font-mono"
            title="Configure GitHub Personal Access Token"
          >
            <Key className="w-3.5 h-3.5 text-cyan-400" />
            <span>{githubPat ? 'PAT Saved' : 'Add GitHub PAT'}</span>
          </button>
        </div>

        {/* GitHub PAT Configuration Drawer */}
        {isPatConfigOpen && (
          <div className="relative z-10 mb-6 p-4 rounded-xl bg-black/60 border border-cyan-500/30 animate-fadeIn space-y-2">
            <div className="flex items-center justify-between text-xs font-mono text-cyan-400 font-bold">
              <span>GitHub Personal Access Token (PAT):</span>
              <span className="text-[10px] text-slate-400">Repo: shahudgg/SaifAI-Builder</span>
            </div>
            <input
              type="password"
              value={githubPat}
              onChange={(e) => handleSavePat(e.target.value)}
              placeholder="ghp_YOUR_GITHUB_PERSONAL_ACCESS_TOKEN"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3.5 py-2 text-xs text-slate-200 font-mono focus:outline-none focus:border-cyan-500"
            />
            <p className="text-[11px] text-slate-400">
              🔒 Stored safely in your browser/server. Requires <code className="text-cyan-300 font-mono">repo</code> or <code className="text-cyan-300 font-mono">workflow</code> permissions to dispatch <code className="text-cyan-300 font-mono">build.yml</code>.
            </p>
          </div>
        )}

        {/* TASK 1: Input Box & Build App Button */}
        <form onSubmit={handleBuildApp} className="relative z-10 space-y-4">
          <div>
            <label className="block text-xs font-mono text-cyan-400 uppercase font-bold mb-1.5">
              Enter Your App Description or Feature Prompt:
            </label>
            <div className="relative">
              <textarea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                placeholder="e.g. Build an AI-powered fitness app with workout tracking, daily calorie calculator, and dark mode UI..."
                rows={3}
                disabled={buildStatus === 'building' || buildStatus === 'triggering'}
                className="w-full rounded-xl bg-black/60 border border-slate-800 focus:border-cyan-500/80 p-3.5 text-sm text-slate-100 placeholder-slate-500 transition-colors focus:outline-none resize-none"
              />
            </div>
          </div>

          <div className="flex flex-col sm:flex-row items-center gap-3">
            <button
              type="submit"
              disabled={buildStatus === 'building' || buildStatus === 'triggering'}
              className={`w-full sm:w-auto flex-1 py-3.5 px-6 rounded-xl font-black text-sm uppercase tracking-wide transition-all cursor-pointer flex items-center justify-center gap-2.5 shadow-xl ${
                buildStatus === 'building' || buildStatus === 'triggering'
                  ? 'bg-slate-800 text-slate-400 border border-slate-700 cursor-not-allowed'
                  : 'bg-gradient-to-r from-cyan-500 via-blue-500 to-indigo-600 hover:from-cyan-400 hover:to-indigo-500 text-white shadow-cyan-500/30 hover:scale-[1.01]'
              }`}
            >
              {buildStatus === 'triggering' ? (
                <>
                  <div className="w-4 h-4 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin"></div>
                  <span>Triggering GitHub Workflow...</span>
                </>
              ) : buildStatus === 'building' ? (
                <>
                  <Clock className="w-4 h-4 text-cyan-400 animate-spin [animation-duration:3s]" />
                  <span>Building... Please wait</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-4 h-4" />
                  <span>Build App (Trigger GitHub Actions)</span>
                </>
              )}
            </button>

            {/* Quick check button for Firebase sync */}
            <button
              type="button"
              onClick={async () => {
                const rec = await checkFirebaseDatabase(prompt);
                if (rec && rec.driveFileId) {
                  setLatestAppRecord(rec);
                  setBuildStatus('ready');
                  setStatusMessage('Fetched latest app record from Firebase Realtime DB!');
                } else {
                  setStatusMessage('No completed build found in Firebase yet.');
                }
              }}
              className="px-4 py-3.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-xs font-mono text-slate-300 border border-slate-800 transition-colors flex items-center gap-1.5 cursor-pointer shrink-0"
              title="Poll Firebase Realtime Database"
            >
              <RefreshCw className="w-3.5 h-3.5 text-cyan-400" />
              <span>Check Firebase</span>
            </button>
          </div>
        </form>

        {/* TASK 2: Realtime Status Banner */}
        {statusMessage && (
          <div
            className={`relative z-10 mt-5 p-3.5 rounded-xl border text-xs font-mono flex items-center gap-2.5 animate-fadeIn ${
              buildStatus === 'ready'
                ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
                : buildStatus === 'building' || buildStatus === 'triggering'
                ? 'bg-cyan-950/40 border-cyan-500/40 text-cyan-300'
                : 'bg-rose-950/40 border-rose-500/40 text-rose-300'
            }`}
          >
            {buildStatus === 'ready' && <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />}
            {(buildStatus === 'building' || buildStatus === 'triggering') && (
              <div className="w-3.5 h-3.5 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin shrink-0"></div>
            )}
            {buildStatus === 'error' && <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />}
            <span className="flex-1">{statusMessage}</span>
          </div>
        )}

        {/* TASK 3: Generated Direct Download Link Card */}
        {latestAppRecord && (
          <div className="relative z-10 mt-6 p-5 rounded-xl bg-gradient-to-br from-slate-950 via-[#0a0f1e] to-cyan-950/40 border border-cyan-500/50 shadow-xl animate-fadeIn">
            <div className="flex flex-wrap items-center justify-between gap-2 pb-3 mb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-ping"></span>
                <span className="text-xs font-mono font-bold text-emerald-400 uppercase">
                  {latestAppRecord.status || 'Ready to Download'}
                </span>
              </div>
              <span className="text-[11px] font-mono text-slate-400">
                Source: Firebase Realtime DB
              </span>
            </div>

            <div className="space-y-2 mb-4">
              <div className="text-sm font-bold text-white flex items-center gap-2">
                <HardDrive className="w-4 h-4 text-cyan-400 shrink-0" />
                <span className="break-words">{latestAppRecord.appName || prompt || 'Saif AI Generated App'}</span>
              </div>
              <div className="text-xs font-mono text-slate-400 truncate">
                Google Drive File ID: <span className="text-cyan-300 font-semibold">{latestAppRecord.driveFileId}</span>
              </div>
            </div>

            {/* Direct Download Button & Ad Rewarded Download Option */}
            <div className="flex flex-col sm:flex-row items-center gap-3">
              <a
                href={directDownloadUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="w-full sm:w-auto flex-1 py-3 px-5 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-400 hover:to-teal-500 text-white font-extrabold text-xs sm:text-sm tracking-wide uppercase transition-all shadow-lg shadow-emerald-500/25 flex items-center justify-center gap-2 cursor-pointer"
              >
                <Download className="w-4 h-4 animate-bounce" />
                <span>Download APK (Direct Google Drive)</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </a>

              {onDownloadWithAd && (
                <button
                  type="button"
                  onClick={() => onDownloadWithAd(directDownloadUrl, `${latestAppRecord.appName || 'SaifAI_App'}.apk`)}
                  className="w-full sm:w-auto py-3 px-4 rounded-xl bg-cyan-500/20 hover:bg-cyan-500/30 text-cyan-300 border border-cyan-500/40 font-bold text-xs transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>Download via Ad Gateway</span>
                </button>
              )}

              <button
                type="button"
                onClick={copyDownloadUrl}
                className="px-3.5 py-3 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-300 text-xs font-mono border border-slate-800 transition-colors flex items-center gap-1.5 cursor-pointer"
                title="Copy Direct Download Link"
              >
                {copiedLink ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedLink ? 'Copied' : 'Copy'}</span>
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
