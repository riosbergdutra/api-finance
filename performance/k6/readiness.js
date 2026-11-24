import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const path = __ENV.READINESS_PATH || '/actuator/health/readiness';
const vus = Number(__ENV.VUS || 1);
const duration = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    readiness_probe: {
      executor: 'constant-vus',
      vus,
      duration,
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}${path}`, {
    tags: { endpoint: 'readiness', workload: 'read-only' },
    timeout: '5s',
  });

  check(response, {
    'readiness responds HTTP 200': (r) => r.status === 200,
    'readiness response is JSON': (r) =>
      (r.headers['Content-Type'] || '').includes('application/json'),
  });

  sleep(1);
}
