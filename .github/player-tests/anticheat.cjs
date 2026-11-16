// Real joins against a production Fiw AntiCheat server, through the vanilla wire protocol.
// stdout is a JSON bridge to the runner: {"console": ...} lines become server console input, and
// console responses are read back from the server log of this session.
//
// The headless client speaks the companion handshake itself: it receives the challenge nonce and
// answers with a made-up mod list, so every server path runs for real: pass, banned mod, wrong
// nonce, no reply (a client without the companion), admin exemptions, recheck, the freeze, and
// persistence across a restart.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { once } = require('node:events')
const mineflayer = require('mineflayer')

const [port, version, phase, directory] = process.argv.slice(2)
const emit = value => process.stdout.write(JSON.stringify(value) + '\n')
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
const deadline = setTimeout(() => { emit({ error: 'Join verification timed out' }); process.exit(1) }, 280000)
const log = path.join(directory, `${phase}.log`)
const configFile = path.join(directory, 'config', 'fiw-mods-api', 'config.json')

// Which loader, from the jar the runner installed: channel names differ per loader.
const jar = fs.readdirSync(path.join(directory, 'mods')).find(name => name.startsWith('fiw-anticheat-'))
const loader = jar.split('-')[2]
const channels = loader === 'fabric'
  ? { challenge: 'fiw-mods-api:challenge', response: 'fiw-mods-api:response' }
  : loader === 'neoforge'
    ? { challenge: 'fiw_mods_api:challenge', response: 'fiw_mods_api:response' }
    : { challenge: 'fiw_mods_api:main', response: 'fiw_mods_api:main' } // Forge: one channel, a discriminator byte first

const TIMEOUT_SECONDS = 15 // room for the freeze check before the timeout kick
const KICK = 'QA kick: mod not allowed'
const TIMEOUT = 'QA timeout: no companion reply'

// ── server log ───────────────────────────────────────────────────────────────
const text = () => fs.readFileSync(log, 'utf8')
const mark = () => text().length
async function until(condition, description, timeout = 15000) {
  const end = Date.now() + timeout
  for (;;) {
    const value = condition()
    if (value) return value
    if (Date.now() > end) throw new Error(`Timed out: ${description}`)
    await delay(200)
  }
}
async function logShows(start, needle, description, timeout) {
  return until(() => text().slice(start).includes(needle), description || needle, timeout)
}
async function command(line, response, timeout) {
  const start = mark()
  emit({ console: line })
  if (response) await logShows(start, response, `${line} -> ${response}`, timeout)
  else await delay(800)
  return text().slice(start)
}

// ── wire format (FriendlyByteBuf) ──────────────────────────────────────────────
function varInt(value) {
  const out = []
  do {
    let b = value & 0x7f
    value >>>= 7
    if (value !== 0) b |= 0x80
    out.push(b)
  } while (value !== 0)
  return Buffer.from(out)
}
function readVarInt(buf, offset = 0) {
  let value = 0; let shift = 0; let pos = offset
  for (;;) {
    const b = buf[pos++]
    value |= (b & 0x7f) << shift
    if ((b & 0x80) === 0) return { value, next: pos }
    shift += 7
  }
}
const utf = s => { const b = Buffer.from(s, 'utf8'); return Buffer.concat([varInt(b.length), b]) }
const bytes = b => Buffer.concat([varInt(b.length), b])
const list = items => Buffer.concat([varInt(items.length), ...items.map(utf)])
function response(mods, nonce) {
  const parts = [varInt(mods.length)]
  for (const id of mods) parts.push(utf(id), utf('1.0.0'), utf(''), list([]), list([]), list([]))
  parts.push(varInt(0)) // no resource packs
  parts.push(bytes(nonce))
  const body = Buffer.concat(parts)
  return loader === 'forge' ? Buffer.concat([Buffer.from([1]), body]) : body
}
function challengeNonce(data) {
  let offset = 0
  if (loader === 'forge') {
    if (data[0] !== 0) return null // not the challenge message
    offset = 1
  }
  const { value, next } = readVarInt(data, offset)
  return data.subarray(next, next + value)
}

// ── a joining player ─────────────────────────────────────────────────────────
// reply: 'clean' | 'banned' | 'wrong' | null (no companion: never answers, never registers).
function join(name, reply) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, auth: 'offline', version })
  const state = { name, bot, start: mark(), challenges: 0, kicked: null, ended: false, spawned: false }
  if (reply && loader === 'neoforge') {
    // NeoForge only sends payloads on channels the client declared during configuration.
    bot._client.on('state', s => {
      if (s === 'configuration') {
        bot._client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(`${channels.challenge}\0`) })
      }
    })
  }
  bot._client.on('custom_payload', packet => {
    if (packet.channel !== channels.challenge) return
    const nonce = challengeNonce(packet.data)
    if (!nonce) return
    state.challenges++
    if (!reply) return
    const echo = reply === 'wrong' ? Buffer.from(nonce.map(b => b ^ 0xff)) : nonce
    const mods = reply === 'banned' ? ['minecraft', 'qa_cheat'] : ['minecraft', 'qa_harmless']
    bot._client.write('custom_payload', { channel: channels.response, data: response(mods, echo) })
  })
  bot.on('kicked', reason => { state.kicked = typeof reason === 'string' ? reason : JSON.stringify(reason) })
  bot.on('end', () => { state.ended = true })
  bot.on('error', () => {})
  bot.once('spawn', () => { state.spawned = true })
  return state
}
// The server log is the authority: a client can lose the kick packet when the connection closes
// right after it (seen on Forge 1.20.1), so the server's own disconnect line also counts.
async function kickedWith(state, needle, description, timeout = 15000) {
  const serverSaid = () => text().slice(state.start).includes(`${state.name} lost connection: ${needle}`)
  await until(() => (state.kicked && state.kicked.includes(needle)) || serverSaid(),
    `${description}: kicked with "${needle}" (client saw: ${state.kicked})`, timeout)
}
function connected(state, description) {
  assert(state.bot.entity && !state.kicked && !state.ended, `${description}: still connected (kick: ${state.kicked})`)
}
async function stillOnline(state, ms, description) {
  await delay(ms)
  connected(state, description)
}
// Only the success output has this header; failures ("Unknown player", "No profile recorded") don't.
const profileShown = name => command(`fiwmods profile ${name}`, `[FiwAntiCheat] ${name} - `)
async function leave(state) {
  if (state.ended) return
  const ended = once(state.bot, 'end')
  state.bot.quit('QA done')
  await Promise.race([ended, delay(5000)])
}

// ── scenario ─────────────────────────────────────────────────────────────────
async function configure() {
  await until(() => fs.existsSync(configFile), 'config file created by the mod')
  const config = JSON.parse(fs.readFileSync(configFile, 'utf8'))
  config.timeout_seconds = TIMEOUT_SECONDS
  config.kick_message = KICK
  config.timeout_message = TIMEOUT
  config.detection.banned_mods = ['qa_cheat']
  fs.writeFileSync(configFile, JSON.stringify(config, null, 2))
  await command('fiwmods reload', 'Config + signatures reloaded.')
}

async function first() {
  await configure()
  await command('fiwmods exempt add QAForce force_block', 'Granted')
  await command('fiwmods exempt add QASilent silent', 'Granted')
  emit({ assertion: 'config reload and exemption grants work from the console' })

  // A client without the companion: no server error (the runner fails on any), frozen, timeout kick.
  const none = join('QANoReply', null)
  await until(() => none.spawned || none.kicked, 'client without companion reaches the world')
  connected(none, 'client without companion is let in first')
  await command('execute at QANoReply run summon minecraft:pig ~1 ~ ~ {NoAI:1b,Tags:["qa_pig"]}')
  const pig = await until(() => Object.values(none.bot.entities).find(e => e.name === 'pig'), 'pig visible to the frozen player')
  connected(none, 'frozen player before attacking')
  none.bot.attack(pig)
  await delay(500)
  none.bot.attack(pig)
  await delay(1000)
  connected(none, 'frozen player after attacking (the attacks were really sent)')
  const health = await command('data get entity @e[type=minecraft:pig,tag=qa_pig,limit=1] Health', 'has the following entity data')
  assert.match(health, /has the following entity data: 10(\.0)?f/, 'a frozen player cannot hurt anything')
  await command('kill @e[type=minecraft:pig,tag=qa_pig]')
  await kickedWith(none, TIMEOUT, 'client without companion', (TIMEOUT_SECONDS + 8) * 1000)
  // NeoForge must not send to a client that never declared the channel (that used to throw).
  if (loader === 'neoforge') assert.equal(none.challenges, 0, 'NeoForge sends no challenge to a client without the companion')
  emit({ assertion: 'a client without the companion joins, is frozen (cannot attack) and is kicked with timeout_message' })

  // The companion answers with a clean list: passes, then an admin recheck passes again.
  let start = mark()
  const clean = join('QAClean', 'clean')
  await logShows(start, 'QAClean passed verification', 'clean companion passes')
  await stillOnline(clean, 2000, 'clean companion stays')
  // Control for the freeze check above: once verified, the same attack does hurt.
  await command('execute at QAClean run summon minecraft:pig ~1 ~ ~ {NoAI:1b,Tags:["qa_pig"]}')
  const target = await until(() => Object.values(clean.bot.entities).find(e => e.name === 'pig'), 'pig visible to the verified player')
  clean.bot.attack(target)
  await delay(1000)
  const hurt = await command('data get entity @e[type=minecraft:pig,tag=qa_pig,limit=1] Health', 'has the following entity data')
  assert.doesNotMatch(hurt, /has the following entity data: 10(\.0)?f/, 'a verified player can hurt a mob (the freeze check is real)')
  await command('kill @e[type=minecraft:pig,tag=qa_pig]')
  start = mark()
  await command('fiwmods recheck player QAClean', 'Re-checking QAClean')
  await logShows(start, 'QAClean passed verification', 'recheck passes again')
  assert.equal(clean.challenges, 2, 'recheck sent a second challenge')
  await profileShown('QAClean')
  await leave(clean)
  emit({ assertion: 'companion with clean mods passes and can fight again, recheck challenges an online player, profile is recorded' })

  start = mark()
  const cheat = join('QACheat', 'banned')
  await kickedWith(cheat, KICK, 'banned mod')
  await logShows(start, 'QACheat flagged', 'banned mod is logged')
  start = mark()
  const wrong = join('QAMismatch', 'wrong')
  await kickedWith(wrong, KICK, 'wrong nonce')
  await logShows(start, 'Nonce mismatch from QAMismatch', 'the wrong nonce is the reason for the kick')
  emit({ assertion: 'a banned mod and a wrong nonce are kicked with kick_message' })

  const forced = join('QAForce', 'clean')
  await kickedWith(forced, KICK, 'force_block exemption')
  assert.equal(forced.challenges, 0, 'force_block kicks before any challenge is sent')
  start = mark()
  const silent = join('QASilent', 'banned')
  await logShows(start, 'QASilent flagged', 'silent exemption is still scanned')
  await stillOnline(silent, 2000, 'silent exemption is never kicked')
  await leave(silent)
  emit({ assertion: 'force_block kicks without a challenge; silent is scanned but never kicked' })
}

async function restart() {
  await delay(2000)
  const list = (await command('fiwmods exempt list', 'active exemption(s)')).toLowerCase()
  assert(list.includes('qaforce') && list.includes('qasilent'), 'exemption grants survive a restart')
  await profileShown('QAClean') // saved in the first run; QAClean is offline here
  const start = mark()
  const clean = join('QAClean', 'clean')
  await logShows(start, 'QAClean passed verification', 'clean companion passes after the restart')
  await leave(clean)
  const none = join('QANoReply', null)
  await kickedWith(none, TIMEOUT, 'restored timeout_message', (TIMEOUT_SECONDS + 10) * 1000)
  const leftovers = []
  const walk = dir => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const file = path.join(dir, entry.name)
      if (entry.isDirectory()) walk(file)
      else if (entry.name.endsWith('.tmp')) leftovers.push(file)
    }
  }
  walk(path.dirname(configFile))
  assert.deepEqual(leftovers, [], 'atomic saves leave no temporary files')
  emit({ assertion: 'grants, config and profiles survive a restart; no temporary files are left' })
}

async function main() {
  emit({ loader, version })
  if (phase === 'first') await first()
  else if (phase === 'restart') await restart()
  else throw new Error(`Unknown test phase: ${phase}`)
  clearTimeout(deadline)
  emit({ passed: phase, version, loader })
  setTimeout(() => process.exit(0), 500)
}

main().catch(error => { emit({ error: error.stack }); process.exit(1) })
