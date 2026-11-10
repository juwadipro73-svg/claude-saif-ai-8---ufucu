#!/usr/bin/env python3
"""Bounded FFmpeg timeline renderer for SAIF AI Cloud template.

Supports text/code title cards, image and video clips, and audio/voice tracks. It does not
execute arbitrary HTML/JavaScript from code scenes. Add a separately sandboxed browser
renderer if pixel-perfect Studio effects are needed; never claim that this template has them.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import sys

FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'


# Force a known demuxer for each declared MIME, instead of letting FFmpeg probe a
# malicious playlist that could reference network resources or worker credential files.
MEDIA_FORMAT = {
    'image/png': ('image2', 'png'), 'image/jpeg': ('image2', 'mjpeg'),
    'image/webp': ('image2', 'webp'), 'video/mp4': ('mov', None),
    'audio/mpeg': ('mp3', None), 'audio/mp3': ('mp3', None),
    'audio/wav': ('wav', None), 'audio/mp4': ('mov', None),
    'audio/ogg': ('ogg', None),
}


def check_media(media, mime):
    with media.open('rb') as source:
        data = source.read(16)
    allowed = {
        'image/png': data.startswith(b'\x89PNG\r\n\x1a\n'),
        'image/jpeg': data.startswith(b'\xff\xd8\xff'),
        'image/webp': data.startswith(b'RIFF') and data[8:12] == b'WEBP',
        'video/mp4': data[4:8] == b'ftyp',
        'audio/mpeg': data.startswith(b'ID3') or (len(data) > 1 and data[0] == 0xff and data[1] & 0xe0 == 0xe0),
        'audio/mp3': data.startswith(b'ID3') or (len(data) > 1 and data[0] == 0xff and data[1] & 0xe0 == 0xe0),
        'audio/wav': data.startswith(b'RIFF') and data[8:12] == b'WAVE',
        'audio/mp4': data[4:8] == b'ftyp',
        'audio/ogg': data.startswith(b'OggS'),
    }
    if not allowed.get(mime, False):
        raise ValueError('Private media does not match its declared type')


def format_for_media(asset_id, kind, media, asset_types):
    defaults = {'image': 'image/png', 'video': 'video/mp4', 'audio': 'audio/mpeg', 'voice': 'audio/mpeg'}
    mime = defaults[kind] if asset_types is None else asset_types.get(asset_id)
    if mime not in MEDIA_FORMAT or (kind == 'image' and not mime.startswith('image/')) or \
            (kind == 'video' and mime != 'video/mp4') or \
            (kind in ('audio', 'voice') and not mime.startswith('audio/')):
        raise ValueError('Timeline media MIME does not match its kind')
    if asset_types is not None:
        check_media(media, mime)
    return MEDIA_FORMAT[mime]


def build_command(project, assets_dir, out_path, scratch, asset_types=None):
    width, height, fps = project['width'], project['height'], project['fps']
    duration = project['durationUs'] / 1_000_000
    if not (360 <= width <= 2160 and 360 <= height <= 2160 and 24 <= fps <= 60 and 1 <= duration <= 10800):
        raise ValueError('Invalid bounded video configuration')
    bitrate = project.get('bitrate')
    if bitrate is not None and (type(bitrate) is not int or not 100_000 <= bitrate <= 50_000_000):
        raise ValueError('Invalid bounded video bitrate')
    args = ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
            '-f', 'lavfi', '-i', f'color=c=0x0b1226:s={width}x{height}:r={fps}:d={duration:.3f}']
    filters = ['[0:v]format=yuv420p[base0]']
    last = 'base0'
    audio_labels = []
    index = 0
    for item in project['items']:
        start = item['startUs'] / 1_000_000
        length = item['durationUs'] / 1_000_000
        end = start + length
        if start < 0 or length <= 0 or end > duration + .001:
            raise ValueError('Video segment outside timeline')
        kind = item['type']
        if kind in ('text', 'code') or (kind == 'voice' and not item.get('assetId')):
            text = item.get('text') or item.get('title') or ''
            # Scene HTML is deliberately not executed in this Actions runner. Its title appears instead.
            if not text:
                continue
            text_file = Path(scratch) / f'text_{index:03}.txt'
            text_file.write_text(text[:2000], encoding='utf-8')
            color = item.get('color', '#c8e3ff').lstrip('#')
            if not re.fullmatch('[0-9a-fA-F]{6}', color):
                raise ValueError('Invalid timeline text color')
            text_size = min(90, max(18, int(item.get('fontSize', 46))))
            label = f'text{index}'
            filters.append(f'[{last}]drawtext=fontfile={FONT}:textfile={text_file}:fontsize={text_size}:'
                           f'fontcolor=0x{color}:x=(w-text_w)/2:y=(h-text_h)/2:expansion=none:'
                           f"enable='between(t,{start:.3f},{end:.3f})'[{label}]")
            last = label
        elif kind in ('image', 'video'):
            asset = item.get('assetId')
            if not asset or not re.fullmatch(r'[0-9a-fA-F-]{36}', asset):
                raise ValueError('Undeclared timeline media')
            media = Path(assets_dir) / asset
            if not media.is_file():
                raise ValueError('Missing privately staged media')
            demuxer, decoder = format_for_media(asset, kind, media, asset_types)
            args += ['-protocol_whitelist', 'file,pipe', '-f', demuxer]
            if decoder:
                args += ['-c:v', decoder]
            if kind == 'image':
                args += ['-loop', '1', '-framerate', str(fps), '-i', str(media)]
                source_in = 0
            else:
                args += ['-stream_loop', '-1', '-i', str(media)]
                source_in = item.get('sourceInUs', 0) / 1_000_000
            input_index = len([v for v in args if v == '-i']) - 1
            label = f'media{index}'
            filters.append(f'[{input_index}:v]trim=start={source_in:.3f}:duration={length:.3f},'
                           f'setpts=PTS-STARTPTS+{start:.3f}/TB,'
                           f'scale={width}:{height}:force_original_aspect_ratio=decrease,'
                           f'pad={width}:{height}:(ow-iw)/2:(oh-ih)/2[{label}]')
            next_label = f'layer{index}'
            filters.append(f"[{last}][{label}]overlay=x=0:y=0:eof_action=pass:"
                           f"enable='between(t,{start:.3f},{end:.3f})'[{next_label}]")
            last = next_label
        elif kind in ('audio', 'voice') and item.get('assetId'):
            asset = item['assetId']
            if not re.fullmatch(r'[0-9a-fA-F-]{36}', asset):
                raise ValueError('Undeclared timeline audio')
            media = Path(assets_dir) / asset
            if not media.is_file():
                raise ValueError('Missing privately staged audio')
            demuxer, _ = format_for_media(asset, kind, media, asset_types)
            args += ['-protocol_whitelist', 'file,pipe', '-f', demuxer, '-stream_loop', '-1', '-i', str(media)]
            input_index = len([v for v in args if v == '-i']) - 1
            label = f'audio{index}'
            filters.append(f'[{input_index}:a]atrim=duration={length:.3f},'
                           f'asetpts=PTS-STARTPTS+{start:.3f}/TB[{label}]')
            audio_labels.append(label)
        index += 1
    filters.append(f'[{last}]format=yuv420p,trim=duration={duration:.3f}[outv]')
    if audio_labels:
        filters.append(''.join(f'[{label}]' for label in audio_labels) +
                       f'amix=inputs={len(audio_labels)}:duration=longest:dropout_transition=0,'
                       f'apad,atrim=duration={duration:.3f}[outa]')
    args += ['-filter_complex', ';'.join(filters), '-map', '[outv]']
    if audio_labels:
        args += ['-map', '[outa]', '-c:a', 'aac', '-b:a', '128k']
    else:
        args += ['-an']
    args += ['-c:v', 'libx264', '-preset', 'veryfast']
    if bitrate is None:
        args += ['-crf', '23']  # prompt-only storyboard default
    else:
        # Honor Studio's selected quality tier as a bounded video bitrate target.
        args += ['-b:v', str(bitrate), '-maxrate', str(bitrate), '-bufsize', str(bitrate * 2)]
    args += ['-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-t', f'{duration:.3f}', str(out_path)]
    return args


def main():
    if len(sys.argv) != 5:
        raise SystemExit('Usage: render_video.py input.json asset-dir output.mp4 scratch-dir')
    input_file, assets_dir, out_file, scratch = sys.argv[1:]
    payload = json.loads(Path(input_file).read_text(encoding='utf-8'))
    project = payload['project']
    if project.get('type') != 'video':
        raise ValueError('Expected a video timeline')
    os.makedirs(scratch, exist_ok=True)
    asset_types = {a['assetId']: a['mimeType'] for a in payload.get('assets', [])}
    command = build_command(project, assets_dir, out_file, scratch, asset_types)
    subprocess.run(command, check=True, timeout=3 * 60 * 60)
    if Path(out_file).stat().st_size < 1024:
        raise ValueError('Video render produced an empty file')


if __name__ == '__main__':
    main()
