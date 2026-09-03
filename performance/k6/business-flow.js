import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

/**
 * Two-instance multiplayer business-flow test.
 *
 * The default `smoke` profile runs one isolated room and is safe for a local
 * verification. `load` uses a constant arrival rate and must be enabled
 * explicitly. Thresholds are acceptance criteria for this run; they are not
 * pre-recorded performance results or production SLOs.
 *
 * Local example (the report directory must already exist):
 *   k6 run -e APP_1_URL=http://localhost:18081 \
 *     -e APP_2_URL=http://localhost:18082 \
 *     -e SUMMARY_JSON=tmp/k6/business-flow.json \
 *     -e SUMMARY_MD=tmp/k6/business-flow.md \
 *     performance/k6/business-flow.js
 *
 * Docker example:
 *   docker run --rm -i -v "$PWD:/work" -w /work grafana/k6 run \
 *     -e APP_1_URL=http://host.docker.internal:18081 \
 *     -e APP_2_URL=http://host.docker.internal:18082 \
 *     performance/k6/business-flow.js
 */

const APP_1_URL = trimTrailingSlash(__ENV.APP_1_URL || 'http://host.docker.internal:18081');
const APP_2_URL = trimTrailingSlash(__ENV.APP_2_URL || 'http://host.docker.internal:18082');
const PROFILE = (__ENV.PROFILE || 'smoke').toLowerCase();
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '15s';
const ITERATION_PAUSE_SECONDS = numberEnv('ITERATION_PAUSE_SECONDS', 0.1, 0);

const FLOW_SUCCESS_TARGET = numberEnv('FLOW_SUCCESS_TARGET', 0.99, 0, 1);
const STEP_SUCCESS_TARGET = numberEnv('STEP_SUCCESS_TARGET', 0.99, 0, 1);
const HTTP_FAILURE_TARGET = numberEnv('HTTP_FAILURE_TARGET', 0.01, 0, 1);
// Latency limits are deliberately opt-in. An exploratory run should first
// establish an observed baseline; pass HTTP_P95_MS and/or FLOW_P95_MS only
// when the current environment has an evidence-backed acceptance target.
const HTTP_P95_MS = optionalNumberEnv('HTTP_P95_MS', 1);
const FLOW_P95_MS = optionalNumberEnv('FLOW_P95_MS', 1);

const flowSuccess = new Rate('business_flow_success');
const flowDuration = new Trend('business_flow_duration', true);
const flowFailures = new Counter('business_flow_failures');
const stepSuccess = new Rate('business_step_success');
const stepDuration = new Trend('business_step_duration', true);
const cleanupSuccess = new Rate('business_cleanup_success');
const crossInstanceMismatch = new Rate('cross_instance_mismatch');
const unexpectedConflicts = new Counter('unexpected_conflicts');
const rateLimitedResponses = new Counter('rate_limited_responses');
const serverErrors = new Counter('server_errors');

export const options = {
  discardResponseBodies: false,
  scenarios: scenarioFor(PROFILE),
  thresholds: thresholdsForRun(),
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function multiplayerStartFlow() {
  const startedAt = Date.now();
  const context = {
    flowId: createFlowId(),
    commandSequence: 0,
    roomCode: null,
    host: null,
    guest: null,
    stateVersion: null,
  };
  let businessCompleted = false;
  let cleanupCompleted = true;

  try {
    context.host = guestLogin(context, APP_1_URL, 'host_login_app1', 'app-1');
    context.guest = distinctGuestLogin(
      context,
      APP_2_URL,
      'guest_login_app2',
      'app-2',
      context.host.username,
    );

    const created = command(context, {
      baseUrl: APP_1_URL,
      instance: 'app-1',
      step: 'host_create_room_app1',
      method: 'POST',
      path: '/api/room/create',
      name: 'POST /api/room/create',
      token: context.host.token,
      // Capture the room as soon as a usable 2xx payload arrives. If a later
      // semantic assertion fails, the finally block can still dissolve it.
      capture: (body) => {
        if (isRoom(body)) {
          context.roomCode = body.code;
          context.stateVersion = body.stateVersion;
        }
      },
      validate: (body) => isRoom(body)
        && body.playerCount === 1
        && body.status === 'WAITING'
        && playerMatches(body, context.host.username, { host: true }),
    });
    context.roomCode = created.code;
    context.stateVersion = requireStateVersion(created, 'host_create_room_app1');

    // Deliberately read through app-2 before joining: this proves that the
    // second instance can observe the Redis-backed room created by app-1.
    const observedByGuest = query(context, {
      baseUrl: APP_2_URL,
      instance: 'app-2',
      step: 'guest_read_room_cross_instance',
      path: `/api/room/${context.roomCode}`,
      name: 'GET /api/room/:code',
      token: context.guest.token,
      crossInstanceExpected: (body) => isRoom(body)
        && body.code === context.roomCode
        && body.playerCount === 1
        && body.stateVersion === context.stateVersion,
      validate: (body) => isRoom(body)
        && body.code === context.roomCode
        && body.playerCount === 1
        && body.stateVersion === context.stateVersion,
    });
    context.stateVersion = requireStateVersion(observedByGuest, 'guest_read_room_cross_instance');

    const joined = command(context, {
      baseUrl: APP_2_URL,
      instance: 'app-2',
      step: 'guest_join_room_app2',
      method: 'POST',
      path: '/api/room/join',
      name: 'POST /api/room/join',
      token: context.guest.token,
      expectedStateVersion: context.stateVersion,
      body: { code: context.roomCode },
      validate: (responseBody) => isRoom(responseBody)
        && responseBody.code === context.roomCode
        && responseBody.playerCount === 2
        && playerMatches(responseBody, context.host.username)
        && playerMatches(responseBody, context.guest.username),
    });
    context.stateVersion = requireNextVersion(joined, context.stateVersion, 'guest_join_room_app2');

    // Alternate subsequent commands between instances. Every mutation uses
    // the exact stateVersion returned by the preceding successful operation.
    const hostCharacter = roomCommand(context, {
      baseUrl: APP_1_URL,
      instance: 'app-1',
      step: 'host_select_character_app1',
      path: `/api/room/${context.roomCode}/character`,
      name: 'POST /api/room/:code/character',
      token: context.host.token,
      body: { characterClass: 'SUN_WUKONG' },
      validate: (body) => playerMatches(body, context.host.username, {
        characterClass: 'SUN_WUKONG',
      }),
    });
    context.stateVersion = hostCharacter.stateVersion;

    const guestCharacter = roomCommand(context, {
      baseUrl: APP_2_URL,
      instance: 'app-2',
      step: 'guest_select_character_app2',
      path: `/api/room/${context.roomCode}/character`,
      name: 'POST /api/room/:code/character',
      token: context.guest.token,
      body: { characterClass: 'ZHU_BAJIE' },
      validate: (body) => playerMatches(body, context.guest.username, {
        characterClass: 'ZHU_BAJIE',
      }),
    });
    context.stateVersion = guestCharacter.stateVersion;

    const hostReady = roomCommand(context, {
      baseUrl: APP_1_URL,
      instance: 'app-1',
      step: 'host_ready_app1',
      path: `/api/room/${context.roomCode}/ready`,
      name: 'POST /api/room/:code/ready',
      token: context.host.token,
      validate: (body) => playerMatches(body, context.host.username, { ready: true }),
    });
    context.stateVersion = hostReady.stateVersion;

    const guestReady = roomCommand(context, {
      baseUrl: APP_2_URL,
      instance: 'app-2',
      step: 'guest_ready_app2',
      path: `/api/room/${context.roomCode}/ready`,
      name: 'POST /api/room/:code/ready',
      token: context.guest.token,
      validate: (body) => body.players.length === 2
        && body.players.every((player) => player.ready && player.characterClass),
    });
    context.stateVersion = guestReady.stateVersion;

    query(context, {
      baseUrl: APP_1_URL,
      instance: 'app-1',
      step: 'host_check_can_start_app1',
      path: `/api/room/${context.roomCode}/canStart`,
      name: 'GET /api/room/:code/canStart',
      token: context.host.token,
      validate: (body) => body === true,
    });

    const started = roomCommand(context, {
      baseUrl: APP_1_URL,
      instance: 'app-1',
      step: 'host_start_game_app1',
      path: `/api/room/${context.roomCode}/start-game`,
      name: 'POST /api/room/:code/start-game',
      token: context.host.token,
      validate: (body) => body.status === 'IN_MAP'
        && body.floor === 1
        && Array.isArray(body.map)
        && body.map.length > 0
        && body.players.every((player) => player.hp > 0 && player.maxHp > 0),
    });
    context.stateVersion = started.stateVersion;

    const observedStartedGame = query(context, {
      baseUrl: APP_2_URL,
      instance: 'app-2',
      step: 'guest_read_started_game_cross_instance',
      path: `/api/room/${context.roomCode}`,
      name: 'GET /api/room/:code',
      token: context.guest.token,
      crossInstanceExpected: (body) => isRoom(body)
        && body.status === 'IN_MAP'
        && body.playerCount === 2
        && body.stateVersion === context.stateVersion,
      validate: (body) => isRoom(body)
        && body.status === 'IN_MAP'
        && body.playerCount === 2
        && body.stateVersion === context.stateVersion,
    });
    context.stateVersion = requireStateVersion(
      observedStartedGame,
      'guest_read_started_game_cross_instance',
    );
    businessCompleted = true;
  } catch (error) {
    flowFailures.add(1, {
      flow: 'multiplayer_start',
      failed_step: error.step || 'unknown',
      profile: PROFILE,
    });
    console.error(`business flow failed: ${String(error.message || error)}`);
  } finally {
    if (context.roomCode && context.host && context.host.token) {
      cleanupCompleted = safelyDissolveRoom(context);
    }
    const succeeded = businessCompleted && cleanupCompleted;
    const flowTags = { flow: 'multiplayer_start', profile: PROFILE };
    flowSuccess.add(succeeded, flowTags);
    flowDuration.add(Date.now() - startedAt, flowTags);
  }

  if (ITERATION_PAUSE_SECONDS > 0) sleep(ITERATION_PAUSE_SECONDS);
}

function guestLogin(context, baseUrl, step, instance) {
  return command(context, {
    baseUrl,
    instance,
    step,
    method: 'POST',
    path: '/api/auth/guest',
    name: 'POST /api/auth/guest',
    validate: (body) => typeof body.token === 'string'
      && body.token.length > 0
      && typeof body.username === 'string'
      && body.username.startsWith('guest_')
      && body.role === 'GUEST',
  });
}

function distinctGuestLogin(context, baseUrl, step, instance, forbiddenUsername) {
  const maxRetries = 2;
  for (let attempt = 0; attempt <= maxRetries; attempt += 1) {
    const attemptStep = attempt === 0 ? step : `${step}_retry_${attempt}`;
    const guest = guestLogin(context, baseUrl, attemptStep, instance);
    if (guest.username !== forbiddenUsername) return guest;
  }
  throw stepError(
    `${step}_distinct_username`,
    `guest username still collides with host after ${maxRetries} retries`,
  );
}

function roomCommand(context, request) {
  const previousVersion = context.stateVersion;
  const responseBody = command(context, {
    ...request,
    method: 'POST',
    expectedStateVersion: previousVersion,
    validate: (body, response) => isRoom(body)
      && request.validate(body, response),
  });
  requireNextVersion(responseBody, previousVersion, request.step);
  return responseBody;
}

function command(context, request) {
  const headers = {
    Accept: 'application/json',
    'X-Idempotency-Key': nextIdempotencyKey(context, request.step),
  };
  if (request.token) headers.Authorization = `Bearer ${request.token}`;
  if (request.expectedStateVersion !== undefined) {
    if (!isStateVersion(request.expectedStateVersion)) {
      throw stepError(request.step, `invalid expected stateVersion: ${request.expectedStateVersion}`);
    }
    headers['X-Expected-State-Version'] = String(request.expectedStateVersion);
  }
  let payload = null;
  if (request.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(request.body);
  }
  return executeJsonStep(context, request, headers, payload);
}

function query(context, request) {
  const headers = { Accept: 'application/json' };
  if (request.token) headers.Authorization = `Bearer ${request.token}`;
  return executeJsonStep(context, { ...request, method: 'GET' }, headers, null);
}

function executeJsonStep(context, request, headers, payload) {
  const tags = stepTags(request.step, request.instance);
  const response = http.request(
    request.method,
    `${request.baseUrl}${request.path}`,
    payload,
    {
      headers,
      timeout: REQUEST_TIMEOUT,
      tags: { ...tags, name: request.name },
    },
  );
  const parsed = parseJson(response);
  const statusOk = response.status >= 200 && response.status < 300;
  recordUnexpectedStatus(response, tags);
  if (request.crossInstanceExpected) {
    let mismatch = true;
    if (statusOk && parsed.ok) {
      try {
        mismatch = !request.crossInstanceExpected(parsed.value);
      } catch (_) {
        mismatch = true;
      }
    }
    crossInstanceMismatch.add(mismatch, {
      ...tags,
      checkpoint: request.step,
    });
  }
  if (statusOk && parsed.ok && request.capture) {
    try {
      request.capture(parsed.value);
    } catch (_) {
      // Capture is best-effort cleanup bookkeeping, not part of the API
      // contract. The regular validator below remains authoritative.
    }
  }
  let contractOk = false;
  if (statusOk && parsed.ok) {
    try {
      contractOk = Boolean(request.validate(parsed.value, response));
    } catch (_) {
      contractOk = false;
    }
  }
  check(response, {
    [`${request.step}: HTTP 2xx`]: () => statusOk,
    [`${request.step}: response contract`]: () => contractOk,
  }, tags);
  const succeeded = statusOk && contractOk;
  stepSuccess.add(succeeded, tags);
  stepDuration.add(response.timings.duration, tags);

  if (!succeeded) {
    const reason = parsed.ok ? compactBody(response.body) : 'response is not valid JSON';
    throw stepError(request.step, `${response.status} ${reason}`);
  }
  return parsed.value;
}

function safelyDissolveRoom(context) {
  const readStep = 'cleanup_read_latest_room_app1';
  const readTags = stepTags(readStep, 'app-1');
  const latestResponse = http.get(`${APP_1_URL}/api/room/${context.roomCode}`, {
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${context.host.token}`,
    },
    timeout: REQUEST_TIMEOUT,
    tags: { ...readTags, name: 'GET /api/room/:code (cleanup)' },
    responseCallback: http.expectedStatuses(200, 404),
  });
  recordUnexpectedStatus(latestResponse, readTags);
  const roomAlreadyGone = latestResponse.status === 404;
  const parsed = parseJson(latestResponse);
  const readable = roomAlreadyGone
    || (latestResponse.status === 200 && parsed.ok && isRoom(parsed.value));
  check(latestResponse, {
    [`${readStep}: room readable or already gone`]: () => readable,
  }, readTags);
  stepSuccess.add(readable, readTags);
  stepDuration.add(latestResponse.timings.duration, readTags);

  if (roomAlreadyGone) {
    cleanupSuccess.add(true, { flow: 'multiplayer_start', profile: PROFILE });
    return true;
  }
  if (!readable) {
    cleanupSuccess.add(false, { flow: 'multiplayer_start', profile: PROFILE });
    console.error(`cleanup read failed for room ${context.roomCode}: ${latestResponse.status}`);
    return false;
  }

  const latestVersion = parsed.value.stateVersion;
  if (!isStateVersion(latestVersion)) {
    cleanupSuccess.add(false, { flow: 'multiplayer_start', profile: PROFILE });
    console.error(`cleanup read returned invalid stateVersion for room ${context.roomCode}`);
    return false;
  }

  const leaveStep = 'cleanup_host_leave_app1';
  const leaveTags = stepTags(leaveStep, 'app-1');
  const leaveResponse = http.post(
    `${APP_1_URL}/api/room/${context.roomCode}/leave`,
    null,
    {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${context.host.token}`,
        'X-Expected-State-Version': String(latestVersion),
        'X-Idempotency-Key': nextIdempotencyKey(context, leaveStep),
      },
      timeout: REQUEST_TIMEOUT,
      tags: { ...leaveTags, name: 'POST /api/room/:code/leave (cleanup)' },
    },
  );
  const leaveBody = parseJson(leaveResponse);
  recordUnexpectedStatus(leaveResponse, leaveTags);
  const dissolved = leaveResponse.status >= 200
    && leaveResponse.status < 300
    && leaveBody.ok
    && leaveBody.value.dissolved === true;
  check(leaveResponse, {
    [`${leaveStep}: HTTP 2xx`]: () => leaveResponse.status >= 200 && leaveResponse.status < 300,
    [`${leaveStep}: room dissolved`]: () => dissolved,
  }, leaveTags);
  stepSuccess.add(dissolved, leaveTags);
  stepDuration.add(leaveResponse.timings.duration, leaveTags);
  cleanupSuccess.add(dissolved, { flow: 'multiplayer_start', profile: PROFILE });
  if (!dissolved) {
    console.error(`cleanup leave failed for room ${context.roomCode}: ${leaveResponse.status}`);
  }
  return dissolved;
}

function isRoom(body) {
  return body !== null
    && typeof body === 'object'
    && typeof body.code === 'string'
    && Array.isArray(body.players)
    && isStateVersion(body.stateVersion);
}

function playerMatches(room, username, expected = {}) {
  if (!room || !Array.isArray(room.players)) return false;
  const player = room.players.find((candidate) => candidate.username === username);
  if (!player) return false;
  return Object.entries(expected).every(([key, value]) => player[key] === value);
}

function requireStateVersion(body, step) {
  if (!isStateVersion(body && body.stateVersion)) {
    throw stepError(step, 'response has no non-negative integer stateVersion');
  }
  return body.stateVersion;
}

function requireNextVersion(body, previousVersion, step) {
  const nextVersion = requireStateVersion(body, step);
  if (nextVersion <= previousVersion) {
    throw stepError(step, `stateVersion did not advance (${previousVersion} -> ${nextVersion})`);
  }
  return nextVersion;
}

function isStateVersion(value) {
  return Number.isInteger(value) && value >= 0;
}

function nextIdempotencyKey(context, step) {
  context.commandSequence += 1;
  // The flow id is unique per VU/iteration/run; the monotonic sequence makes
  // every mutation in that flow unique while remaining under 128 characters.
  return `k6-${context.flowId}-${context.commandSequence}-${step}`.slice(0, 128);
}

function createFlowId() {
  return `${Date.now()}-${__VU}-${__ITER}-${Math.random().toString(36).slice(2, 10)}`;
}

function stepTags(step, instance) {
  return {
    flow: 'multiplayer_start',
    business_step: step,
    target_instance: instance,
    profile: PROFILE,
  };
}

function parseJson(response) {
  try {
    return { ok: true, value: response.json() };
  } catch (_) {
    return { ok: false, value: null };
  }
}

function compactBody(body) {
  if (typeof body !== 'string') return '<empty response>';
  return body.replace(/\s+/g, ' ').slice(0, 300);
}

function recordUnexpectedStatus(response, tags) {
  // Add zero samples as well so successful reports explicitly contain zeroes
  // instead of silently omitting these operationally important metrics.
  unexpectedConflicts.add(response.status === 409 ? 1 : 0, tags);
  rateLimitedResponses.add(response.status === 429 ? 1 : 0, tags);
  serverErrors.add(response.status >= 500 ? 1 : 0, tags);
}

function stepError(step, message) {
  const error = new Error(`[${step}] ${message}`);
  error.step = step;
  return error;
}

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, '');
}

function numberEnv(name, fallback, min, max = Number.POSITIVE_INFINITY) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') return fallback;
  const value = Number(raw);
  if (!Number.isFinite(value) || value < min || value > max) {
    throw new Error(`${name} must be between ${min} and ${max}`);
  }
  return value;
}

function optionalNumberEnv(name, min, max = Number.POSITIVE_INFINITY) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') return null;
  return numberEnv(name, min, min, max);
}

function positiveIntegerEnv(name, fallback) {
  const value = numberEnv(name, fallback, 1);
  if (!Number.isInteger(value)) throw new Error(`${name} must be an integer`);
  return value;
}

function thresholdsForRun() {
  const thresholds = {
    business_flow_success: [`rate>=${FLOW_SUCCESS_TARGET}`],
    business_step_success: [`rate>=${STEP_SUCCESS_TARGET}`],
    business_cleanup_success: [`rate>=${FLOW_SUCCESS_TARGET}`],
    cross_instance_mismatch: ['rate==0'],
    unexpected_conflicts: ['count==0'],
    rate_limited_responses: ['count==0'],
    server_errors: ['count==0'],
    http_req_failed: [`rate<=${HTTP_FAILURE_TARGET}`],
  };
  if (HTTP_P95_MS !== null) {
    thresholds['http_req_duration{flow:multiplayer_start}'] = [`p(95)<${HTTP_P95_MS}`];
  }
  if (FLOW_P95_MS !== null) {
    thresholds.business_flow_duration = [`p(95)<${FLOW_P95_MS}`];
  }
  if (PROFILE === 'load') {
    // An arrival-rate run that silently drops scheduled iterations did not
    // actually deliver its requested load, so it must not be reported green.
    thresholds.dropped_iterations = ['count==0'];
  }
  return thresholds;
}

function scenarioFor(profile) {
  if (profile === 'smoke') {
    return {
      multiplayer_start: {
        executor: 'shared-iterations',
        vus: positiveIntegerEnv('VUS', 1),
        iterations: positiveIntegerEnv('ITERATIONS', 1),
        maxDuration: __ENV.MAX_DURATION || '2m',
        gracefulStop: '10s',
        tags: { profile: 'smoke' },
      },
    };
  }
  if (profile === 'load') {
    return {
      multiplayer_start: {
        executor: 'constant-arrival-rate',
        rate: positiveIntegerEnv('RATE', 1),
        timeUnit: __ENV.TIME_UNIT || '1s',
        duration: __ENV.DURATION || '30s',
        preAllocatedVUs: positiveIntegerEnv('PRE_ALLOCATED_VUS', 5),
        maxVUs: positiveIntegerEnv('MAX_VUS', 20),
        gracefulStop: '30s',
        tags: { profile: 'load' },
      },
    };
  }
  throw new Error(`Unsupported PROFILE '${profile}'. Use 'smoke' or 'load'.`);
}

export function handleSummary(data) {
  const jsonPath = __ENV.SUMMARY_JSON || 'business-flow-summary.json';
  const markdownPath = __ENV.SUMMARY_MD || 'business-flow-summary.md';
  const markdown = buildMarkdownSummary(data);
  return {
    stdout: `${markdown}\n`,
    [jsonPath]: JSON.stringify(data, null, 2),
    [markdownPath]: markdown,
  };
}

function buildMarkdownSummary(data) {
  const metrics = data.metrics || {};
  const thresholdRows = [];
  let accepted = true;
  Object.keys(metrics).sort().forEach((metricName) => {
    const thresholds = metrics[metricName].thresholds || {};
    Object.keys(thresholds).forEach((expression) => {
      const ok = thresholds[expression].ok === true;
      accepted = accepted && ok;
      thresholdRows.push(`| \`${metricName}\` | \`${expression}\` | ${ok ? 'PASS' : 'FAIL'} |`);
    });
  });
  if (thresholdRows.length === 0) accepted = false;

  const iterations = metricValue(metrics, 'iterations', 'count');
  const iterationsPerSecond = metricValue(metrics, 'iterations', 'rate');
  const requestsPerSecond = metricValue(metrics, 'http_reqs', 'rate');
  const droppedIterations = metricValue(metrics, 'dropped_iterations', 'count') ?? 0;
  const successRate = metricValue(metrics, 'business_flow_success', 'rate');
  const cleanupRate = metricValue(metrics, 'business_cleanup_success', 'rate');
  const mismatchRate = metricValue(metrics, 'cross_instance_mismatch', 'rate');
  const mismatchCount = metricValue(metrics, 'cross_instance_mismatch', 'passes') ?? 0;
  const conflictCount = metricValue(metrics, 'unexpected_conflicts', 'count') ?? 0;
  const rateLimitedCount = metricValue(metrics, 'rate_limited_responses', 'count') ?? 0;
  const serverErrorCount = metricValue(metrics, 'server_errors', 'count') ?? 0;
  const flowP95 = metricValue(metrics, 'business_flow_duration', 'p(95)');
  const requestP95 = metricValue(metrics, 'http_req_duration', 'p(95)');
  const requestFailureRate = metricValue(metrics, 'http_req_failed', 'rate');

  return [
    '# k6 双实例多人开局业务链路报告',
    '',
    `- 生成时间：${new Date().toISOString()}`,
    `- 配置：\`${PROFILE}\``,
    `- app-1：\`${APP_1_URL}\``,
    `- app-2：\`${APP_2_URL}\``,
    `- 时延门禁：${latencyThresholdDescription()}`,
    `- 本次阈值验收：**${accepted ? 'PASS' : 'FAIL'}**`,
    '',
    '> 这些数值只描述本次测试配置、数据与运行环境，不构成生产性能结论或 SLO。',
    '',
    '## 本次结果',
    '',
    '| 指标 | 数值 |',
    '| --- | ---: |',
    `| 完成迭代 | ${formatNumber(iterations, 0)} |`,
    `| 实际迭代速率 | ${formatRate(iterationsPerSecond)} iterations/s |`,
    `| HTTP 请求速率 | ${formatRate(requestsPerSecond)} req/s |`,
    `| 丢弃迭代 | ${formatNumber(droppedIterations, 0)} |`,
    `| 完整业务链路成功率 | ${formatPercent(successRate)} |`,
    `| 房间清理成功率 | ${formatPercent(cleanupRate)} |`,
    `| 跨实例不一致 | ${formatPercent(mismatchRate)}（${formatNumber(mismatchCount, 0)} 次） |`,
    `| 非预期 409 | ${formatNumber(conflictCount, 0)} |`,
    `| HTTP 429 | ${formatNumber(rateLimitedCount, 0)} |`,
    `| HTTP 5xx | ${formatNumber(serverErrorCount, 0)} |`,
    `| 业务链路耗时 P95 | ${formatMilliseconds(flowP95)} |`,
    `| HTTP 请求耗时 P95 | ${formatMilliseconds(requestP95)} |`,
    `| HTTP 请求失败率 | ${formatPercent(requestFailureRate)} |`,
    '',
    '## 验收阈值',
    '',
    '| 指标 | 条件 | 结果 |',
    '| --- | --- | --- |',
    ...(thresholdRows.length ? thresholdRows : ['| - | 未返回阈值结果 | FAIL |']),
    '',
    '## 覆盖链路',
    '',
    'app-1 游客登录 → app-2 游客登录 → app-1 创建房间 → app-2 跨实例读取并加入 → '
      + '双方跨实例选角与准备 → app-1 开始游戏 → app-2 校验开局状态 → 房主解散房间。',
    '',
    '## 数据清理边界',
    '',
    '- “房间清理成功”表示房主退出后逻辑房间已解散；已完成的幂等记录由 Redis 在 10 分钟后自然过期。',
    '- 若建房已在服务端完成、但响应在返回房间码前丢失，脚本无法定向解散该房间，需依赖房间 2 小时 TTL。',
    '- Redis 的逻辑删除或 TTL 到期不代表 AOF/数据卷文件会立即缩小；物理空间回收取决于后续 AOF 重写等维护过程。',
    '- 游客登录仅签发 JWT，本场景不会在 MySQL 中创建测试用户。',
    '',
  ].join('\n');
}

function metricValue(metrics, metricName, valueName) {
  const metric = metrics[metricName];
  return metric && metric.values ? metric.values[valueName] : undefined;
}

function formatPercent(value) {
  return Number.isFinite(value) ? `${(value * 100).toFixed(2)}%` : 'N/A';
}

function formatMilliseconds(value) {
  return Number.isFinite(value) ? `${value.toFixed(2)} ms` : 'N/A';
}

function formatNumber(value, decimals) {
  return Number.isFinite(value) ? value.toFixed(decimals) : 'N/A';
}

function formatRate(value) {
  return Number.isFinite(value) ? value.toFixed(2) : 'N/A';
}

function latencyThresholdDescription() {
  const enabled = [];
  if (HTTP_P95_MS !== null) enabled.push(`HTTP P95 < ${HTTP_P95_MS} ms`);
  if (FLOW_P95_MS !== null) enabled.push(`Flow P95 < ${FLOW_P95_MS} ms`);
  return enabled.length ? enabled.join('；') : '未启用（通过环境变量显式设置）';
}
