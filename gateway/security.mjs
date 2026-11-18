import { createPublicKey, verify, createHmac } from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';

// Only public keys from the operator-configured issuer are trusted. No jku/x5u lookup from tokens.
export async function authenticate(header, cfg, keys, now = Math.floor(Date.now() / 1000)) {
  if (!header?.startsWith('Bearer ') || header.length > 9000) throw Error('unauthorized');
  const pieces = header.slice(7).split('.');
  if (pieces.length !== 3 || pieces.some(x => !/^[\w-]+$/.test(x))) throw Error('unauthorized');
  const [h, c] = pieces.slice(0, 2).map(x => JSON.parse(Buffer.from(x, 'base64url')));
  if (h.alg !== 'RS256' || typeof h.kid !== 'string' || h.kid.length > 180 || h.crit) throw Error('unauthorized');
  const jwk = (await keys()).find(x => x.kid === h.kid && x.kty === 'RSA' && (!x.use || x.use === 'sig') && (!x.alg || x.alg === h.alg));
  if (!jwk || !verify('RSA-SHA256', Buffer.from(pieces[0] + '.' + pieces[1]), createPublicKey({key:jwk,format:'jwk'}), Buffer.from(pieces[2], 'base64url'))) throw Error('unauthorized');
  if (c.iss !== cfg.issuer || ![c.aud].flat().includes(cfg.audience) || typeof c.sub !== 'string' || !c.sub || c.sub.length > 255 || !Number.isInteger(c.exp) || !Number.isInteger(c.iat) || c.exp <= now || c.iat > now + 30 || c.exp - c.iat > 3600 || (c.nbf && c.nbf > now + 30) || !String(c.scope || '').split(' ').includes('nexus:plan')) throw Error('unauthorized');
  return c.sub;
}

export function makeBudget(path, salt) {
  const db = new DatabaseSync(path);
  db.exec('PRAGMA journal_mode=WAL; PRAGMA busy_timeout=2000; CREATE TABLE IF NOT EXISTS budget (bucket TEXT, subject TEXT, n INTEGER NOT NULL, expires INTEGER NOT NULL, PRIMARY KEY(bucket,subject))');
  const read = db.prepare('SELECT n FROM budget WHERE bucket=? AND subject=?');
  const add = db.prepare('INSERT INTO budget VALUES(?,?,1,?) ON CONFLICT(bucket,subject) DO UPDATE SET n=n+1');
  const purge = db.prepare('DELETE FROM budget WHERE expires<=?');
  return (subject, limits, now = Date.now()) => {
    const hash = createHmac('sha256', salt).update(subject).digest('hex');
    db.exec('BEGIN IMMEDIATE');
    try {
      purge.run(now);
      for (const [scope, ms, limit] of limits) {
        const bucket = scope + ':' + Math.floor(now / ms);
        const id = scope === 'global' ? '*' : hash;
        if ((read.get(bucket,id)?.n || 0) >= limit) { db.exec('ROLLBACK'); return false; }
        add.run(bucket,id,(Math.floor(now / ms)+1)*ms);
      }
      db.exec('COMMIT');return true;
    } catch (e) { db.exec('ROLLBACK');throw e; }
  };
}

const TOOLS = new Set('connection_probe device_report list_apps find_app launch_app previous_app open_panel open_interface open_url web_search maps find_contact dial_contact compose_sms compose_email share_text save_note set_reminder'.split(' '));
export function boundedBody(body) {
  if (!body || Object.keys(body).some(k=>!['contents','systemInstruction','tools','generationConfig','toolConfig'].includes(k)) || !Array.isArray(body.contents) || body.contents.length < 1 || body.contents.length > 18) throw Error('bad request');
  for(const turn of body.contents) {
    if (!['model','user'].includes(turn.role) || !Array.isArray(turn.parts) || turn.parts.length>100 || turn.parts.some(p=>!p || Object.keys(p).some(k=>!['text','thought','thoughtSignature','functionCall','functionResponse'].includes(k)))) throw Error('unsupported parts');
  }
  if(body.systemInstruction && (!Array.isArray(body.systemInstruction.parts) || body.systemInstruction.parts.some(p=>Object.keys(p).length!==1 || typeof p.text!=='string'))) throw Error('bad instruction');
  if(!Array.isArray(body.tools)||body.tools.length!==1||Object.keys(body.tools[0]).length!==1||!Array.isArray(body.tools[0].functionDeclarations)||body.tools[0].functionDeclarations.length>24||body.tools[0].functionDeclarations.some(f=>!TOOLS.has(f.name)))throw Error('unsupported tool');
  return {contents:body.contents,systemInstruction:body.systemInstruction,tools:body.tools,toolConfig:body.toolConfig, generationConfig:{maxOutputTokens:1600}};
}
