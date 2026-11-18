#!/usr/bin/env python3
"""Opt-in live check on a trusted CI runner; no auth header, response body or prompt logged."""
import json, os, secrets, urllib.request, urllib.error
from pathlib import Path

model = 'gemini-3.1-flash-lite'
key = os.getenv('JARVIS_TEST_GEMINI_API_KEY', '')
url = 'https://generativelanguage.googleapis.com/v1beta/models/' + model
report = {'model': model, 'valid_credential_test': 'not run; no repository QA credential supplied'}

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

opener = urllib.request.build_opener(NoRedirect)

def call(path, credential, body=None):
    req = urllib.request.Request(url + path, headers={'x-goog-api-key': credential, 'Content-Type': 'application/json'}, data=None if body is None else json.dumps(body).encode())
    try:
        with opener.open(req, timeout=15) as response:
            return response.status, json.loads(response.read(160000))
    except urllib.error.HTTPError as error:
        return error.code, {}

try:
    status, _ = call('', 'NEXUS_INVALID_QA_CREDENTIAL')
    report['invalid_credential_http_status'] = status
    if key:
        status, meta = call('', key)
        assert status == 200 and 'generateContent' in meta.get('supportedGenerationMethods', []), 'Model metadata not verified'
        nonce = secrets.token_hex(4)
        status, result = call(':generateContent', key, {'contents': [{'role': 'user', 'parts': [{'text': 'Call connection_probe with nonce ' + nonce}]}], 'tools':[{'functionDeclarations':[{'name':'connection_probe','parameters':{'type':'OBJECT','properties':{'nonce':{'type':'STRING'}},'required':['nonce']}}]}], 'toolConfig':{'functionCallingConfig':{'mode':'ANY','allowedFunctionNames':['connection_probe']}}})
        assert status == 200 and result['candidates'][0].get('finishReason') == 'STOP' and any(not p.get('functionCall', {}).get('willContinue') and 'partialArgs' not in p.get('functionCall', {}) and p.get('functionCall',{}).get('name')=='connection_probe' and p['functionCall'].get('args',{}).get('nonce')==nonce for p in result['candidates'][0]['content']['parts']), 'Function call not verified'
        report['valid_credential_test'] = 'passed metadata and nonce function probe'
except Exception as error:
    report['error_class'] = type(error).__name__
    if key: report['valid_credential_test'] = 'failed'
finally:
    Path('build/device-evidence').mkdir(parents=True,exist_ok=True)
    Path('build/device-evidence/provider.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
if report['valid_credential_test'] == 'failed': raise SystemExit(1)
