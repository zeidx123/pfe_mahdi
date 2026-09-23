#!/usr/bin/env python3
"""Direct selected-provider check with real images; never changes tours or exposes API keys."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PROVIDERS = {'GROQ': 'https://api.groq.com/openai/v1', 'OPENROUTER': 'https://openrouter.ai/api/v1'}
GROQ_MODELS = {'qwen/qwen3.8-27b'}
LABELS = {'CLEANED', 'NOT_CLEANED', 'OBSTRUCTED', 'CANNOT_DETERMINE'}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def settings(path):
    values = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip().strip('\"\'')
    values.update(os.environ)
    return values


def operational_prompt():
    source = (ROOT / 'BACK/src/main/java/com/municipalite/gestiontournees/ai/vision/service/ImageAnalysisService.java').read_text()
    block = source.split('private String buildVisionPrompt(boolean paired)', 1)[1].split('"""', 2)[1]
    # Match Java text-block indentation and the AFTER-only substitution.
    lines = block.splitlines()
    nonempty = [len(s) - len(s.lstrip()) for s in lines if s.strip()]
    indent = min(nonempty)
    return '\n'.join(line[indent:] for line in lines).strip().replace('%s', 'Une seule image est fournie : APRÈS la collecte déclarée.') + '\n'


def safe_error(message, key):
    text = str(message).replace(key, '[REDACTED]') if key else str(message)
    text = re.sub(r'AIza[A-Za-z0-9_-]+', '[REDACTED]', text)
    text = re.sub(r'sk-or-[A-Za-z0-9_-]+', '[REDACTED]', text)
    text = re.sub(r'gsk_[A-Za-z0-9_-]+', '[REDACTED]', text)
    text = re.sub(r'https?://\S+', '[provider help link]', text)
    return text[:1400]


def valid_free_model(model):
    return bool(re.fullmatch(r'[A-Za-z0-9._-]+/[A-Za-z0-9._-]+:free', model)) and not model.startswith('openrouter/')


def valid_model(provider, model):
    return model in GROQ_MODELS if provider == 'GROQ' else provider == 'OPENROUTER' and valid_free_model(model)


def select_configuration(config, model_override=None, timeout_override=None, provider_override=None):
    provider = (provider_override or config.get('COLLECTION_AI_PROVIDER') or 'GROQ').strip().upper()
    if provider not in PROVIDERS:
        raise ValueError('COLLECTION_AI_PROVIDER must explicitly select GROQ or OPENROUTER.')
    key = config.get(f'{provider}_API_KEY', '')
    default = 'qwen/qwen3.8-27b' if provider == 'GROQ' else 'qwen/qwen3.8-27b:free'
    model = model_override or config.get(f'{provider}_MODEL_VISION') or default
    try:
        timeout = timeout_override if timeout_override is not None else float(config.get(f'{provider}_TIMEOUT_SECONDS') or 60)
    except (ValueError, TypeError):
        raise ValueError(f'{provider}_TIMEOUT_SECONDS must be a number from 1 to 120.') from None
    if not key or any(marker in key.upper() for marker in ('VOTRE_CLE', 'REPLACE', 'YOUR_KEY')):
        raise ValueError(f'Configure {provider}_API_KEY before running this diagnostic.')
    if not valid_model(provider, model):
        raise ValueError('Use the reviewed Groq Qwen model or an explicit OpenRouter :free model for the selected provider.')
    if not math.isfinite(timeout) or not 1 <= timeout <= 120:
        raise ValueError(f'{provider}_TIMEOUT_SECONDS must be a number from 1 to 120.')
    return provider, model, key, timeout


def apply_provider_response(result, root, key):
    if not isinstance(root, dict):
        result['status'] = 'INVALID_RESPONSE'
        return
    if root.get('error') is not None:
        result.update(status='PROVIDER_UNAVAILABLE', failureCategory='PROVIDER_RESPONSE_ERROR')
        return
    choices = root.get('choices')
    choice = choices[0] if isinstance(choices, list) and choices else None
    if not isinstance(choice, dict):
        result['status'] = 'INVALID_RESPONSE'
        return
    result['finishReason'] = choice.get('finish_reason')
    result['modelVersion'] = root.get('model')
    usage = root.get('usage')
    if isinstance(usage, dict):
        result['usageMetadata'] = {target: usage[source] for source, target in (
            ('prompt_tokens', 'promptTokenCount'), ('completion_tokens', 'candidatesTokenCount'),
            ('total_tokens', 'totalTokenCount')) if isinstance(usage.get(source), (int, float))}
    if choice.get('error') is not None:
        result.update(status='PROVIDER_UNAVAILABLE', failureCategory='PROVIDER_RESPONSE_ERROR')
        return
    if choice.get('finish_reason') not in (None, '', 'stop'):
        result.update(status='INVALID_RESPONSE', failureCategory='INCOMPLETE_PROVIDER_RESPONSE')
        return
    message = choice.get('message')
    if not isinstance(message, dict):
        result['status'] = 'INVALID_RESPONSE'
        return
    if message.get('refusal'):
        result.update(status='PROVIDER_UNAVAILABLE', failureCategory='PROVIDER_REFUSAL')
        return
    text = message.get('content')
    if isinstance(text, list):
        text = ''.join(part['text'] for part in text if isinstance(part, dict)
                       and part.get('type') == 'text' and isinstance(part.get('text'), str))
    if not isinstance(text, str) or not text.strip():
        result['status'] = 'EMPTY_RESPONSE'
        return
    result['providerText'] = safe_error(text, key)
    try:
        parsed = json.loads(text)
        valid = (isinstance(parsed, dict) and parsed.get('status') in LABELS
                 and isinstance(parsed.get('description'), str) and 0 < len(parsed['description'].strip()) <= 2000)
        result['status'] = 'PASS' if valid else 'INVALID_RESPONSE'
        if valid:
            result['prediction'] = parsed['status']
            result['description'] = safe_error(parsed['description'].strip(), key)
    except (ValueError, TypeError):
        result['status'] = 'INVALID_RESPONSE'


def probe(image, model, key, prompt, timeout, provider='GROQ'):
    if provider not in PROVIDERS or not valid_model(provider, model):
        raise ValueError('A reviewed model for the explicitly selected provider is required.')
    if not key or any(marker in key.upper() for marker in ('VOTRE_CLE', 'REPLACE', 'YOUR_KEY')):
        raise ValueError(f'Configure {provider}_API_KEY before running this diagnostic.')
    if not math.isfinite(timeout) or not 1 <= timeout <= 120:
        raise ValueError('Timeout must be a number from 1 to 120 seconds.')
    data = image.read_bytes()
    mime = 'image/jpeg' if data.startswith(b'\xff\xd8\xff') else 'image/png' if data.startswith(b'\x89PNG\r\n\x1a\n') else None
    if mime is None or not 0 < len(data) <= 10 * 1024 * 1024:
        raise ValueError('Supply a JPEG/PNG image no larger than 10 MiB.')
    result = {'image': image.name, 'imageSha256': hashlib.sha256(data).hexdigest(),
              'imageBytes': len(data), 'inputMode': 'AFTER_ONLY', 'provider': provider, 'model': model,
              'startedAtUtc': datetime.now(timezone.utc).isoformat()}
    schema = {'type': 'object', 'properties': {'status': {'type': 'string', 'enum': sorted(LABELS)},
        'description': {'type': 'string'}}, 'required': ['status', 'description'], 'additionalProperties': False}
    body = {'model': model, 'messages': [{'role': 'user', 'content': [
        {'type': 'text', 'text': prompt}, {'type': 'image_url', 'image_url': {
            'url': f'data:{mime};base64,{base64.b64encode(data).decode()}'}}]}],
        'stream': False, 'temperature': 0,
        'response_format': {'type': 'json_schema', 'json_schema': {'name': 'point_cleanliness', 'strict': True, 'schema': schema}}}
    if provider == 'GROQ':
        body.update(reasoning_effort='none', max_completion_tokens=1024)
    else:
        body.update(reasoning={'enabled': False}, max_tokens=1024,
                    provider={'allow_fallbacks': False, 'require_parameters': True,
                              'max_price': {'prompt': 0, 'completion': 0, 'request': 0, 'image': 0}})
    request = urllib.request.Request(f'{PROVIDERS[provider]}/chat/completions',
        data=json.dumps(body).encode(), headers={'Authorization': f'Bearer {key}', 'Content-Type': 'application/json',
                                               'Accept': 'application/json'}, method='POST')
    started = time.monotonic()
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=timeout) as response:
            result['httpStatus'] = response.status
            root = json.load(response)
        apply_provider_response(result, root, key)
    except urllib.error.HTTPError as error:
        result.update(status='PROVIDER_UNAVAILABLE', httpStatus=error.code)
        try:
            details = json.loads(error.read()).get('error', {})
            result['providerStatus'] = details.get('code', details.get('status'))
            result['providerMessage'] = safe_error(details.get('message', ''), key)
        except (ValueError, TypeError, AttributeError):
            result['providerMessage'] = 'Provider returned an HTTP error without a readable JSON error.'
    except (ValueError, TypeError) as error:
        result.update(status='INVALID_RESPONSE', failureCategory=type(error).__name__)
    except OSError as error:
        result['status'] = 'REQUEST_FAILED'
        reason = getattr(error, 'reason', error)
        result['failureCategory'] = type(reason).__name__
        result['providerMessage'] = safe_error(str(reason), key)
    result['latencyMs'] = round((time.monotonic() - started) * 1000)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path, default=ROOT / 'BACK/.env')
    parser.add_argument('--provider', choices=tuple(PROVIDERS), help='Override COLLECTION_AI_PROVIDER for this diagnostic only.')
    parser.add_argument('--model', help='Explicit reviewed model override for the selected provider; does not change application settings.')
    parser.add_argument('--image', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--timeout', type=float, help='Override the selected provider timeout (1–120 seconds).')
    args = parser.parse_args()
    config = settings(args.env_file)
    try:
        provider, model, key, timeout = select_configuration(config, args.model, args.timeout, args.provider)
    except ValueError as error:
        parser.error(str(error))
    prompt = operational_prompt()
    report = {'checkedAtUtc': datetime.now(timezone.utc).isoformat(), 'provider': provider,
        'providerHost': 'api.groq.com' if provider == 'GROQ' else 'openrouter.ai', 'promptVersion': 'point-cleanliness-v1',
        'promptSha256': hashlib.sha256(prompt.encode()).hexdigest(), 'prompt': prompt,
        'timeoutSeconds': timeout, 'reasoningEnabled': False, 'maxOutputTokens': 1024,
        'method': 'DIRECT_PROVIDER_HTTP', 'results': [],
        'scope': 'Small development check of real provider responses; not an independent accuracy benchmark. Tour history is unchanged.'}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    for image in args.image:
        result = probe(image.resolve(), model, key, prompt, timeout, provider)
        report['results'].append(result)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False), flush=True)
        if result.get('httpStatus') in (401, 402, 403, 429) or result.get('failureCategory') == 'PROVIDER_RESPONSE_ERROR':
            print('Stopped additional requests after credential/permission/quota rejection.', flush=True)
            break
    return 0 if len(report['results']) == len(args.image) and all(r['status'] == 'PASS' for r in report['results']) else 2


if __name__ == '__main__':
    raise SystemExit(main())
