import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const base = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../apk-template');
const safeSource = /^app\/src\/main\/(?:AndroidManifest\.xml|(?:java|kotlin|res|assets)\/(?:[A-Za-z0-9_.-]+\/)*[A-Za-z0-9_.-]+\.(?:java|kt|xml|html|css|js|json|txt|svg))$/;
const safeBinary = /^app\/src\/main\/(?:res\/(?:drawable|mipmap|raw|font)(?:-[a-z0-9-]+)?|assets)\/(?:[A-Za-z0-9_.-]+\/)*[A-Za-z0-9_.-]+\.(?:png|jpg|jpeg|webp|ttf|otf|mp3|wav)$/;
const safePackage = /^[a-zA-Z_][\w]*(?:\.[a-zA-Z_][\w]*){1,9}$/;
const escXml = str => str.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');

export function safeDestination(root, name, binary = false) {
  if (typeof name !== 'string' || name.split('/').includes('..') ||
      !(binary ? safeBinary : safeSource).test(name)) throw new Error('Unsafe project path');
  const dest = path.resolve(root, name);
  if (!dest.startsWith(path.resolve(root) + path.sep)) throw new Error('Project path escapes its sandbox');
  return dest;
}

async function geminiHtml(prompt) {
  if (!process.env.GEMINI_API_KEY) throw new Error('Prompt-only APK builds need GEMINI_API_KEY in the builder, or project source from SAIF AI Studio');
  const res = await fetch('https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent', {
    method: 'POST', headers: { 'x-goog-api-key': process.env.GEMINI_API_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({ contents: [{ parts: [{ text: `Create a single self-contained interactive offline HTML game/app for Android WebView. No external resources. Provide raw HTML only, no markdown. User request: ${prompt.slice(0, 3500)}` }] }],
      generationConfig: { maxOutputTokens: 16000, temperature: 0.6 } })
  });
  if (!res.ok) throw new Error(`App generation failed (HTTP ${res.status})`);
  const data = await res.json();
  const text = data.candidates?.[0]?.content?.parts?.map(p => p.text || '').join('') || '';
  const html = text.replace(/^```(?:html)?\s*/i, '').replace(/\s*```$/, '').trim();
  if (!/^<!doctype html|^<html/i.test(html) || !/<\/html>/i.test(html) || Buffer.byteLength(html) > 512_000)
    throw new Error('The app generator did not return a complete, bounded HTML app. Retry in SAIF AI.');
  return html;
}

export async function prepareApk({ input, assetsRoot, outputDir }) {
  const project = input.project || { type: 'android', appName: 'SAIF AI App',
    packageName: 'com.saifai.generated', files: [] };
  if (project.type !== 'android' || !safePackage.test(project.packageName) ||
      !Array.isArray(project.files) || project.files.length > 80) throw new Error('Invalid Android project');
  await fs.cp(base, outputDir, { recursive: true });
  await fs.writeFile(path.join(outputDir, 'gradle.properties'),
    `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\nsaif.generated.package=${project.packageName}\n`);
  for (const file of project.files) {
    const dest = safeDestination(outputDir, file.path);
    if (typeof file.content !== 'string' || Buffer.byteLength(file.content) > 512_000) throw new Error('Invalid source file');
    await fs.mkdir(path.dirname(dest), { recursive: true });
    await fs.writeFile(dest, file.content);
  }
  for (const asset of input.assets || []) {
    if (!asset.path || !/^[0-9a-f-]{36}$/i.test(asset.assetId)) throw new Error('Invalid Android resource asset');
    const dest = safeDestination(outputDir, asset.path, true);
    await fs.mkdir(path.dirname(dest), { recursive: true });
    await fs.copyFile(path.join(assetsRoot, asset.assetId), dest);
  }
  const mainDir = path.join(outputDir, 'app/src/main');
  // Project Studio's starter manifest references @drawable/ic_launcher but its
  // scaffold does not create an icon file. Supply a fixed vector only if no
  // custom resource with that name was included; also support @mipmap icons.
  const paths = [...project.files.map(f => f.path), ...(input.assets || []).map(a => a.path)];
  const fallbackIcon = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="72dp" android:height="72dp"
    android:viewportWidth="72" android:viewportHeight="72">
    <path android:fillColor="#0B1226" android:pathData="M0,0h72v72h-72z" />
    <path android:fillColor="#FF6B1A" android:pathData="M36,8L64,64H8Z" />
</vector>`;
  for (const kind of ['drawable', 'mipmap']) {
    const hasIcon = paths.some(p => typeof p === 'string' &&
      new RegExp(`^app/src/main/res/${kind}(?:-[a-z0-9-]+)?/ic_launcher\\.(?:xml|png|jpg|jpeg|webp)$`).test(p));
    if (!hasIcon) {
      const dir = path.join(mainDir, 'res', kind);
      await fs.mkdir(dir, { recursive: true });
      await fs.writeFile(path.join(dir, 'ic_launcher.xml'), fallbackIcon);
    }
  }
  const assetHtml = path.join(mainDir, 'assets/index.html');
  const nativeMain = project.files.find(f => /\bclass\s+MainActivity\b/.test(f.content) && /\.(?:java|kt)$/.test(f.path));
  if (!nativeMain) {
    if (!project.files.some(f => f.path === 'app/src/main/assets/index.html')) {
      await fs.mkdir(path.dirname(assetHtml), { recursive: true });
      await fs.writeFile(assetHtml, await geminiHtml(input.prompt));
    }
    const javaDir = path.join(mainDir, 'java', ...project.packageName.split('.'));
    await fs.mkdir(javaDir, { recursive: true });
    await fs.writeFile(path.join(javaDir, 'MainActivity.java'), `package ${project.packageName};\n
import android.app.Activity;\nimport android.os.Bundle;\nimport android.webkit.WebView;\n
public class MainActivity extends Activity {\n  @Override public void onCreate(Bundle state) {\n    super.onCreate(state);\n    WebView view = new WebView(this);\n    view.getSettings().setJavaScriptEnabled(true);\n    view.getSettings().setAllowFileAccessFromFileURLs(false);\n    view.getSettings().setAllowUniversalAccessFromFileURLs(false);\n    view.getSettings().setBlockNetworkLoads(true);\n    setContentView(view);\n    view.loadUrl("file:///android_asset/index.html");\n  }\n}\n`);
  }
  const activity = nativeMain?.content.match(/\bpackage\s+([a-zA-Z_][\w.]*)\s*[;\n]/)?.[1] || project.packageName;
  const manifest = path.join(mainDir, 'AndroidManifest.xml');
  if (!project.files.some(f => f.path === 'app/src/main/AndroidManifest.xml')) {
    await fs.writeFile(manifest, `<?xml version="1.0" encoding="utf-8"?>\n<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="${escXml(project.appName)}" android:theme="@android:style/Theme.Material.Light.NoActionBar" android:usesCleartextTraffic="false"><activity android:name="${activity}.MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>`);
  }
  return { appId: project.packageName, sourceFiles: project.files.length };
}
