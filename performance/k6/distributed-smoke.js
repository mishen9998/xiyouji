import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * Lightweight baseline for the Nginx -> two-instance deployment.
 * Usage:
 *   k6 run -e BASE_URL=http://localhost:8080 -e VUS=20 -e DURATION=1m \
 *     performance/k6/distributed-smoke.js
 */
export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const health = http.get(`${baseUrl}/health`, { tags: { endpoint: 'health' } });
  check(health, {
    'health is HTTP 200': (response) => response.status === 200,
    'health is UP': (response) => response.json('status') === 'UP',
  });

  const instance = http.get(`${baseUrl}/api/instance/info`, {
    tags: { endpoint: 'instance-info' },
  });
  check(instance, {
    'instance info is HTTP 200': (response) => response.status === 200,
    'instance id is present': (response) => Boolean(response.json('instanceId')),
  });

  sleep(0.2);
}
