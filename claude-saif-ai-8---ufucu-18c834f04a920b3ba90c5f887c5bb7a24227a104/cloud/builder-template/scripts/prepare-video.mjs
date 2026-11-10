import fs from 'node:fs/promises';

/** Prompt-only video storyboards require a configured AI provider; a local Studio timeline does not. */
export async function prepareVideo(input) {
  if (input.project) return input.project;
  if (!process.env.GEMINI_API_KEY)
    throw new Error('Prompt-only video jobs need GEMINI_API_KEY, or a timeline from SAIF AI Video Studio');
  const match = input.prompt.match(/duration\s+(\d+(?:\.\d+)?)\s+minutes?/i);
  const seconds = match ? Math.round(Number(match[1]) * 60) : 15;
  if (!Number.isFinite(seconds) || seconds < 1 || seconds > 180 * 60) throw new Error('Video duration must be 1 second to 180 minutes');
  const ratio = input.prompt.match(/\b(9:16|16:9|1:1)\b/)?.[1] || '9:16';
  const [width, height] = ratio === '16:9' ? [1280, 720] : ratio === '1:1' ? [1080, 1080] : [720, 1280];
  const res = await fetch('https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent', {
    method: 'POST', headers: { 'x-goog-api-key': process.env.GEMINI_API_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({ contents: [{ parts: [{ text: `Write 4 to 8 short on-screen text cards for a video on this topic. Return only JSON: {"cards":["short text",...]}. No unsafe content or markdown. Request: ${input.prompt.slice(0, 3500)}` }] }],
      generationConfig: { responseMimeType: 'application/json', maxOutputTokens: 1200 } })
  });
  if (!res.ok) throw new Error(`Video storyboard generation failed (HTTP ${res.status})`);
  const data = await res.json();
  const cards = JSON.parse(data.candidates?.[0]?.content?.parts?.[0]?.text || '{}').cards;
  if (!Array.isArray(cards) || cards.length < 2 || cards.length > 12 ||
      cards.some(c => typeof c !== 'string' || c.length > 200)) throw new Error('Invalid generated video storyboard');
  const total = seconds * 1_000_000;
  const items = cards.map((text, index) => {
    const startUs = Math.floor(total * index / cards.length);
    return { type: 'text', startUs, durationUs: Math.floor(total * (index + 1) / cards.length) - startUs,
      text, fontSize: 46, color: '#e1ecff' };
  });
  return { type: 'video', name: 'SAIF AI Video', width, height, fps: 30, durationUs: total, items };
}

export async function saveVideo(input, dest) {
  const project = await prepareVideo(input);
  await fs.writeFile(dest, JSON.stringify({ ...input, project }));
  return project;
}
