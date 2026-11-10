import React, { useState } from 'react';
import { X, Copy, Check, Terminal, Code, Zap, Shield, Sparkles } from 'lucide-react';

interface ApiPushModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ApiPushModal: React.FC<ApiPushModalProps> = ({ isOpen, onClose }) => {
  const [copied, setCopied] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'android' | 'curl' | 'node' | 'python' | 'flutter'>('android');

  if (!isOpen) return null;

  const copySnippet = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(id);
    setTimeout(() => setCopied(null), 2000);
  };

  const snippets = {
    android: `// ========================================================
// Android Kotlin Background Worker & Deliverable Manager
// Dependencies: implementation("com.squareup.okhttp3:okhttp:4.12.0")
// ========================================================
package com.saifai.app.network

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SaifAiVaultManager {
    private const val BASE_URL = "${window.location.origin}"
    private val client = OkHttpClient()

    /**
     * Checks if deliverable should be uploaded to SAIF AI.
     * Skips trivial tasks (e.g. small AI images, text responses)
     * and only triggers for heavy deliverables (APK, MP4, Project ZIP).
     */
    fun shouldGenerateVaultLink(file: File): Boolean {
        val extension = file.extension.lowercase()
        val heavyExtensions = listOf("apk", "aab", "zip", "mp4", "tar", "gz")
        
        // Skip lightweight image thumbnails or plain text
        if (extension in listOf("jpg", "jpeg", "png", "webp") && file.length() < 2 * 1024 * 1024) {
            return false // Display directly in native UI!
        }
        
        return extension in heavyExtensions || file.length() >= 5 * 1024 * 1024
    }

    /**
     * Pushes file to SAIF AI and returns unique user download link
     */
    suspend fun pushDeliverableFile(
        file: File,
        mimeType: String = "application/octet-stream"
    ): String? = withContext(Dispatchers.IO) {
        if (!shouldGenerateVaultLink(file)) {
            return@withContext null // Keep in-app!
        }

        try {
            val fileBytes = file.readBytes()
            val base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

            val payload = JSONObject().apply {
                val fileObj = JSONObject().apply {
                    put("name", file.name)
                    put("data", "data:$mimeType;base64,$base64Data")
                    put("mimeType", mimeType)
                    put("size", file.length())
                }
                put("files", JSONArray().put(fileObj))
                put("securityMode", "onetime")
                put("burnOnDownload", true)
                put("expiryHours", 48)
                put("senderApp", "SAIF AI Android Native Engine")
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/push")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val accessUrl = json.getString("accessUrl")
                return@withContext "$BASE_URL$accessUrl" // Return unique link to user!
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}`,
    curl: `curl -X POST "${window.location.origin}/api/push" \\
  -H "Content-Type: application/json" \\
  -d '{
    "files": [
      {
        "name": "User_Invoice_102.pdf",
        "data": "JVBERi0xLjUKJcfsj6IKMSAwIG...",
        "mimeType": "application/pdf"
      }
    ],
    "securityMode": "onetime",
    "burnOnDownload": true,
    "expiryHours": 24,
    "senderApp": "My Native App"
  }'

# Response Returns:
# {
#   "success": true,
#   "vaultId": "vault_93021",
#   "accessUrl": "/?v=vault_93021&token=fedebe03",
#   "dynamicToken": "fedebe03"
# }
# Send "${window.location.origin}/?v=vault_93021&token=fedebe03" to the user!`,

    node: `// In your Node.js / Express backend or cloud function
const axios = require('axios');

async function pushFileAndGetLink(fileName, base64Data) {
  const response = await axios.post('${window.location.origin}/api/push', {
    files: [{
      name: fileName,
      data: base64Data,
      mimeType: 'application/pdf'
    }],
    securityMode: 'onetime', // Burns after recipient downloads
    burnOnDownload: true,
    expiryHours: 24,
    senderApp: 'My Backend Engine'
  });

  const { accessUrl } = response.data;
  const fullUserLink = \`${window.location.origin}\${accessUrl}\`;
  
  // Give this unique link to your user!
  return fullUserLink;
}`,

    python: `# In your Python backend (FastAPI / Flask / Django)
import requests

def push_file_to_saif(filename, base64_content):
    url = "${window.location.origin}/api/push"
    payload = {
        "files": [{
            "name": filename,
            "data": base64_content,
            "mimeType": "application/octet-stream"
        }],
        "securityMode": "onetime",
        "burnOnDownload": True,
        "expiryHours": 24,
        "senderApp": "Python Worker"
    }
    
    res = requests.post(url, json=payload).json()
    user_download_link = f"${window.location.origin}{res['accessUrl']}"
    
    # Send user_download_link to user!
    return user_download_link`,

    flutter: `// In your Flutter / Dart App
import 'dart:convert';
import 'package:http/http.dart' as http;

Future<String> pushUserFile(String fileName, List<int> fileBytes) async {
  final base64String = base64Encode(fileBytes);
  
  final res = await http.post(
    Uri.parse('${window.location.origin}/api/push'),
    headers: {'Content-Type': 'application/json'},
    body: jsonEncode({
      'files': [{
        'name': fileName,
        'data': 'data:application/pdf;base64,$base64String',
        'mimeType': 'application/pdf'
      }],
      'securityMode': 'onetime',
      'burnOnDownload': true,
      'senderApp': 'Flutter Client'
    }),
  );

  final data = jsonDecode(res.body);
  return '${window.location.origin}\${data['accessUrl']}';
}`
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn">
      <div className="relative w-full max-w-2xl rounded-2xl bg-[#090d1a] border border-cyan-500/40 shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
        {/* Header */}
        <div className="p-4 bg-slate-900 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Terminal className="w-4 h-4 text-cyan-400" />
            <h3 className="text-sm font-bold text-white font-mono uppercase">
              App Direct Push API & Unique Link Integration
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded text-slate-400 hover:text-white transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto space-y-4">
          <div className="p-3 rounded-xl bg-cyan-500/10 border border-cyan-500/30 text-xs text-slate-300 space-y-1">
            <div className="font-bold text-cyan-400 font-mono flex items-center gap-1.5">
              <Zap className="w-3.5 h-3.5" />
              HOW YOUR APP PUSHES FILES & GIVES LINKS:
            </div>
            <p>
              1. Your app creates or receives any file.
            </p>
            <p>
              2. Your app calls <code className="text-cyan-300 font-mono">POST /api/push</code> with the file payload.
            </p>
            <p>
              3. SAIF AI returns a unique link (e.g. <code className="text-cyan-300 font-mono">/?v=vault_123&token=abc</code>).
            </p>
            <p>
              4. Your app gives this unique link to your user. When they open it, they land directly on their file's download page.
            </p>
          </div>

          {/* Code Tabs */}
          <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
            {(['android', 'curl', 'node', 'python', 'flutter'] as const).map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-3 py-1 rounded text-xs font-mono uppercase font-semibold transition-all cursor-pointer ${
                  activeTab === tab
                    ? 'bg-cyan-500 text-slate-950 font-bold'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                {tab}
              </button>
            ))}
          </div>

          <div className="relative rounded-xl bg-black/80 border border-slate-800 p-4">
            <button
              onClick={() => copySnippet(activeTab, snippets[activeTab])}
              className="absolute top-3 right-3 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 font-mono flex items-center gap-1 transition-all cursor-pointer"
            >
              {copied === activeTab ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
              <span>{copied === activeTab ? 'Copied' : 'Copy'}</span>
            </button>
            <pre className="text-xs font-mono text-cyan-200/90 leading-relaxed overflow-x-auto">
              {snippets[activeTab]}
            </pre>
          </div>
        </div>

        {/* Footer */}
        <div className="p-3 bg-slate-900 border-t border-slate-800 text-right">
          <button
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 font-medium cursor-pointer"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
