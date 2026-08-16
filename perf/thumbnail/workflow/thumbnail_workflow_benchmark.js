import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_PREFIX = __ENV.USER_PREFIX || 'perfthumb';
const USER_DOMAIN = __ENV.USER_DOMAIN || 'test.com';
const USER_PASSWORD = __ENV.USER_PASSWORD || 'Testtest1';
const USER_COUNT = Number(__ENV.USER_COUNT || 5);
const DOCS_PER_USER = Number(__ENV.DOCS_PER_USER || 5);
const RUN_ID = __ENV.RUN_ID || 'thumbwf';
const WORKFLOW_VUS = Number(__ENV.WORKFLOW_VUS || USER_COUNT);
const WORKFLOW_DURATION = __ENV.WORKFLOW_DURATION || '30s';
const RESULT_DIR = __ENV.RESULT_DIR || 'perf/thumbnail/workflow/results';
const THUMBNAIL_CONTENT_TYPE = __ENV.THUMBNAIL_CONTENT_TYPE || 'image/webp';
const THUMBNAIL_SIZE_BYTES = Number(__ENV.THUMBNAIL_SIZE_BYTES || 1024);
const THUMBNAIL_BODY = 'x'.repeat(THUMBNAIL_SIZE_BYTES);

if (WORKFLOW_VUS > USER_COUNT) {
  throw new Error('WORKFLOW_VUS must be <= USER_COUNT to avoid same-user thumbnail token contention');
}

export const options = {
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    thumbnail_workflow: {
      executor: 'constant-vus',
      vus: WORKFLOW_VUS,
      duration: WORKFLOW_DURATION,
      exec: 'runThumbnailWorkflow',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    thumbnail_workflow_failed: ['rate<0.02'],
  },
};

const workflowFailed = new Rate('thumbnail_workflow_failed');
const tLogin = new Trend('thumbnail_login_ms');
const tDocListSetup = new Trend('thumbnail_setup_doc_list_ms');
const tGraphSetup = new Trend('thumbnail_setup_graph_ms');
const tSaveUpdate = new Trend('op_save_update_ms');
const tUploadUrl = new Trend('op_image_upload_url_ms');
const tS3PutThumbnail = new Trend('op_s3_put_thumbnail_ms');
const tImageComplete = new Trend('op_image_complete_ms');
const tThumbnailFinalize = new Trend('op_thumbnail_finalize_ms');
const tDocListAfterThumbnail = new Trend('op_doc_list_after_thumbnail_ms');
const tWorkflowTotal = new Trend('op_thumbnail_workflow_total_ms');

function pad3(n) {
  return String(n).padStart(3, '0');
}

function userEmail(userNo) {
  return `${USER_PREFIX}_u${pad3(userNo)}@${USER_DOMAIN}`;
}

function headers(cookie) {
  return {
    headers: {
      Cookie: cookie,
      'Content-Type': 'application/json',
    },
  };
}

function ensureStatus(res, statuses, op) {
  const ok = check(res, {
    [`${op} status`]: (r) => statuses.includes(r.status),
  });
  workflowFailed.add(!ok, { op });
  if (!ok) {
    const snippet = typeof res.body === 'string' ? res.body.slice(0, 300) : '';
    throw new Error(`${op} failed status=${res.status}, body=${snippet}`);
  }
}

function parseJson(res, op) {
  try {
    return res.json();
  } catch (_e) {
    throw new Error(`${op} invalid JSON`);
  }
}

function login(email) {
  const loginJar = new http.CookieJar();
  const res = http.post(
    `${BASE_URL}/api/user/login`,
    JSON.stringify({ email, password: USER_PASSWORD }),
    {
      headers: { 'Content-Type': 'application/json' },
      jar: loginJar,
      tags: { op: 'thumbnail_login', name: 'POST /api/user/login' },
    },
  );
  tLogin.add(res.timings.duration);
  ensureStatus(res, [200], 'thumbnail_login');

  const jsession = res.cookies.JSESSIONID && res.cookies.JSESSIONID[0];
  if (!jsession || !jsession.value) {
    throw new Error('thumbnail_login missing JSESSIONID');
  }
  return `JSESSIONID=${jsession.value}`;
}

function fetchDocs(cookie) {
  const res = http.get(
    `${BASE_URL}/api/document?page=0&size=${DOCS_PER_USER}&sort=updatedAt&order=desc`,
    { headers: { Cookie: cookie }, tags: { op: 'thumbnail_setup_doc_list', name: 'GET /api/document' } },
  );
  tDocListSetup.add(res.timings.duration);
  ensureStatus(res, [200], 'thumbnail_setup_doc_list');
  const body = parseJson(res, 'thumbnail_setup_doc_list');
  if (!body || !Array.isArray(body.content) || body.content.length === 0) {
    throw new Error('thumbnail_setup_doc_list empty content');
  }
  return body.content.map((doc) => ({ docId: doc.id }));
}

function attachSaveIds(cookie, docs) {
  return docs.map((doc) => {
    const res = http.get(
      `${BASE_URL}/api/document/${doc.docId}/graph`,
      { headers: { Cookie: cookie }, tags: { op: 'thumbnail_setup_graph', name: 'GET /api/document/{docId}/graph' } },
    );
    tGraphSetup.add(res.timings.duration);
    ensureStatus(res, [200], 'thumbnail_setup_graph');
    const graph = parseJson(res, 'thumbnail_setup_graph');
    const branch = graph.branches && graph.branches.find((item) => item.saveId);
    if (!branch) {
      throw new Error(`thumbnail_setup_graph missing saveId docId=${doc.docId}`);
    }
    return { docId: doc.docId, saveId: branch.saveId };
  });
}

function buildSaveContent(docId, iteration) {
  return [
    {
      id: `thumbwf-${docId}-${iteration}-1`,
      type: 'paragraph',
      props: {},
      content: [
        {
          type: 'text',
          text: `thumbnail workflow ${RUN_ID} doc ${docId} iteration ${iteration}`,
          styles: {},
        },
      ],
      children: [],
    },
  ];
}

function requestSaveUpdate(cookie, target, iteration) {
  const res = http.put(
    `${BASE_URL}/api/document/${target.docId}/save/${target.saveId}`,
    JSON.stringify({ content: buildSaveContent(target.docId, iteration) }),
    { ...headers(cookie), tags: { op: 'save_update', name: 'PUT /api/document/{docId}/save/{saveId}' } },
  );
  tSaveUpdate.add(res.timings.duration);
  ensureStatus(res, [200], 'save_update');
  const body = parseJson(res, 'save_update');
  if (!body.thumbnail || !body.thumbnail.requestToken) {
    throw new Error('save_update missing thumbnail.requestToken');
  }
  return body.thumbnail.requestToken;
}

function requestUploadUrl(cookie, docId, iteration) {
  const res = http.post(
    `${BASE_URL}/api/images/upload-url`,
    JSON.stringify({
      docId,
      originalFileName: `thumbnail-${RUN_ID}-${docId}-${iteration}.${extensionOf(THUMBNAIL_CONTENT_TYPE)}`,
      contentType: THUMBNAIL_CONTENT_TYPE,
      size: THUMBNAIL_SIZE_BYTES,
      purpose: 'DOC_THUMBNAIL',
    }),
    { ...headers(cookie), tags: { op: 'image_upload_url', name: 'POST /api/images/upload-url' } },
  );
  tUploadUrl.add(res.timings.duration);
  ensureStatus(res, [200], 'image_upload_url');
  const body = parseJson(res, 'image_upload_url');
  if (!body.imageId || !body.uploadUrl) {
    throw new Error('image_upload_url missing imageId or uploadUrl');
  }
  return {
    imageId: body.imageId,
    uploadUrl: body.uploadUrl,
    method: body.method || 'PUT',
  };
}

function extensionOf(contentType) {
  switch (contentType) {
    case 'image/jpeg':
      return 'jpg';
    case 'image/png':
      return 'png';
    case 'image/webp':
      return 'webp';
    case 'image/gif':
      return 'gif';
    default:
      return 'bin';
  }
}

function requestS3PutThumbnail(upload) {
  if (upload.method !== 'PUT') {
    throw new Error(`s3_put_thumbnail unsupported upload method=${upload.method}`);
  }

  const res = http.put(
    upload.uploadUrl,
    THUMBNAIL_BODY,
    {
      headers: {
        'Content-Type': THUMBNAIL_CONTENT_TYPE,
      },
      tags: {
        op: 's3_put_thumbnail',
        name: 's3_put_thumbnail',
      },
    },
  );
  tS3PutThumbnail.add(res.timings.duration);
  ensureStatus(res, [200, 201, 204], 's3_put_thumbnail');
}

function requestImageComplete(cookie, imageId) {
  const res = http.post(
    `${BASE_URL}/api/images/${imageId}/complete`,
    null,
    { headers: { Cookie: cookie }, tags: { op: 'image_complete', name: 'POST /api/images/{imageId}/complete' } },
  );
  tImageComplete.add(res.timings.duration);
  ensureStatus(res, [200], 'image_complete');
}

function requestThumbnailFinalize(cookie, target, imageId, requestToken, iteration) {
  const res = http.put(
    `${BASE_URL}/api/document/${target.docId}/thumbnail`,
    JSON.stringify({
      imageId,
      requestToken,
      signature: `sig-${RUN_ID}-${target.docId}-${iteration}`,
    }),
    { ...headers(cookie), tags: { op: 'thumbnail_finalize', name: 'PUT /api/document/{docId}/thumbnail' } },
  );
  tThumbnailFinalize.add(res.timings.duration);
  ensureStatus(res, [200], 'thumbnail_finalize');
}

function requestDocList(cookie) {
  const res = http.get(
    `${BASE_URL}/api/document?page=0&size=${DOCS_PER_USER}&sort=updatedAt&order=desc`,
    { headers: { Cookie: cookie }, tags: { op: 'doc_list_after_thumbnail', name: 'GET /api/document' } },
  );
  tDocListAfterThumbnail.add(res.timings.duration);
  ensureStatus(res, [200], 'doc_list_after_thumbnail');
}

export function setup() {
  const users = [];
  for (let userNo = 1; userNo <= USER_COUNT; userNo += 1) {
    const cookie = login(userEmail(userNo));
    const docs = attachSaveIds(cookie, fetchDocs(cookie));
    users.push({ userNo, cookie, docs });
  }
  return { users };
}

export function runThumbnailWorkflow(data) {
  const userIndex = (exec.vu.idInTest - 1) % data.users.length;
  const user = data.users[userIndex];
  const iteration = exec.scenario.iterationInTest;
  const target = user.docs[iteration % user.docs.length];
  const started = Date.now();

  const requestToken = requestSaveUpdate(user.cookie, target, iteration);
  const upload = requestUploadUrl(user.cookie, target.docId, iteration);
  requestS3PutThumbnail(upload);
  requestImageComplete(user.cookie, upload.imageId);
  requestThumbnailFinalize(user.cookie, target, upload.imageId, requestToken, iteration);
  requestDocList(user.cookie);

  tWorkflowTotal.add(Date.now() - started);
}

export function handleSummary(data) {
  return {
    stdout: `\n[thumbnail-workflow] run_id=${RUN_ID}, users=${USER_COUNT}, docs_per_user=${DOCS_PER_USER}, vus=${WORKFLOW_VUS}, duration=${WORKFLOW_DURATION}, s3_put=included, thumbnail_size=${THUMBNAIL_SIZE_BYTES}\n`,
    [`${RESULT_DIR}/thumbnail_workflow_${RUN_ID}.json`]: JSON.stringify(data, null, 2),
  };
}
