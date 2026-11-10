(() => {
  'use strict';
  const download = document.getElementById('download');
  const status = document.getElementById('status');
  const name = document.getElementById('file-name');
  const size = document.getElementById('file-size');
  const fragment = new URLSearchParams(location.hash.slice(1));
  const job = /^\/download\/([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})$/i.exec(location.pathname)?.[1];
  const claim = fragment.get('claim');
  // The job-specific page is on this website; the private claim stays in the URL fragment.
  // Remove fragments and unsolicited queries from browser history after reading.
  history.replaceState(null, '', location.pathname);
  const formData = { job, claim };
  const request = async (endpoint) => {
    const response = await fetch(endpoint, { method: 'POST', mode: 'same-origin', credentials: 'omit',
      cache: 'no-store', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(formData) });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'This private link is unavailable.');
    return data;
  };
  if (!job || !claim) {
    name.textContent = 'No private file selected';
    status.textContent = 'Open this page using the download button in SAIF AI.';
    return;
  }
  request('/api/inspectDownload').then(file => {
    name.textContent = String(file.name || 'Your private file').slice(0, 120);
    const format = file.kind === 'upload' ? String(file.name || '').split('.').pop().toUpperCase().slice(0, 8) :
      file.kind === 'video' ? 'MP4 VIDEO' : file.kind === 'zip' ? 'PROJECT ZIP' : 'ANDROID APK';
    size.textContent = `${format} · ${(Number(file.bytes) / 1048576).toFixed(1)} MB`;
    status.textContent = 'Verified. Your link will be used when you tap the button.';
    download.disabled = false;
  }).catch(error => {
    name.textContent = 'Link unavailable';
    status.textContent = error.message;
  });
  download.addEventListener('click', () => {
    download.disabled = true;
    status.textContent = 'Streaming your private file… Your link is now used.';
    // A regular POST form lets the browser stream large APKs/videos without holding
    // a Blob in memory. No claim token is placed in a public download URL.
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/api/claimDownload';
    for (const [key, value] of Object.entries(formData)) {
      const input = document.createElement('input');
      input.type = 'hidden'; input.name = key; input.value = value;
      form.appendChild(input);
    }
    document.body.appendChild(form);
    form.submit();
    form.remove();
  });
})();
