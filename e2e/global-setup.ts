import { connect } from 'node:net'

// E2E를 시작할 때마다 E2E용 Redis 데이터베이스(1번)를 비운다.
// 앞선 실행이 남긴 로그인 차단 기록과 세션 때문에 결과가 달라지지 않게 하려는 것이다.
// 개발용 데이터(0번)는 건드리지 않는다.
const REDIS_HOST = process.env.REDIS_HOST ?? 'localhost'
const REDIS_PORT = Number(process.env.REDIS_PORT ?? 6379)
const REDIS_E2E_DATABASE = process.env.REDIS_E2E_DATABASE ?? '1'

export default async function globalSetup() {
  await sendRedisCommands([
    ['SELECT', REDIS_E2E_DATABASE],
    ['FLUSHDB'],
  ])
}

/** Redis에 명령을 차례로 보내고, 모두 +OK로 답하는지 확인한다. */
function sendRedisCommands(commands: string[][]): Promise<void> {
  const request = commands
    .map((args) => `*${args.length}\r\n` + args.map((arg) => `$${Buffer.byteLength(arg)}\r\n${arg}\r\n`).join(''))
    .join('')
  return new Promise((resolve, reject) => {
    const socket = connect(REDIS_PORT, REDIS_HOST, () => socket.write(request))
    let reply = ''
    socket.setTimeout(5_000, () => socket.destroy(new Error('Redis 응답이 없습니다. docker compose up -d --wait로 Redis를 켜 두세요')))
    socket.on('data', (chunk) => {
      reply += chunk.toString()
      const lines = reply.split('\r\n').filter(Boolean)
      if (lines.length < commands.length) return
      socket.end()
      if (lines.every((line) => line === '+OK')) resolve()
      else reject(new Error(`E2E용 Redis를 비우지 못했습니다: ${reply.trim()}`))
    })
    socket.on('error', reject)
  })
}
