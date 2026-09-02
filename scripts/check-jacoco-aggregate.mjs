import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const EXPECTED_MODULES = [
  'xiyouji-bootstrap',
  'xiyouji-common',
  'xiyouji-domain',
  'xiyouji-application',
  'xiyouji-infrastructure',
]

const reportPath = resolve(
  process.argv[2]
    ?? process.env.JACOCO_AGGREGATE_XML
    ?? 'xiyouji-bootstrap/target/site/jacoco-aggregate/jacoco.xml',
)

function readThreshold(name, fallback) {
  const rawValue = process.env[name] ?? String(fallback)
  const value = Number(rawValue)

  if (!Number.isFinite(value) || value < 0 || value > 100) {
    throw new Error(`${name} must be a percentage between 0 and 100; received "${rawValue}"`)
  }

  return value
}

function readRootCounters(xml) {
  const lastGroupEnd = xml.lastIndexOf('</group>')
  const reportEnd = xml.lastIndexOf('</report>')

  if (lastGroupEnd < 0 || reportEnd < 0 || lastGroupEnd >= reportEnd) {
    throw new Error('report does not contain JaCoCo aggregate groups and report-level counters')
  }

  const reportSummary = xml.slice(lastGroupEnd + '</group>'.length, reportEnd)
  const counters = new Map()
  const counterPattern = /<counter\s+type="([A-Z]+)"\s+missed="(\d+)"\s+covered="(\d+)"\s*\/>/g

  for (const match of reportSummary.matchAll(counterPattern)) {
    counters.set(match[1], {
      missed: Number(match[2]),
      covered: Number(match[3]),
    })
  }

  return counters
}

function percentage(counter, type) {
  if (!counter) {
    throw new Error(`report-level ${type} counter is missing`)
  }

  const total = counter.covered + counter.missed
  if (total === 0) {
    throw new Error(`report-level ${type} counter has no executable items`)
  }

  return (counter.covered / total) * 100
}

try {
  const lineMinimum = readThreshold('JACOCO_LINE_MIN', 28)
  const branchMinimum = readThreshold('JACOCO_BRANCH_MIN', 16)
  const xml = readFileSync(reportPath, 'utf8')
  const moduleNames = [...xml.matchAll(/<group\s+name="([^"]+)"/g)].map((match) => match[1])
  const missingModules = EXPECTED_MODULES.filter((moduleName) => !moduleNames.includes(moduleName))

  if (missingModules.length > 0) {
    throw new Error(`aggregate report is missing module group(s): ${missingModules.join(', ')}`)
  }

  const counters = readRootCounters(xml)
  const checks = [
    { type: 'LINE', minimum: lineMinimum },
    { type: 'BRANCH', minimum: branchMinimum },
  ].map(({ type, minimum }) => {
    const counter = counters.get(type)
    return {
      type,
      minimum,
      counter,
      actual: percentage(counter, type),
    }
  })

  console.log(`JaCoCo aggregate report: ${reportPath}`)
  console.log(`Verified modules (${EXPECTED_MODULES.length}): ${EXPECTED_MODULES.join(', ')}`)

  for (const check of checks) {
    const total = check.counter.covered + check.counter.missed
    const status = check.actual >= check.minimum ? 'PASS' : 'FAIL'
    console.log(
      `${check.type}: ${check.actual.toFixed(2)}% `
      + `(${check.counter.covered}/${total}), minimum ${check.minimum.toFixed(2)}% [${status}]`,
    )
  }

  const failures = checks.filter((check) => check.actual < check.minimum)
  if (failures.length > 0) {
    const details = failures
      .map((check) => `${check.type} ${check.actual.toFixed(2)}% < ${check.minimum.toFixed(2)}%`)
      .join('; ')
    throw new Error(`aggregate coverage is below the configured threshold: ${details}`)
  }
} catch (error) {
  console.error(`JaCoCo aggregate coverage gate failed: ${error.message}`)
  process.exitCode = 1
}
