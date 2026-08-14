import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import vm from 'node:vm'
import ts from 'typescript'

const root = path.resolve(import.meta.dirname, '..')
const parserPath = path.join(root, 'src', 'services', 'ai-parser.ts')
const require = createRequire(import.meta.url)

function loadTsModule(filePath) {
  const source = fs.readFileSync(filePath, 'utf8')
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
  }).outputText

  const module = { exports: {} }
  vm.runInNewContext(output, {
    exports: module.exports,
    module,
    require,
  }, { filename: filePath })
  return module.exports
}

const { createSseMessageParser } = loadTsModule(parserPath)

const messages = []
const parser = createSseMessageParser((msg) => messages.push(msg))

parser.push('data: {"type":"summary","message":"标准换行"}\n\n')
parser.push('data: {"type":"summary","message":"CRLF换行"}\r\n\r\n')
parser.push('data: {"type":"summary","message":"拆')
parser.push('分消息"}\n\n')
parser.push('{"type":"summary","message":"纯JSON"}')

assert.deepEqual(JSON.parse(JSON.stringify(messages)), [
  { type: 'summary', message: '标准换行' },
  { type: 'summary', message: 'CRLF换行' },
  { type: 'summary', message: '拆分消息' },
  { type: 'summary', message: '纯JSON' },
])
